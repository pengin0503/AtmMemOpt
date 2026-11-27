package com.atmmemopt.command;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.diag.NativeImageTracker;
import com.atmmemopt.opt.components.PatchedMapOptStats;
import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.atmmemopt.opt.iris.IrisCachePurger;
import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import com.atmmemopt.opt.owo.OwoDerivedOptHelper;
import com.atmmemopt.opt.render.BerCullingHelper;
import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import com.atmmemopt.opt.startup.JeiFilterOptHelper;
import com.atmmemopt.opt.startup.ReloadProfiler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 * Registers /atmmemopt diagnostic and cache-purge commands on the client command dispatcher.
 */
public final class AtmMemOptCommands {
    private AtmMemOptCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("atmmemopt")
                        .then(Commands.literal("status")
                                .executes(ctx -> executeStatus(ctx.getSource())))
                        .then(Commands.literal("purge-shader-cache")
                                .executes(ctx -> executePurgeShaderCache(ctx.getSource())))
                        .then(Commands.literal("diag")
                                .then(Commands.literal("nativeimage")
                                        .executes(ctx -> executeNativeImageReport(ctx.getSource()))
                                        .then(Commands.literal("on")
                                                .executes(ctx -> executeNativeImageToggle(ctx.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> executeNativeImageToggle(ctx.getSource(), false)))
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> executeNativeImageReset(ctx.getSource())))))
        );
    }

    private static int executeStatus(CommandSourceStack source) {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal("=== AtmMemOpt Memory Status ===").withStyle(ChatFormatting.GOLD));
        sendAndLog(source, logBuf, Component.literal(String.format(
                "Heap: %s / %s (max %s)",
                fmtMB(heap.getUsed()), fmtMB(heap.getCommitted()), fmtMB(heap.getMax())
        )).withStyle(ChatFormatting.WHITE));
        sendAndLog(source, logBuf, Component.literal(String.format(
                "Non-Heap: %s / %s",
                fmtMB(nonHeap.getUsed()), fmtMB(nonHeap.getCommitted())
        )).withStyle(ChatFormatting.WHITE));

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName();
            if (name.contains("Metaspace") || name.contains("Old Gen") || name.contains("G1 Old")) {
                MemoryUsage u = pool.getUsage();
                if (u != null) {
                    sendAndLog(source, logBuf, Component.literal(String.format(
                            "  Pool [%s]: %s / %s",
                            name, fmtMB(u.getUsed()), fmtMB(u.getCommitted())
                    )).withStyle(ChatFormatting.GRAY));
                }
            }
        }

        for (BufferPoolMXBean bp : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            sendAndLog(source, logBuf, Component.literal(String.format(
                    "BufferPool [%s]: count=%d, used=%s, cap=%s",
                    bp.getName(), bp.getCount(), fmtMB(bp.getMemoryUsed()), fmtMB(bp.getTotalCapacity())
            )).withStyle(ChatFormatting.AQUA));
        }

        boolean owoOpt = ModList.get().isLoaded("owo") && AtmMemOptConfig.ENABLE_OWO_DERIVED_MAP_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M1 (owo DerivedMap): %s | unwrapped=%d, customKept=%d",
                owoOpt ? "ON" : "OFF",
                // Guard by mod presence, not by the config flag: never initialize an
                // optional-mod helper class when its mod is absent (NoClassDefFoundError).
                ModList.get().isLoaded("owo") ? OwoDerivedOptHelper.getUnwrappedCount() : 0,
                ModList.get().isLoaded("owo") ? OwoDerivedOptHelper.getKeptCustomCount() : 0
        )).withStyle(ChatFormatting.GREEN));

        boolean m2Opt = AtmMemOptConfig.ENABLE_PATCHED_COMPONENT_MAP_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M2 (PatchedComponentMap): %s | hits=%d (applySkip=%d, restoreFast=%d, fromEmpty=%d)",
                m2Opt ? "ON" : "OFF",
                PatchedMapOptStats.getTotalHits(),
                PatchedMapOptStats.getSkippedEmptyApply(),
                PatchedMapOptStats.getNormalizedEmptyRestore(),
                PatchedMapOptStats.getFastFromEmptyPatch()
        )).withStyle(ChatFormatting.GREEN));

        boolean m3Opt = ModList.get().isLoaded("irons_spellbooks") && AtmMemOptConfig.ENABLE_IRONS_SPELL_CONTAINER_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M3 (Iron's SpellContainer): %s | containerHits=%d, slotHits=%d, dataHits=%d, getOrCreateSaved=%d",
                m3Opt ? "ON" : "OFF",
                ModList.get().isLoaded("irons_spellbooks") ? SpellContainerOptHelper.getContainerHits() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? SpellContainerOptHelper.getSpellSlotHits() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? SpellContainerOptHelper.getSpellDataHits() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? SpellContainerOptHelper.getGetOrCreateAvoided() : 0
        )).withStyle(ChatFormatting.GREEN));

        boolean m4Opt = ModList.get().isLoaded("iris") && AtmMemOptConfig.ENABLE_IRIS_SHADER_CACHE_PURGE.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M4 (Iris ShaderCache): %s | purges=%d, clearedEntries=%d",
                m4Opt ? "ON" : "OFF",
                ModList.get().isLoaded("iris") ? IrisCachePurger.getPurgeCount() : 0,
                ModList.get().isLoaded("iris") ? IrisCachePurger.getTotalEntriesCleared() : 0
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M5 (NativeImage Diag): mixin=%s, runtime=%s",
                NativeImageTracker.isMixinApplied() ? "APPLIED" : "OFF",
                NativeImageTracker.isRuntimeEnabled() ? "RECORDING" : "PAUSED"
        )).withStyle(ChatFormatting.YELLOW));

        boolean m6Opt = AtmMemOptConfig.ENABLE_ITEM_ENCHANTMENTS_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M6 (ItemEnchantments): %s | totalHits=%d (emptyUnified=%d, internHits=%d)",
                m6Opt ? "ON" : "OFF",
                ItemEnchantmentsOptHelper.getTotalHits(),
                ItemEnchantmentsOptHelper.getEmptyUnified(),
                ItemEnchantmentsOptHelper.getInternHits()
        )).withStyle(ChatFormatting.GREEN));

        boolean m7Opt = AtmMemOptConfig.ENABLE_VOXEL_SHAPE_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M7 (VoxelShape): %s | doubleListHits=%d, shapeHits=%d, cacheSize=%d/%d, capRejections=%d, staleRefs=%d, drained=%d, doubleListCapRejections=%d",
                m7Opt ? "ON" : "OFF",
                VoxelShapeOptHelper.getDoubleListHits(),
                VoxelShapeOptHelper.getShapeHits(),
                VoxelShapeOptHelper.getShapeCacheSize(),
                AtmMemOptConfig.VOXEL_SHAPE_MAX_CACHE_ENTRIES.get(),
                VoxelShapeOptHelper.getShapeCapRejections(),
                VoxelShapeOptHelper.getShapeStaleRefs(),
                VoxelShapeOptHelper.getShapeDrained(),
                VoxelShapeOptHelper.getDoubleListCapRejections()
        )).withStyle(ChatFormatting.GREEN));

        boolean m8Jei = ModList.get().isLoaded("jei") && AtmMemOptConfig.ENABLE_JEI_PARALLEL_FILTER.get();
        boolean m8JeiStuff = ModList.get().isLoaded("jeistuff") && AtmMemOptConfig.ENABLE_JEISTUFF_SKIP_LOOT_SCAN.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M8 (Startup/Load): jeiParallel=%s (last=%dms, lazyTips=%d), jeiStuffSkip=%s (skipped=%d), reloadProfiles=%d",
                m8Jei ? "ON" : "OFF",
                JeiFilterOptHelper.getLastBuildMs(),
                JeiFilterOptHelper.getLazyTooltipBuilds(),
                m8JeiStuff ? "ON" : "OFF",
                ReloadProfiler.getJeiStuffLootSkipped(),
                ReloadProfiler.getReloadProfilesLogged()
        )).withStyle(ChatFormatting.GREEN));

        boolean m9Ber = AtmMemOptConfig.ENABLE_BER_DISTANCE_CULLING.get();
        boolean m9Fps = AtmMemOptConfig.ENABLE_UNFOCUSED_FPS_LIMIT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M9 (Render Opt): berCull=%s (dist=%d, culled=%d), unfocusedFps=%s (%dfps)",
                m9Ber ? "ON" : "OFF",
                AtmMemOptConfig.BER_CULLING_DISTANCE.get(),
                BerCullingHelper.getCulledCount(),
                m9Fps ? "ON" : "OFF",
                AtmMemOptConfig.UNFOCUSED_FPS_LIMIT.get()
        )).withStyle(ChatFormatting.GREEN));

        AtmMemOpt.LOGGER.info("\n{}", logBuf);
        return 1;
    }

    private static int executePurgeShaderCache(CommandSourceStack source) {
        if (!ModList.get().isLoaded("iris")) {
            source.sendSuccess(() -> Component.literal(
                    "[AtmMemOpt] Iris is not loaded."
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        IrisCachePurger.PurgeResult res = IrisCachePurger.purgeNow();
        Component msg = Component.literal(String.format(
                "[AtmMemOpt] Purged Iris/glsl_transformer caches: transformCache=%d, buildCache=%d, parseCache=%d, secondaryCache=%d, streams=%d",
                res.transformCache(), res.buildCache(), res.parseCache(), res.secondaryCache(), res.clearedStreams()
        )).withStyle(ChatFormatting.GREEN);
        source.sendSuccess(() -> msg, false);
        AtmMemOpt.LOGGER.info("{}", msg.getString());
        return 1;
    }

    private static int executeNativeImageReport(CommandSourceStack source) {
        if (!NativeImageTracker.isMixinApplied()) {
            source.sendSuccess(() -> Component.literal(
                    "[AtmMemOpt] NativeImage diagnostics mixin is OFF. Set enableNativeImageDiagnostics = true in config/atmmemopt-common.toml and restart."
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        NativeImageTracker.Totals totals = NativeImageTracker.getTotals();
        List<NativeImageTracker.SiteSnapshot> top = NativeImageTracker.getTopUnclosed(20);

        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "=== NativeImage Diagnostics (created=%d, closed=%d, unclosed=%d, sites=%d, recording=%s) ===",
                totals.created(), totals.closed(), totals.unclosed(), totals.distinctSites(),
                NativeImageTracker.isRuntimeEnabled() ? "ON" : "OFF"
        )).withStyle(ChatFormatting.GOLD));

        if (top.isEmpty()) {
            sendAndLog(source, logBuf, Component.literal("No unclosed NativeImage allocations recorded.").withStyle(ChatFormatting.GREEN));
        } else {
            int rank = 1;
            for (NativeImageTracker.SiteSnapshot s : top) {
                sendAndLog(source, logBuf, Component.literal(String.format(
                        "#%02d unclosed=%d (created=%d, closed=%d) | %s",
                        rank++, s.unclosed(), s.created(), s.closed(), s.site()
                )).withStyle(ChatFormatting.WHITE));
            }
        }

        AtmMemOpt.LOGGER.info("\n{}", logBuf);
        return 1;
    }

    private static int executeNativeImageToggle(CommandSourceStack source, boolean enable) {
        if (!NativeImageTracker.isMixinApplied()) {
            source.sendSuccess(() -> Component.literal(
                    "[AtmMemOpt] NativeImage mixin is not loaded. Enable enableNativeImageDiagnostics in config/atmmemopt-common.toml and restart first."
            ).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        NativeImageTracker.setRuntimeEnabled(enable);
        source.sendSuccess(() -> Component.literal(
                "[AtmMemOpt] NativeImage tracking " + (enable ? "ENABLED." : "PAUSED.")
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int executeNativeImageReset(CommandSourceStack source) {
        NativeImageTracker.reset();
        source.sendSuccess(() -> Component.literal(
                "[AtmMemOpt] NativeImage tracking counters cleared."
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static void sendAndLog(CommandSourceStack source, StringBuilder logBuf, Component msg) {
        source.sendSuccess(() -> msg, false);
        logBuf.append(msg.getString()).append('\n');
    }

    private static String fmtMB(long bytes) {
        if (bytes < 0) {
            return "N/A";
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
