package com.atmmemopt.opt.model;

import com.atmmemopt.config.AtmMemOptConfig;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 26: AUDIT ONLY - counts ModelBakery staging maps that are still reachable after a
 * client model reload. No ModelBakery state is cleared or modified in this stage.
 *
 * The last constructed bakery is held through a WeakReference (as Object, so this class can
 * also be loaded on a dedicated server). Sizes are read reflectively and guarded by
 * try/catch; unavailable fields are skipped rather than assumed.
 */
public final class ModelBakeryAuditHelper {
    private static final String[] STAGING_FIELDS = {
            "modelResources", "loadingStack", "unbakedCache", "bakedCache",
            "topLevelModels", "bakedTopLevelModels", "modelGroups"
    };

    private static volatile WeakReference<Object> lastBakery = new WeakReference<>(null);

    private static final LongAdder BAKES_CONSTRUCTED = new LongAdder();
    private static final LongAdder BAKES_COMPLETED = new LongAdder();
    private static final LongAdder AUDITS = new LongAdder();
    private static final LongAdder AUDITS_UNAVAILABLE = new LongAdder();
    private static final AtomicLong LAST_BAKE_NANOS = new AtomicLong();
    private static final AtomicLong LAST_AUDIT_NANOS = new AtomicLong();
    private static final AtomicLong STAGING_MAPS_RETAINED = new AtomicLong(-1L);
    private static final AtomicLong TOTAL_ENTRIES = new AtomicLong(-1L);

    private static volatile boolean auditEnabled = false;
    private static volatile boolean configLoaded = false;

    private ModelBakeryAuditHelper() {
    }

    public static void refreshConfig() {
        try {
            auditEnabled = AtmMemOptConfig.ENABLE_MODEL_BAKERY_CLEANUP.get();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
        }
    }

    private static boolean isAuditEnabled() {
        if (!configLoaded) {
            refreshConfig();
        }
        return auditEnabled;
    }

    /** Called from the client-only ModelBakery constructor mixin; never retains strongly. */
    public static void trackBakery(Object bakery) {
        BAKES_CONSTRUCTED.increment();
        lastBakery = new WeakReference<>(bakery);
    }

    /** Called from the client-only bakeModels() mixin. */
    public static void recordBakeComplete(long nanos) {
        BAKES_COMPLETED.increment();
        LAST_BAKE_NANOS.set(nanos);
    }

    /** Called from the existing AtmMemOpt client reload listener; counting only. */
    public static void audit() {
        if (!isAuditEnabled()) {
            return;
        }
        long start = System.nanoTime();
        AUDITS.increment();
        Object bakery = lastBakery.get();
        if (bakery == null) {
            AUDITS_UNAVAILABLE.increment();
            STAGING_MAPS_RETAINED.set(-1L);
            TOTAL_ENTRIES.set(-1L);
            LAST_AUDIT_NANOS.set(System.nanoTime() - start);
            return;
        }

        int mapsWithEntries = 0;
        long entries = 0L;
        for (String name : STAGING_FIELDS) {
            try {
                Field field = null;
                for (Class<?> type = bakery.getClass(); type != null; type = type.getSuperclass()) {
                    try {
                        field = type.getDeclaredField(name);
                        break;
                    } catch (NoSuchFieldException ignored) {
                        // Field may be declared on a superclass; keep walking up the hierarchy.
                    }
                }
                if (field == null) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(bakery);
                if (value == null) {
                    continue;
                }
                int count;
                if (value instanceof Map<?, ?> map) {
                    count = map.size();
                } else if (value instanceof Collection<?> collection) {
                    count = collection.size();
                } else {
                    continue;
                }
                if (count > 0) {
                    mapsWithEntries++;
                    entries += count;
                }
            } catch (Throwable ignored) {
                // Field renamed/removed by a mod or blocked by the module system: skip it.
            }
        }
        STAGING_MAPS_RETAINED.set(mapsWithEntries);
        TOTAL_ENTRIES.set(entries);
        LAST_AUDIT_NANOS.set(System.nanoTime() - start);
    }

    public static void reset() {
        BAKES_CONSTRUCTED.reset();
        BAKES_COMPLETED.reset();
        AUDITS.reset();
        AUDITS_UNAVAILABLE.reset();
        LAST_BAKE_NANOS.set(0L);
        LAST_AUDIT_NANOS.set(0L);
        STAGING_MAPS_RETAINED.set(-1L);
        TOTAL_ENTRIES.set(-1L);
        lastBakery = new WeakReference<>(null);
    }

    public static long getStagingMapsRetained() {
        return STAGING_MAPS_RETAINED.get();
    }

    public static long getTotalEntries() {
        return TOTAL_ENTRIES.get();
    }

    public static long getBakesConstructed() {
        return BAKES_CONSTRUCTED.sum();
    }

    public static long getBakesCompleted() {
        return BAKES_COMPLETED.sum();
    }

    public static long getAudits() {
        return AUDITS.sum();
    }

    public static long getAuditsUnavailable() {
        return AUDITS_UNAVAILABLE.sum();
    }

    public static long getLastBakeMs() {
        return LAST_BAKE_NANOS.get() / 1_000_000L;
    }

    public static long getLastAuditMs() {
        return LAST_AUDIT_NANOS.get() / 1_000_000L;
    }
}
