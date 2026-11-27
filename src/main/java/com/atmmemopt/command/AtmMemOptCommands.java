package com.atmmemopt.command;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.diag.NativeImageTracker;
import com.atmmemopt.diag.SpriteMemoryDiagnostics;
import com.atmmemopt.opt.chunk.PalettedContainerOptHelper;
import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import com.atmmemopt.opt.components.PatchedMapOptStats;
import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.atmmemopt.opt.iris.IrisCachePurger;
import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import com.atmmemopt.opt.model.BakedQuadOptHelper;
import com.atmmemopt.opt.owo.OwoDerivedOptHelper;
import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import com.atmmemopt.opt.render.BerCullingHelper;
import com.atmmemopt.opt.render.EntityCullingHelper;
import com.atmmemopt.opt.render.ParticleCullingHelper;
import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import com.atmmemopt.opt.startup.JeiFilterOptHelper;
import com.atmmemopt.opt.startup.ReloadProfiler;
import com.atmmemopt.opt.tags.TagEntryOptHelper;
import com.atmmemopt.opt.texture.SpriteMipmapReleaseHelper;
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
                        .then(Commands.literal("purge-caches")
                                .executes(ctx -> executePurgeAllCaches(ctx.getSource())))
                        .then(Commands.literal("diag")
                                .then(Commands.literal("nativeimage")
                                        .executes(ctx -> executeNativeImageReport(ctx.getSource()))
                                        .then(Commands.literal("on")
                                                .executes(ctx -> executeNativeImageToggle(ctx.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> executeNativeImageToggle(ctx.getSource(), false)))
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> executeNativeImageReset(ctx.getSource()))))
                                .then(Commands.literal("ber")
                                        .executes(ctx -> executeBerDiagReport(ctx.getSource()))
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> executeBerDiagReset(ctx.getSource()))))
                                .then(Commands.literal("entity")
                                        .executes(ctx -> executeEntityDiagReport(ctx.getSource()))
                                        .then(Commands.literal("reset")
                                                .executes(ctx -> executeEntityDiagReset(ctx.getSource()))))
                                .then(Commands.literal("sprites")
                                        .executes(ctx -> executeSpritesDiagReport(ctx.getSource())))
                                .then(Commands.literal("all")
                                        .executes(ctx -> executeAllDiagnostics(ctx.getSource()))))
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
                OwoDerivedOptHelper.getUnwrappedCount(),
                OwoDerivedOptHelper.getKeptCustomCount()
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
                SpellContainerOptHelper.getContainerHits(),
                SpellContainerOptHelper.getSpellSlotHits(),
                SpellContainerOptHelper.getSpellDataHits(),
                SpellContainerOptHelper.getGetOrCreateAvoided()
        )).withStyle(ChatFormatting.GREEN));

        boolean m4Opt = ModList.get().isLoaded("iris") && AtmMemOptConfig.ENABLE_IRIS_SHADER_CACHE_PURGE.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M4 (Iris ShaderCache): %s | purges=%d, clearedEntries=%d",
                m4Opt ? "ON" : "OFF",
                IrisCachePurger.getPurgeCount(),
                IrisCachePurger.getTotalEntriesCleared()
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
                "M7 (VoxelShape): %s | doubleListHits=%d, discreteHits=%d, shapeHits=%d, cacheSize=%d/%d",
                m7Opt ? "ON" : "OFF",
                VoxelShapeOptHelper.getDoubleListHits(),
                VoxelShapeOptHelper.getDiscreteHits(),
                VoxelShapeOptHelper.getShapeHits(),
                VoxelShapeOptHelper.getShapeCacheSize(),
                AtmMemOptConfig.VOXEL_SHAPE_MAX_CACHE_ENTRIES.get()
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
                "M9a/b (Render Opt): berCull=%s (dist=%d, culled=%d, rendered=%d), unfocusedFps=%s (%dfps)",
                m9Ber ? "ON" : "OFF",
                BerCullingHelper.getCachedMaxDist(),
                BerCullingHelper.getCulledCount(),
                BerCullingHelper.getRenderedCount(),
                m9Fps ? "ON" : "OFF",
                AtmMemOptConfig.UNFOCUSED_FPS_LIMIT.get()
        )).withStyle(ChatFormatting.GREEN));

        boolean m9Particle = AtmMemOptConfig.ENABLE_PARTICLE_DISTANCE_CULLING.get();
        boolean m9Entity = AtmMemOptConfig.ENABLE_ENTITY_DISTANCE_CULLING.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M9c/d (Cull Opt): particleCull=%s (dist=%d, culled=%d [dist=%d, unfocus=%d]), entityCull=%s (dist=%d/%d/%d, culled=%d)",
                m9Particle ? "ON" : "OFF",
                ParticleCullingHelper.getCachedMaxDist(),
                ParticleCullingHelper.getTotalCulled(),
                ParticleCullingHelper.getCulledDistance(),
                ParticleCullingHelper.getCulledUnfocused(),
                m9Entity ? "ON" : "OFF",
                EntityCullingHelper.getCachedGeneralDist(),
                EntityCullingHelper.getCachedItemDist(),
                EntityCullingHelper.getCachedXpDist(),
                EntityCullingHelper.getCulledCount()
        )).withStyle(ChatFormatting.GREEN));

        boolean m10Opt = AtmMemOptConfig.ENABLE_DATA_COMPONENT_PATCH_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M10 (DataComponentPatch): %s | internHits=%d, mapShares=%d, emptyUnified=%d, cacheSize=%d/%d",
                m10Opt ? "ON" : "OFF",
                DataComponentPatchOptHelper.getInternHits(),
                DataComponentPatchOptHelper.getMapShares(),
                DataComponentPatchOptHelper.getEmptyUnified(),
                DataComponentPatchOptHelper.getCacheSize(),
                AtmMemOptConfig.DATA_COMPONENT_PATCH_MAX_CACHE_ENTRIES.get()
        )).withStyle(ChatFormatting.GREEN));

        boolean m11Opt = AtmMemOptConfig.ENABLE_PALETTED_CONTAINER_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M11 (PalettedContainer): %s | detectorsAvoided=%d",
                m11Opt ? "ON" : "OFF",
                PalettedContainerOptHelper.getDetectorsAvoided()
        )).withStyle(ChatFormatting.GREEN));

        boolean m12Opt = AtmMemOptConfig.ENABLE_RESOURCE_LOCATION_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M12 (ResourceLocation): %s | nsHits=%d, instHits=%d, cacheSize=%d/%d",
                m12Opt ? "ON" : "OFF",
                ResourceLocationOptHelper.getNamespaceHits(),
                ResourceLocationOptHelper.getInstanceHits(),
                ResourceLocationOptHelper.getCacheSize(),
                ResourceLocationOptHelper.getCachedMaxEntries()
        )).withStyle(ChatFormatting.GREEN));

        boolean m13Opt = AtmMemOptConfig.ENABLE_CLIMATE_PARAMETER_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M13 (Climate.Parameter): %s | internHits=%d, cacheSize=%d",
                m13Opt ? "ON" : "OFF",
                ClimateParameterOptHelper.getInternHits(),
                ClimateParameterOptHelper.getCacheSize()
        )).withStyle(ChatFormatting.GREEN));

        boolean m14Opt = AtmMemOptConfig.ENABLE_SPRITE_MIPMAP_RELEASE.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M14 (SpriteMipmap): %s | spritesTrimmed=%d, mipImagesClosed=%d, estFreed=%s",
                m14Opt ? "ON" : "OFF",
                SpriteMipmapReleaseHelper.getSpritesTrimmed(),
                SpriteMipmapReleaseHelper.getImagesClosed(),
                fmtMB(SpriteMipmapReleaseHelper.getEstimatedBytesFreed())
        )).withStyle(ChatFormatting.GREEN));

        boolean m15Opt = AtmMemOptConfig.ENABLE_BAKED_QUAD_VERTEX_DEDUP.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M15 (BakedQuad int[]): %s | hits=%d, estSaved=%s, cacheSize=%d/%d",
                m15Opt ? "ON" : "OFF",
                BakedQuadOptHelper.getVertexHits(),
                fmtMB(BakedQuadOptHelper.getEstimatedBytesSaved()),
                BakedQuadOptHelper.getCacheSize(),
                BakedQuadOptHelper.getCachedMaxEntries()
        )).withStyle(ChatFormatting.GREEN));

        boolean m16Opt = AtmMemOptConfig.ENABLE_TAG_ENTRY_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M16 (TagEntry): %s | internHits=%d, cacheSize=%d",
                m16Opt ? "ON" : "OFF",
                TagEntryOptHelper.getInternHits(),
                TagEntryOptHelper.getCacheSize()
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

    private static int executePurgeAllCaches(CommandSourceStack source) {
        int m3Cleared = ModList.get().isLoaded("irons_spellbooks") ? SpellContainerOptHelper.clearWeakCache() : 0;
        int m4Cleared = ModList.get().isLoaded("iris") ? IrisCachePurger.purgeNow().totalEntries() : 0;
        int m6Cleared = ItemEnchantmentsOptHelper.clearCache();
        int m7Cleared = VoxelShapeOptHelper.clearWeakCaches();
        int m10Cleared = DataComponentPatchOptHelper.clearCache();
        int m12Cleared = ResourceLocationOptHelper.clearWeakCache();
        int m13Cleared = ClimateParameterOptHelper.clearCache();
        int m15Cleared = BakedQuadOptHelper.clearCache();
        int m16Cleared = TagEntryOptHelper.clearCache();

        Component msg = Component.literal(String.format(
                "[AtmMemOpt] Purged caches: M3=%d, M4=%d, M6=%d, M7=%d, M10=%d, M12=%d, M13=%d, M15=%d, M16=%d",
                m3Cleared, m4Cleared, m6Cleared, m7Cleared, m10Cleared, m12Cleared, m13Cleared, m15Cleared, m16Cleared
        )).withStyle(ChatFormatting.GREEN);
        source.sendSuccess(() -> msg, false);
        AtmMemOpt.LOGGER.info("{}", msg.getString());
        return 1;
    }

    private static int executeBerDiagReport(CommandSourceStack source) {
        List<BerCullingHelper.BerDiagSnapshot> top = BerCullingHelper.getTopBerStats(20);
        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "=== BER Culling Diagnostics (dist=%d, rendered=%d, culled=%d) ===",
                BerCullingHelper.getCachedMaxDist(),
                BerCullingHelper.getRenderedCount(),
                BerCullingHelper.getCulledCount()
        )).withStyle(ChatFormatting.GOLD));

        if (top.isEmpty()) {
            sendAndLog(source, logBuf, Component.literal("No BER dispatches recorded yet.").withStyle(ChatFormatting.GREEN));
        } else {
            int rank = 1;
            for (BerCullingHelper.BerDiagSnapshot s : top) {
                sendAndLog(source, logBuf, Component.literal(String.format(
                        "#%02d rendered=%d, culled=%d%s | %s",
                        rank++, s.rendered(), s.culled(),
                        s.whitelisted() ? " [WHITELIST]" : "",
                        s.className()
                )).withStyle(ChatFormatting.WHITE));
            }
        }
        AtmMemOpt.LOGGER.info("\n{}", logBuf);
        return 1;
    }

    private static int executeBerDiagReset(CommandSourceStack source) {
        BerCullingHelper.resetDiag();
        source.sendSuccess(() -> Component.literal(
                "[AtmMemOpt] BER diagnostic counters cleared."
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int executeEntityDiagReport(CommandSourceStack source) {
        List<EntityCullingHelper.EntityDiagSnapshot> top = EntityCullingHelper.getTopEntityStats(20);
        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "=== Entity Culling Diagnostics (dist=%d/item=%d/xp=%d, rendered=%d, culled=%d) ===",
                EntityCullingHelper.getCachedGeneralDist(),
                EntityCullingHelper.getCachedItemDist(),
                EntityCullingHelper.getCachedXpDist(),
                EntityCullingHelper.getRenderedCount(),
                EntityCullingHelper.getCulledCount()
        )).withStyle(ChatFormatting.GOLD));

        if (top.isEmpty()) {
            sendAndLog(source, logBuf, Component.literal("No entity renders recorded yet.").withStyle(ChatFormatting.GREEN));
        } else {
            int rank = 1;
            for (EntityCullingHelper.EntityDiagSnapshot s : top) {
                sendAndLog(source, logBuf, Component.literal(String.format(
                        "#%02d rendered=%d, culled=%d%s | %s",
                        rank++, s.rendered(), s.culled(),
                        s.whitelisted() ? " [WHITELIST]" : "",
                        s.className()
                )).withStyle(ChatFormatting.WHITE));
            }
        }
        AtmMemOpt.LOGGER.info("\n{}", logBuf);
        return 1;
    }

    private static int executeEntityDiagReset(CommandSourceStack source) {
        EntityCullingHelper.resetDiag();
        source.sendSuccess(() -> Component.literal(
                "[AtmMemOpt] Entity diagnostic counters cleared."
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int executeSpritesDiagReport(CommandSourceStack source) {
        SpriteMemoryDiagnostics.Report report = SpriteMemoryDiagnostics.inspect();
        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "=== SpriteAtlas NativeImage Diagnostics (atlases=%d, sprites=%d, animated=%d, liveImg=%d, closedImg=%d, estNative=%s) ===",
                report.atlases().size(),
                report.totalSprites(),
                report.totalAnimated(),
                report.totalLiveImages(),
                report.totalClosedImages(),
                fmtMB(report.totalNativeBytes())
        )).withStyle(ChatFormatting.GOLD));

        int rank = 1;
        for (SpriteMemoryDiagnostics.AtlasSummary a : report.atlases()) {
            sendAndLog(source, logBuf, Component.literal(String.format(
                    "#%02d %s | sprites=%d (anim=%d), liveImg=%d, closedImg=%d, estNative=%s",
                    rank++, a.atlasId(), a.sprites(), a.animated(), a.liveImages(), a.closedImages(), fmtMB(a.nativeBytes())
            )).withStyle(ChatFormatting.WHITE));
        }
        AtmMemOpt.LOGGER.info("\n{}", logBuf);
        return 1;
    }

    private static int executeAllDiagnostics(CommandSourceStack source) {
        executeStatus(source);
        executeSpritesDiagReport(source);
        executeBerDiagReport(source);
        executeEntityDiagReport(source);
        if (NativeImageTracker.isMixinApplied()) {
            executeNativeImageReport(source);
        }
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
