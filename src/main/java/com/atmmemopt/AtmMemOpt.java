package com.atmmemopt;

import com.atmmemopt.command.AtmMemOptCommands;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.atmmemopt.opt.model.BakedQuadOptHelper;
import com.atmmemopt.opt.patch.DataComponentPatchOptHelper;
import com.atmmemopt.opt.render.BerCullingHelper;
import com.atmmemopt.opt.render.EntityCullingHelper;
import com.atmmemopt.opt.render.ParticleCullingHelper;
import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import com.atmmemopt.opt.tags.TagEntryOptHelper;
import com.atmmemopt.opt.tick.EntityMergeOptHelper;
import com.atmmemopt.opt.tick.MobAiThrottleHelper;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
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

        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            ItemEnchantmentsOptHelper.clearCache();
            DataComponentPatchOptHelper.clearCache();
            ClimateParameterOptHelper.clearCache();
            TagEntryOptHelper.clearCache();
        });
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> {
            ItemEnchantmentsOptHelper.clearCache();
            DataComponentPatchOptHelper.clearCache();
            TagEntryOptHelper.clearCache();
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.register();
        }

        LOGGER.info("AtmMemOpt initialized.");
    }

    private static void refreshCachedConfig(ModConfig config) {
        if (config.getSpec() != AtmMemOptConfig.SPEC) {
            return;
        }
        DataComponentPatchOptHelper.refreshConfig();
        ResourceLocationOptHelper.refreshConfig();
        MobAiThrottleHelper.refreshConfig();
        EntityMergeOptHelper.refreshConfig();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            BerCullingHelper.refreshConfig();
            ParticleCullingHelper.refreshConfig();
            EntityCullingHelper.refreshConfig();
            BakedQuadOptHelper.refreshConfig();
        }
    }

    private static final class ClientInit {
        private static void register() {
            NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                    AtmMemOptCommands.register(event.getDispatcher()));
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
                ItemEnchantmentsOptHelper.clearCache();
                DataComponentPatchOptHelper.clearCache();
                ClimateParameterOptHelper.clearCache();
                TagEntryOptHelper.clearCache();
            });
        }
    }
}
