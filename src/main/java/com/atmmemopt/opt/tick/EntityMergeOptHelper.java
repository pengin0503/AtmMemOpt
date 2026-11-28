package com.atmmemopt.opt.tick;

import com.atmmemopt.config.AtmMemOptConfig;

import java.util.concurrent.atomic.LongAdder;

/**
 * Module 21: Configurable expanded merge search radii for ItemEntity and ExperienceOrb
 * to merge dense drops faster and reduce entity tick/render overhead.
 */
public final class EntityMergeOptHelper {
    private static final LongAdder ITEM_SCANS_EXPANDED = new LongAdder();
    private static final LongAdder XP_SCANS_EXPANDED = new LongAdder();

    private static volatile double cachedItemRadius = 1.5D;
    private static volatile double cachedXpRadius = 1.5D;
    private static volatile boolean configLoaded = false;

    private EntityMergeOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedItemRadius = AtmMemOptConfig.ITEM_MERGE_RADIUS.get();
            cachedXpRadius = AtmMemOptConfig.XP_MERGE_RADIUS.get();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
        }
    }

    private static void ensureConfigLoaded() {
        if (!configLoaded) {
            refreshConfig();
        }
    }

    public static double getItemMergeRadius() {
        ensureConfigLoaded();
        return cachedItemRadius;
    }

    public static double getXpMergeRadius() {
        ensureConfigLoaded();
        return cachedXpRadius;
    }

    public static void recordItemScanExpanded() {
        ITEM_SCANS_EXPANDED.increment();
    }

    public static void recordXpScanExpanded() {
        XP_SCANS_EXPANDED.increment();
    }

    public static double getCachedItemRadius() {
        ensureConfigLoaded();
        return cachedItemRadius;
    }

    public static double getCachedXpRadius() {
        ensureConfigLoaded();
        return cachedXpRadius;
    }

    public static long getItemScansExpanded() {
        return ITEM_SCANS_EXPANDED.sum();
    }

    public static long getXpScansExpanded() {
        return XP_SCANS_EXPANDED.sum();
    }
}
