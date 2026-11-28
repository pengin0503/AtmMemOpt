package com.atmmemopt.command;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.AtmMemOptMixinPlugin;
import com.atmmemopt.opt.chunk.PalettedContainerOptHelper;
import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import com.atmmemopt.opt.components.PatchedMapOptStats;
import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import com.atmmemopt.opt.owo.OwoDerivedOptHelper;
import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import com.atmmemopt.opt.tags.TagEntryOptHelper;
import com.atmmemopt.opt.tick.EntityMergeOptHelper;
import com.atmmemopt.opt.tick.MobAiThrottleHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

/**
 * Registers server/common /atmmemopt-server commands (and /atmmemopt on dedicated servers)
 * without referencing any client-only classes so Dedicated Server environments never hit
 * NoClassDefFoundError.
 */
public final class AtmMemOptServerCommands {
    private AtmMemOptServerCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerRoot(dispatcher, "atmmemopt-server");
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            registerRoot(dispatcher, "atmmemopt");
        }
    }

    private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher, String literal) {
        dispatcher.register(
                Commands.literal(literal)
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(ctx -> executeServerStatus(ctx.getSource())))
                        .then(Commands.literal("purge-caches")
                                .executes(ctx -> executeServerPurgeCaches(ctx.getSource())))
        );
    }

    public static String formatModuleState(String moduleId, boolean configEnabled) {
        boolean applied = AtmMemOptMixinPlugin.isModuleApplied(moduleId);
        if (applied == configEnabled) {
            return applied ? "ON" : "OFF";
        }
        return applied ? "ON (off on restart)" : "OFF (on on restart)";
    }

    public static void appendJvmMemorySummary(CommandSourceStack source, StringBuilder logBuf) {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

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
            if (name.contains("Metaspace") || name.contains("Old") || name.contains("ZHeap") || name.contains("Shenandoah")) {
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
    }

    public static void appendCommonModulesStatus(CommandSourceStack source, StringBuilder logBuf) {
        boolean owoCfg = ModList.get().isLoaded("owo") && AtmMemOptConfig.ENABLE_OWO_DERIVED_MAP_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M1 (owo DerivedMap): %s | unwrapped=%d, customKept=%d",
                formatModuleState("M1", owoCfg),
                ModList.get().isLoaded("owo") ? OwoBridge.getUnwrappedCount() : 0L,
                ModList.get().isLoaded("owo") ? OwoBridge.getKeptCustomCount() : 0L
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M2 (PatchedComponentMap): %s | hits=%d (applySkip=%d, restoreFast=%d, fromEmpty=%d)",
                formatModuleState("M2", AtmMemOptConfig.ENABLE_PATCHED_COMPONENT_MAP_OPT.get()),
                PatchedMapOptStats.getTotalHits(),
                PatchedMapOptStats.getSkippedEmptyApply(),
                PatchedMapOptStats.getNormalizedEmptyRestore(),
                PatchedMapOptStats.getFastFromEmptyPatch()
        )).withStyle(ChatFormatting.GREEN));

        boolean m3Cfg = ModList.get().isLoaded("irons_spellbooks") && AtmMemOptConfig.ENABLE_IRONS_SPELL_CONTAINER_OPT.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M3 (Iron's SpellContainer): %s | containerHits=%d, slotHits=%d, dataHits=%d, getOrCreateSaved=%d"
                        + ", caches(data=%d/%d, slot=%d/%d, emptyContainer=%d/%d, emptySlotArrays=%d/%d, container=%d/%d, evicted=%d)",
                formatModuleState("M3", m3Cfg),
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getContainerHits() : 0L,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getSpellSlotHits() : 0L,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getSpellDataHits() : 0L,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getGetOrCreateAvoided() : 0L,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getSpellDataCacheSize() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getCachedMaxStrongEntries() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getSpellSlotCacheSize() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getCachedMaxStrongEntries() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getEmptyContainerCacheSize() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getCachedMaxStrongEntries() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getEmptySlotArraysSize() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getCachedMaxEmptySlotLength() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getContainerCacheSize() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getCachedMaxContainerEntries() : 0,
                ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.getEvictions() : 0L
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M6 (ItemEnchantments): %s | totalHits=%d (emptyUnified=%d, internHits=%d, cache=%d/%d, evicted=%d)",
                formatModuleState("M6", AtmMemOptConfig.ENABLE_ITEM_ENCHANTMENTS_OPT.get()),
                ItemEnchantmentsOptHelper.getTotalHits(),
                ItemEnchantmentsOptHelper.getEmptyUnified(),
                ItemEnchantmentsOptHelper.getInternHits(),
                ItemEnchantmentsOptHelper.getCacheSize(),
                ItemEnchantmentsOptHelper.getCachedMaxEntries(),
                ItemEnchantmentsOptHelper.getEvictions()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M7 (VoxelShape): %s | doubleListHits=%d, discreteHits=%d, shapeHits=%d, cacheSize=%d/%d"
                        + ", doubleListSize=%d/%d, discreteSize=%d/%d, cubeSize=%d, cubeInternHits=%d, cubeSkipped=%d"
                        + ", capRej=%d, stale=%d, drained=%d, dlCapRej=%d, longCoordSkip=%d, discreteCapRej=%d, discreteProbeHits=%d, evicted=%d",
                formatModuleState("M7", AtmMemOptConfig.ENABLE_VOXEL_SHAPE_OPT.get()),
                VoxelShapeOptHelper.getDoubleListHits(),
                VoxelShapeOptHelper.getDiscreteHits(),
                VoxelShapeOptHelper.getShapeHits(),
                VoxelShapeOptHelper.getShapeCacheSize(),
                VoxelShapeOptHelper.getCachedMaxShapeEntries(),
                VoxelShapeOptHelper.getDoubleListCacheSize(),
                VoxelShapeOptHelper.getCachedMaxDoubleListEntries(),
                VoxelShapeOptHelper.getDiscreteCacheSize(),
                VoxelShapeOptHelper.getCachedMaxDiscreteEntries(),
                VoxelShapeOptHelper.getCubeCacheSize(),
                VoxelShapeOptHelper.getCubeInternHits(),
                VoxelShapeOptHelper.getCubeSkipped(),
                VoxelShapeOptHelper.getShapeCapRejections(),
                VoxelShapeOptHelper.getShapeStaleRefs(),
                VoxelShapeOptHelper.getShapeDrained(),
                VoxelShapeOptHelper.getDoubleListCapRejections(),
                VoxelShapeOptHelper.getLongCoordListSkips(),
                VoxelShapeOptHelper.getDiscreteCapRejections(),
                VoxelShapeOptHelper.getDiscreteProbeHits(),
                VoxelShapeOptHelper.getEvictions()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M10 (DataComponentPatch): %s | internHits=%d, mapShares=%d, emptyUnified=%d, cacheSize=%d/%d"
                        + ", skippedHeavy=%d, keyPayloadBytes=%d, evicted=%d",
                formatModuleState("M10", AtmMemOptConfig.ENABLE_DATA_COMPONENT_PATCH_OPT.get()),
                DataComponentPatchOptHelper.getInternHits(),
                DataComponentPatchOptHelper.getMapShares(),
                DataComponentPatchOptHelper.getEmptyUnified(),
                DataComponentPatchOptHelper.getCacheSize(),
                DataComponentPatchOptHelper.getCachedMaxEntries(),
                DataComponentPatchOptHelper.getSkippedHeavyKeys(),
                DataComponentPatchOptHelper.getEstimatedKeyBytes(),
                DataComponentPatchOptHelper.getEvictions()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M11 (PalettedContainer): %s | detectorsAvoided=%d",
                formatModuleState("M11", AtmMemOptConfig.ENABLE_PALETTED_CONTAINER_OPT.get()),
                PalettedContainerOptHelper.getDetectorsAvoided()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M12 (ResourceLocation): %s | nsHits=%d, instHits=%d, cacheSize=%d/%d, namespaceSize=%d/%d, instEvicted=%d, nsEvicted=%d",
                formatModuleState("M12", AtmMemOptConfig.ENABLE_RESOURCE_LOCATION_OPT.get()),
                ResourceLocationOptHelper.getNamespaceHits(),
                ResourceLocationOptHelper.getInstanceHits(),
                ResourceLocationOptHelper.getCacheSize(),
                ResourceLocationOptHelper.getCachedMaxEntries(),
                ResourceLocationOptHelper.getNamespaceCacheSize(),
                ResourceLocationOptHelper.getCachedMaxNamespaceEntries(),
                ResourceLocationOptHelper.getInstanceEvictions(),
                ResourceLocationOptHelper.getNamespaceEvictions()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M13/18 (Climate.Parameter): %s | paramHits=%d, arrayHits=%d, param=%d/%d, array=%d/%d, evicted=%d",
                formatModuleState("M13", AtmMemOptConfig.ENABLE_CLIMATE_PARAMETER_OPT.get()),
                ClimateParameterOptHelper.getInternHits(),
                ClimateParameterOptHelper.getArrayHits(),
                ClimateParameterOptHelper.getCacheSize(),
                ClimateParameterOptHelper.getCachedMaxEntries(),
                ClimateParameterOptHelper.getArrayCacheSize(),
                ClimateParameterOptHelper.getCachedArrayMaxEntries(),
                ClimateParameterOptHelper.getEvictions()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M16 (TagEntry): %s | internHits=%d, cacheSize=%d/%d, evicted=%d",
                formatModuleState("M16", AtmMemOptConfig.ENABLE_TAG_ENTRY_OPT.get()),
                TagEntryOptHelper.getInternHits(),
                TagEntryOptHelper.getCacheSize(),
                TagEntryOptHelper.getCachedMaxEntries(),
                TagEntryOptHelper.getEvictions()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M20/21 (TPS Opt): mobAi=%s (dist=%d/%d, skipped=%d), merge=%s (item=%.1f/xp=%.1f, scans=%d/%d)",
                formatModuleState("M20", AtmMemOptConfig.ENABLE_MOB_AI_THROTTLING.get()),
                MobAiThrottleHelper.getCachedNearDist(),
                MobAiThrottleHelper.getCachedFarDist(),
                MobAiThrottleHelper.getThrottledTicks(),
                formatModuleState("M21", AtmMemOptConfig.ENABLE_ENTITY_MERGE_OPT.get()),
                EntityMergeOptHelper.getCachedItemRadius(),
                EntityMergeOptHelper.getCachedXpRadius(),
                EntityMergeOptHelper.getItemScansExpanded(),
                EntityMergeOptHelper.getXpScansExpanded()
        )).withStyle(ChatFormatting.GREEN));
    }

    private static int executeServerStatus(CommandSourceStack source) {
        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal("=== AtmMemOpt Server Status ===").withStyle(ChatFormatting.GOLD));
        appendJvmMemorySummary(source, logBuf);
        appendCommonModulesStatus(source, logBuf);
        AtmMemOpt.LOGGER.info("\n{}", logBuf);
        return 1;
    }

    private static int executeServerPurgeCaches(CommandSourceStack source) {
        int m3Cleared = ModList.get().isLoaded("irons_spellbooks") ? IronsSpellbooksBridge.clearWeakCache() : 0;
        int m6Cleared = ItemEnchantmentsOptHelper.clearCache();
        int m7Cleared = VoxelShapeOptHelper.clearWeakCaches();
        int m10Cleared = DataComponentPatchOptHelper.clearCache();
        int m12Cleared = ResourceLocationOptHelper.clearWeakCache();
        int m13Cleared = ClimateParameterOptHelper.clearCache();
        int m16Cleared = TagEntryOptHelper.clearCache();

        Component msg = Component.literal(String.format(
                "[AtmMemOpt] Purged server caches: M3=%d, M6=%d, M7=%d, M10=%d, M12=%d, M13=%d, M16=%d",
                m3Cleared, m6Cleared, m7Cleared, m10Cleared, m12Cleared, m13Cleared, m16Cleared
        )).withStyle(ChatFormatting.GREEN);
        source.sendSuccess(() -> msg, false);
        AtmMemOpt.LOGGER.info("{}", msg.getString());
        return 1;
    }

    public static void sendAndLog(CommandSourceStack source, StringBuilder logBuf, Component msg) {
        source.sendSuccess(() -> msg, false);
        logBuf.append(msg.getString()).append('\n');
    }

    public static String fmtMB(long bytes) {
        if (bytes < 0) {
            return "N/A";
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static final class OwoBridge {
        private static long getUnwrappedCount() {
            return OwoDerivedOptHelper.getUnwrappedCount();
        }

        private static long getKeptCustomCount() {
            return OwoDerivedOptHelper.getKeptCustomCount();
        }
    }

    /** Package-private so the client command class can reuse it instead of touching the helper directly. */
    static final class IronsSpellbooksBridge {
        private static long getContainerHits() {
            return SpellContainerOptHelper.getContainerHits();
        }

        private static long getSpellSlotHits() {
            return SpellContainerOptHelper.getSpellSlotHits();
        }

        private static long getSpellDataHits() {
            return SpellContainerOptHelper.getSpellDataHits();
        }

        private static long getGetOrCreateAvoided() {
            return SpellContainerOptHelper.getGetOrCreateAvoided();
        }

        static int clearWeakCache() {
            return SpellContainerOptHelper.clearWeakCache();
        }

        static int getSpellDataCacheSize() {
            return SpellContainerOptHelper.getSpellDataCacheSize();
        }

        static int getSpellSlotCacheSize() {
            return SpellContainerOptHelper.getSpellSlotCacheSize();
        }

        static int getEmptyContainerCacheSize() {
            return SpellContainerOptHelper.getEmptyContainerCacheSize();
        }

        static int getEmptySlotArraysSize() {
            return SpellContainerOptHelper.getEmptySlotArraysSize();
        }

        static int getContainerCacheSize() {
            return SpellContainerOptHelper.getContainerCacheSize();
        }

        static int getCachedMaxStrongEntries() {
            return SpellContainerOptHelper.getCachedMaxStrongEntries();
        }

        static int getCachedMaxContainerEntries() {
            return SpellContainerOptHelper.getCachedMaxContainerEntries();
        }

        static int getCachedMaxEmptySlotLength() {
            return SpellContainerOptHelper.getCachedMaxEmptySlotLength();
        }

        static long getEvictions() {
            return SpellContainerOptHelper.getEvictions();
        }
    }
}
