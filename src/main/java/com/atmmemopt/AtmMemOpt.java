package com.atmmemopt;

import com.atmmemopt.command.AtmMemOptCommands;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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

        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ItemEnchantmentsOptHelper.clearCache());
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> ItemEnchantmentsOptHelper.clearCache());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.register();
        }

        LOGGER.info("AtmMemOpt initialized.");
    }

    private static final class ClientInit {
        private static void register() {
            NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                    AtmMemOptCommands.register(event.getDispatcher()));
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                    ItemEnchantmentsOptHelper.clearCache());
        }
    }
}
