package com.atmmemopt;

import com.atmmemopt.command.AtmMemOptCommands;
import com.atmmemopt.command.AtmMemOptServerCommands;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.diag.NativeImageTracker;
import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import com.atmmemopt.opt.model.BakedQuadOptHelper;
import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import com.atmmemopt.opt.render.BerCullingHelper;
import com.atmmemopt.opt.render.EntityCullingHelper;
import com.atmmemopt.opt.render.ParticleCullingHelper;
import com.atmmemopt.opt.render.UnfocusedFpsLimitHelper;
import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import com.atmmemopt.opt.tags.TagEntryOptHelper;
import com.atmmemopt.opt.tick.EntityMergeOptHelper;
import com.atmmemopt.opt.tick.MobAiThrottleHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(AtmMemOpt.MOD_ID)
public class AtmMemOpt {
    public static final String MOD_ID = "atmmemopt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AtmMemOpt(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, AtmMemOptConfig.SPEC, "atmmemopt-common.toml");

        modEventBus.addListener((ModConfigEvent.Loading event) -> refreshCachedConfig(event.getConfig()));
        modEventBus.addListener((ModConfigEvent.Reloading event) -> refreshCachedConfig(event.getConfig()));

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                AtmMemOptServerCommands.register(event.getDispatcher()));

        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> clearWorldScopedCaches());
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
            // M3 joins M6/M10/M16 here: Iron's registry objects are replaced on tag reload.
            if (ModList.get().isLoaded("irons_spellbooks")) {
                IronsCacheBridge.clearWeakCache();
            }
            ItemEnchantmentsOptHelper.clearCache();
            DataComponentPatchOptHelper.clearCache();
            TagEntryOptHelper.clearCache();
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.register(modEventBus);
        }

        LOGGER.info("AtmMemOpt initialized.");
    }

    private static void clearWorldScopedCaches() {
        ItemEnchantmentsOptHelper.clearCache();
        DataComponentPatchOptHelper.clearCache();
        ClimateParameterOptHelper.clearCache();
        TagEntryOptHelper.clearCache();
        // Strong Class keys in the render diag maps must not survive logout/server stop.
        BerCullingHelper.clearClassStats();
        EntityCullingHelper.clearClassStats();
        if (ModList.get().isLoaded("irons_spellbooks")) {
            IronsCacheBridge.clearWeakCache();
        }
    }

    private static void refreshCachedConfig(ModConfig config) {
        if (config.getSpec() != AtmMemOptConfig.SPEC) {
            return;
        }
        ItemEnchantmentsOptHelper.refreshConfig();
        VoxelShapeOptHelper.refreshConfig();
        DataComponentPatchOptHelper.refreshConfig();
        ResourceLocationOptHelper.refreshConfig();
        ClimateParameterOptHelper.refreshConfig();
        TagEntryOptHelper.refreshConfig();
        if (ModList.get().isLoaded("irons_spellbooks")) {
            IronsCacheBridge.refreshConfig();
        }
        MobAiThrottleHelper.refreshConfig();
        EntityMergeOptHelper.refreshConfig();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.refreshConfig();
        }
    }

    private static final class IronsCacheBridge {
        private static void clearWeakCache() {
            SpellContainerOptHelper.clearWeakCache();
        }

        private static void refreshConfig() {
            SpellContainerOptHelper.refreshConfig();
        }
    }

    private static final class ClientInit {
        private static void register(IEventBus modEventBus) {
            NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                    AtmMemOptCommands.register(event.getDispatcher()));
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
                clearWorldScopedCaches();
                BakedQuadOptHelper.clearCache();
            });
            modEventBus.addListener((RegisterClientReloadListenersEvent event) ->
                    event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
                        int cleared = BakedQuadOptHelper.clearCache();
                        if (cleared > 0) {
                            LOGGER.debug("[AtmMemOpt] Cleared {} BakedQuad cache entries on resource reload.", cleared);
                        }
                    }));
        }

        private static void refreshConfig() {
            NativeImageTracker.refreshConfig();
            BerCullingHelper.refreshConfig();
            UnfocusedFpsLimitHelper.refreshConfig();
            ParticleCullingHelper.refreshConfig();
            EntityCullingHelper.refreshConfig();
            BakedQuadOptHelper.refreshConfig();
        }
    }
}
