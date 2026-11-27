package com.atmmemopt.diag;

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

    private static volatile boolean mixinApplied = false;
    private static volatile boolean runtimeEnabled = false;

    private static final ConcurrentHashMap<String, SiteStats> STATS = new ConcurrentHashMap<>();

    private NativeImageTracker() {
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
    }

    public static String recordAllocation() {
        if (!runtimeEnabled) {
            return null;
        }
        String site = captureCallerSite();
        STATS.computeIfAbsent(site, k -> new SiteStats()).created.increment();
        return site;
    }

    public static void recordClose(String site) {
        if (site == null) {
            return;
        }
        SiteStats stats = STATS.get(site);
        if (stats != null) {
            stats.closed.increment();
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
        long created = 0;
        long closed = 0;
        for (SiteStats s : STATS.values()) {
            created += s.created.sum();
            closed += s.closed.sum();
        }
        return new Totals(created, closed, Math.max(0L, created - closed), STATS.size());
    }

    private static String captureCallerSite() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder(192);
        int added = 0;
        for (StackTraceElement frame : trace) {
            String cls = frame.getClassName();
            if (cls.equals(Thread.class.getName())
                    || cls.startsWith("com.atmmemopt.")
                    || cls.equals("com.mojang.blaze3d.platform.NativeImage")) {
                continue;
            }
            if (added > 0) {
                sb.append(" <- ");
            }
            sb.append(shortenClassName(cls))
              .append('.')
              .append(frame.getMethodName())
              .append(':')
              .append(frame.getLineNumber());
            added++;
            if (added >= MAX_STACK_FRAMES) {
                break;
            }
        }
        return added == 0 ? "<unknown>" : sb.toString();
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
