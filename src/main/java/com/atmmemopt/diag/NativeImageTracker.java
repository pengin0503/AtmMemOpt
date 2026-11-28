package com.atmmemopt.diag;

import com.atmmemopt.config.AtmMemOptConfig;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 5: Lightweight stack-trace aggregator for NativeImage allocation/close tracking.
 * Active only when both the Mixin is enabled via config and runtime tracking is turned on.
 */
public final class NativeImageTracker {
    private static final int MAX_STACK_FRAMES = 6;
    private static final String SHALLOW_SITE = "<shallow>";
    private static final String OVERFLOW_SITE = "<overflow>";

    private static volatile boolean mixinApplied = false;
    private static volatile boolean runtimeEnabled = false;
    private static volatile int maxSites = 512;
    private static volatile boolean shallow = false;

    private static final ConcurrentHashMap<String, SiteStats> STATS = new ConcurrentHashMap<>();
    private static final LongAdder TOTAL_CREATED = new LongAdder();
    private static final LongAdder TOTAL_CLOSED = new LongAdder();
    private static final LongAdder DROPPED_SITES = new LongAdder();
    private static final LongAdder OVERFLOW_CLOSED = new LongAdder();

    private NativeImageTracker() {
    }

    public static void refreshConfig() {
        try {
            maxSites = Math.max(0, AtmMemOptConfig.NATIVE_IMAGE_DIAG_MAX_SITES.get());
            shallow = AtmMemOptConfig.NATIVE_IMAGE_DIAG_SHALLOW.get();
        } catch (IllegalStateException ignored) {
            // Config spec not loaded yet; keep defaults.
        }
    }

    public static void markMixinApplied() {
        mixinApplied = true;
        runtimeEnabled = true;
    }

    public static boolean isMixinApplied() {
        return mixinApplied;
    }

    public static boolean isRuntimeEnabled() {
        return mixinApplied && runtimeEnabled;
    }

    public static void setRuntimeEnabled(boolean enabled) {
        runtimeEnabled = enabled;
    }

    public static void reset() {
        STATS.clear();
        TOTAL_CREATED.reset();
        TOTAL_CLOSED.reset();
        DROPPED_SITES.reset();
        OVERFLOW_CLOSED.reset();
    }

    public static String recordAllocation() {
        if (!runtimeEnabled) {
            return null;
        }
        TOTAL_CREATED.increment();
        String site = captureCallerSite();
        SiteStats stats = STATS.get(site);
        if (stats == null) {
            // New site: synchronize only this rare path so the map stays strictly bounded.
            synchronized (STATS) {
                stats = STATS.get(site);
                if (stats == null) {
                    if (maxSites <= 0 || STATS.size() >= maxSites) {
                        DROPPED_SITES.increment();
                        return OVERFLOW_SITE;
                    }
                    stats = new SiteStats();
                    STATS.put(site, stats);
                }
            }
        }
        stats.created.increment();
        return site;
    }

    public static void recordClose(String site) {
        if (site == null) {
            return;
        }
        if (OVERFLOW_SITE.equals(site)) {
            OVERFLOW_CLOSED.increment();
            TOTAL_CLOSED.increment();
            return;
        }
        SiteStats stats = STATS.get(site);
        if (stats != null) {
            stats.closed.increment();
            TOTAL_CLOSED.increment();
        }
    }

    public static List<SiteSnapshot> getTopUnclosed(int limit) {
        return STATS.entrySet().stream()
                .map(e -> {
                    long created = e.getValue().created.sum();
                    long closed = e.getValue().closed.sum();
                    return new SiteSnapshot(e.getKey(), created, closed, Math.max(0L, created - closed));
                })
                .filter(s -> s.unclosed() > 0)
                .sorted(Comparator.comparingLong(SiteSnapshot::unclosed).reversed()
                        .thenComparing(Comparator.comparingLong(SiteSnapshot::created).reversed()))
                .limit(limit)
                .toList();
    }

    public static Totals getTotals() {
        long created = TOTAL_CREATED.sum();
        long closed = TOTAL_CLOSED.sum();
        return new Totals(created, closed, Math.max(0L, created - closed), STATS.size());
    }

    public static int getSiteCount() {
        return STATS.size();
    }

    public static int getMaxSites() {
        return maxSites;
    }

    public static long getDroppedSites() {
        return DROPPED_SITES.sum();
    }

    public static boolean isShallow() {
        return shallow;
    }

    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    private static String captureCallerSite() {
        if (shallow) {
            return SHALLOW_SITE;
        }
        List<String> frames = STACK_WALKER.walk(stream -> stream.filter(frame -> {
                    String cls = frame.getClassName();
                    return !cls.startsWith("com.atmmemopt.")
                            && !cls.equals("com.mojang.blaze3d.platform.NativeImage");
                })
                .limit(MAX_STACK_FRAMES)
                .map(frame -> shortenClassName(frame.getClassName()) + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .toList());
        return frames.isEmpty() ? "<unknown>" : String.join(" <- ", frames);
    }

    private static String shortenClassName(String full) {
        int lastDot = full.lastIndexOf('.');
        if (lastDot <= 0) {
            return full;
        }
        int prevDot = full.lastIndexOf('.', lastDot - 1);
        return prevDot > 0 ? full.substring(prevDot + 1) : full;
    }

    private static final class SiteStats {
        final LongAdder created = new LongAdder();
        final LongAdder closed = new LongAdder();
    }

    public record SiteSnapshot(String site, long created, long closed, long unclosed) {
    }

    public record Totals(long created, long closed, long unclosed, int distinctSites) {
    }
}
