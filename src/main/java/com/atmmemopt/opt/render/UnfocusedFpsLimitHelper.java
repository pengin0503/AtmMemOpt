package com.atmmemopt.opt.render;

import com.atmmemopt.config.AtmMemOptConfig;

/**
 * Module 9b: Caches the unfocused framerate limit safely so MinecraftFpsLimitMixin
 * never throws IllegalStateException during early boot before ModConfig is loaded
 * and avoids per-frame ModConfigSpec lookups.
 */
public final class UnfocusedFpsLimitHelper {
    private static volatile int cachedLimit = 15;
    private static volatile boolean configLoaded = false;

    private UnfocusedFpsLimitHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedLimit = AtmMemOptConfig.UNFOCUSED_FPS_LIMIT.get();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
            // Config spec not yet loaded during early splash screen
        }
    }

    public static int getCachedLimit() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedLimit;
    }
}
