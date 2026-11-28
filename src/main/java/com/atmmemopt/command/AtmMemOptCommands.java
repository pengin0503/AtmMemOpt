package com.atmmemopt.command;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.diag.NativeImageTracker;
import com.atmmemopt.diag.SpriteMemoryDiagnostics;
import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.atmmemopt.opt.font.FontNativeImageHelper;
import com.atmmemopt.opt.iris.IrisCachePurger;
import com.atmmemopt.opt.keys.KeyInternOptHelper;
import com.atmmemopt.opt.model.BakedQuadOptHelper;
import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import com.atmmemopt.opt.render.BerCullingHelper;
import com.atmmemopt.opt.render.EntityCullingHelper;
import com.atmmemopt.opt.render.ParticleCullingHelper;
import com.atmmemopt.opt.render.UnfocusedFpsLimitHelper;
import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import com.atmmemopt.opt.startup.JeiFilterOptHelper;
import com.atmmemopt.opt.startup.ReloadProfiler;
import com.atmmemopt.opt.tags.TagEntryOptHelper;
import com.atmmemopt.opt.text.StyleOptHelper;
import com.atmmemopt.opt.texture.SpriteMipmapReleaseHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

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
        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal("=== AtmMemOpt Memory Status ===").withStyle(ChatFormatting.GOLD));
        AtmMemOptServerCommands.appendJvmMemorySummary(source, logBuf);
        AtmMemOptServerCommands.appendCommonModulesStatus(source, logBuf);

        boolean m4Cfg = ModList.get().isLoaded("iris") && AtmMemOptConfig.ENABLE_IRIS_SHADER_CACHE_PURGE.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M4 (Iris ShaderCache): %s | purges=%d, clearedEntries=%d",
                AtmMemOptServerCommands.formatModuleState("M4", m4Cfg),
                IrisCachePurger.getPurgeCount(),
                IrisCachePurger.getTotalEntriesCleared()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M5 (NativeImage Diag): mixin=%s, runtime=%s, sites=%d/%d, dropped=%d, shallow=%s",
                NativeImageTracker.isMixinApplied() ? "APPLIED" : "OFF",
                NativeImageTracker.isRuntimeEnabled() ? "RECORDING" : "PAUSED",
                NativeImageTracker.getSiteCount(),
                NativeImageTracker.getMaxSites(),
                NativeImageTracker.getDroppedSites(),
                NativeImageTracker.isShallow()
        )).withStyle(ChatFormatting.YELLOW));

        boolean m8JeiCfg = ModList.get().isLoaded("jei") && AtmMemOptConfig.ENABLE_JEI_PARALLEL_FILTER.get();
        boolean m8JeiStuffCfg = ModList.get().isLoaded("jeistuff") && AtmMemOptConfig.ENABLE_JEISTUFF_SKIP_LOOT_SCAN.get();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "M8 (Startup/Load): jeiParallel=%s (last=%dms, lazyTips=%d, deferred=%d, deferredDiscarded=%d), jeiStuffSkip=%s (skipped=%d), reloadProfiles=%d",
                AtmMemOptServerCommands.formatModuleState("M8a", m8JeiCfg),
                JeiFilterOptHelper.getLastBuildMs(),
                JeiFilterOptHelper.getLazyTooltipBuilds(),
                JeiFilterOptHelper.getDeferredElementCount(),
                JeiFilterOptHelper.getDeferredDiscarded(),
                AtmMemOptServerCommands.formatModuleState("M8b", m8JeiStuffCfg),
                ReloadProfiler.getJeiStuffLootSkipped(),
                ReloadProfiler.getReloadProfilesLogged()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M9a/b (Render Opt): berCull=%s (dist=%d, culled=%d, rendered=%d, classStats=%d, cacheEvals=%d), unfocusedFps=%s (%dfps)",
                AtmMemOptServerCommands.formatModuleState("M9a", AtmMemOptConfig.ENABLE_BER_DISTANCE_CULLING.get()),
                BerCullingHelper.getCachedMaxDist(),
                BerCullingHelper.getCulledCount(),
                BerCullingHelper.getRenderedCount(),
                BerCullingHelper.getClassStatsSize(),
                BerCullingHelper.getWhitelistCacheEvaluations(),
                AtmMemOptServerCommands.formatModuleState("M9b", AtmMemOptConfig.ENABLE_UNFOCUSED_FPS_LIMIT.get()),
                UnfocusedFpsLimitHelper.getCachedLimit()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M9c/d (Cull Opt): particleCull=%s (dist=%d, culled=%d [dist=%d, unfocus=%d]), entityCull=%s (dist=%d/%d/%d, culled=%d, classStats=%d, cacheEvals=%d)",
                AtmMemOptServerCommands.formatModuleState("M9c", AtmMemOptConfig.ENABLE_PARTICLE_DISTANCE_CULLING.get()),
                ParticleCullingHelper.getCachedMaxDist(),
                ParticleCullingHelper.getTotalCulled(),
                ParticleCullingHelper.getCulledDistance(),
                ParticleCullingHelper.getCulledUnfocused(),
                AtmMemOptServerCommands.formatModuleState("M9d", AtmMemOptConfig.ENABLE_ENTITY_DISTANCE_CULLING.get()),
                EntityCullingHelper.getCachedGeneralDist(),
                EntityCullingHelper.getCachedItemDist(),
                EntityCullingHelper.getCachedXpDist(),
                EntityCullingHelper.getCulledCount(),
                EntityCullingHelper.getClassStatsSize(),
                EntityCullingHelper.getWhitelistCacheEvaluations()
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M14 (SpriteMipmap): %s | spritesTrimmed=%d, reTrims=%d, mipImagesClosed=%d, estFreed=%s",
                AtmMemOptServerCommands.formatModuleState("M14", AtmMemOptConfig.ENABLE_SPRITE_MIPMAP_RELEASE.get()),
                SpriteMipmapReleaseHelper.getSpritesTrimmed(),
                SpriteMipmapReleaseHelper.getReTrims(),
                SpriteMipmapReleaseHelper.getImagesClosed(),
                fmtMB(SpriteMipmapReleaseHelper.getEstimatedBytesFreed())
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M24 (Font NativeImage): %s (AUDIT ONLY - no release) | imagesClosed=%d, estFreed=%s, observed=%d, providerCloses=%d, estObserved=%s",
                AtmMemOptServerCommands.formatModuleState("M24", AtmMemOptConfig.ENABLE_FONT_NATIVE_IMAGE_RELEASE.get()),
                FontNativeImageHelper.getImagesClosed(),
                fmtMB(FontNativeImageHelper.getEstimatedBytesFreed()),
                FontNativeImageHelper.getImagesObserved(),
                FontNativeImageHelper.getProviderCloses(),
                fmtMB(FontNativeImageHelper.getEstimatedObservedBytes())
        )).withStyle(ChatFormatting.GREEN));

        sendAndLog(source, logBuf, Component.literal(String.format(
                "M15/17 (BakedQuad): %s | vertHits=%d, quadHits=%d, estSaved=%s, vertex=%d/%d, quad=%d/%d, capRej=%d/%d, postBake=%d, evicted=%d",
                AtmMemOptServerCommands.formatModuleState("M15", AtmMemOptConfig.ENABLE_BAKED_QUAD_VERTEX_DEDUP.get()),
                BakedQuadOptHelper.getVertexHits(),
                BakedQuadOptHelper.getQuadHits(),
                fmtMB(BakedQuadOptHelper.getEstimatedBytesSaved()),
                BakedQuadOptHelper.getCacheSize(),
                BakedQuadOptHelper.getCachedMaxEntries(),
                BakedQuadOptHelper.getQuadCacheSize(),
                BakedQuadOptHelper.getCachedMaxQuadEntries(),
                BakedQuadOptHelper.getVertexCapRejections(),
                BakedQuadOptHelper.getQuadCapRejections(),
                BakedQuadOptHelper.getInternedPostBake(),
                BakedQuadOptHelper.getEvictions()
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
                "=== NativeImage Diagnostics (created=%d, closed=%d, unclosed=%d, sites=%d/%d, dropped=%d, shallow=%s, recording=%s) ===",
                totals.created(), totals.closed(), totals.unclosed(), totals.distinctSites(),
                NativeImageTracker.getMaxSites(), NativeImageTracker.getDroppedSites(),
                NativeImageTracker.isShallow(),
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
        // Route through the server-side bridge; no direct optional-helper reference here.
        int m3Cleared = ModList.get().isLoaded("irons_spellbooks")
                ? AtmMemOptServerCommands.IronsSpellbooksBridge.clearWeakCache() : 0;
        int m4Cleared = ModList.get().isLoaded("iris") ? IrisCachePurger.purgeNow().totalEntries() : 0;
        int m6Cleared = ItemEnchantmentsOptHelper.clearCache();
        int m7Cleared = VoxelShapeOptHelper.clearWeakCaches();
        int m10Cleared = DataComponentPatchOptHelper.clearCache();
        int m12Cleared = ResourceLocationOptHelper.clearWeakCache();
        int m13Cleared = ClimateParameterOptHelper.clearCache();
        int m15Cleared = BakedQuadOptHelper.clearCache();
        int m16Cleared = TagEntryOptHelper.clearCache();
        int m22Cleared = StyleOptHelper.clearCache();
        int m23Cleared = KeyInternOptHelper.clearCache();

        Component msg = Component.literal(String.format(
                "[AtmMemOpt] Purged caches: M3=%d, M4=%d, M6=%d, M7=%d, M10=%d, M12=%d, M13=%d, M15=%d, M16=%d, M22=%d, M23=%d",
                m3Cleared, m4Cleared, m6Cleared, m7Cleared, m10Cleared, m12Cleared, m13Cleared, m15Cleared, m16Cleared,
                m22Cleared, m23Cleared
        )).withStyle(ChatFormatting.GREEN);
        source.sendSuccess(() -> msg, false);
        AtmMemOpt.LOGGER.info("{}", msg.getString());
        return 1;
    }

    private static int executeBerDiagReport(CommandSourceStack source) {
        List<BerCullingHelper.BerDiagSnapshot> top = BerCullingHelper.getTopBerStats(20);
        StringBuilder logBuf = new StringBuilder();
        sendAndLog(source, logBuf, Component.literal(String.format(
                "=== BER Culling Diagnostics (dist=%d, rendered=%d, culled=%d, classStats=%d, cacheEvals=%d) ===",
                BerCullingHelper.getCachedMaxDist(),
                BerCullingHelper.getRenderedCount(),
                BerCullingHelper.getCulledCount(),
                BerCullingHelper.getClassStatsSize(),
                BerCullingHelper.getWhitelistCacheEvaluations()
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
                "=== Entity Culling Diagnostics (dist=%d/item=%d/xp=%d, rendered=%d, culled=%d, classStats=%d, cacheEvals=%d) ===",
                EntityCullingHelper.getCachedGeneralDist(),
                EntityCullingHelper.getCachedItemDist(),
                EntityCullingHelper.getCachedXpDist(),
                EntityCullingHelper.getRenderedCount(),
                EntityCullingHelper.getCulledCount(),
                EntityCullingHelper.getClassStatsSize(),
                EntityCullingHelper.getWhitelistCacheEvaluations()
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
