package com.atmmemopt.opt.startup;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 8c: Lightweight reload listener timer that wraps PreparableReloadListeners
 * in SimpleReloadInstance and logs the Top 10 slowest listeners (prepare + apply ms).
 *
 * Module 25 (diagnostics only): when enableReloadArtifactAudit is on, also measures the
 * change in Runtime heap-used across each listener's apply task and reports the largest
 * growers next to the timings. Nothing is cleared and no reload state is modified.
 */
public final class ReloadProfiler {
    private static final LongAdder JEISTUFF_LOOT_SKIPPED = new LongAdder();
    private static final AtomicLong RELOAD_PROFILES_LOGGED = new AtomicLong();

    private static final LongAdder AUDIT_SESSIONS = new LongAdder();
    private static final LongAdder AUDIT_SAMPLES = new LongAdder();
    private static final LongAdder TOTAL_GROWTH_BYTES = new LongAdder();
    private static final AtomicLong MAX_GROWTH_BYTES = new AtomicLong();
    private static volatile List<GrowthSnapshot> lastTopGrowth = List.of();

    private static volatile boolean auditEnabled = false;
    private static volatile boolean configLoaded = false;

    private ReloadProfiler() {
    }

    public static void refreshConfig() {
        try {
            auditEnabled = AtmMemOptConfig.ENABLE_RELOAD_ARTIFACT_AUDIT.get();
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

    public static void recordJeiStuffLootSkipped() {
        JEISTUFF_LOOT_SKIPPED.increment();
    }

    public static long getJeiStuffLootSkipped() {
        return JEISTUFF_LOOT_SKIPPED.sum();
    }

    public static long getReloadProfilesLogged() {
        return RELOAD_PROFILES_LOGGED.get();
    }

    public static boolean isAuditActive() {
        return isAuditEnabled();
    }

    public static long getAuditSessions() {
        return AUDIT_SESSIONS.sum();
    }

    /** Number of measured apply tasks (a listener may submit more than one). */
    public static long getAuditSamples() {
        return AUDIT_SAMPLES.sum();
    }

    public static long getTotalGrowthBytes() {
        return TOTAL_GROWTH_BYTES.sum();
    }

    public static long getMaxGrowthBytes() {
        return MAX_GROWTH_BYTES.get();
    }

    public static List<GrowthSnapshot> getLastTopGrowth() {
        return lastTopGrowth;
    }

    public static Session createSession(List<PreparableReloadListener> listeners) {
        ConcurrentLinkedQueue<ListenerTiming> timings = new ConcurrentLinkedQueue<>();
        List<PreparableReloadListener> wrapped = new ArrayList<>(listeners.size());
        boolean audit = isAuditEnabled();
        for (PreparableReloadListener delegate : listeners) {
            wrapped.add(new TimedReloadListener(delegate, timings, audit));
        }
        return new Session(wrapped, timings, System.nanoTime(), audit);
    }

    public static final class Session {
        public final List<PreparableReloadListener> wrappedListeners;
        private final ConcurrentLinkedQueue<ListenerTiming> timings;
        private final long startNanos;
        private final boolean audit;

        Session(List<PreparableReloadListener> wrappedListeners, ConcurrentLinkedQueue<ListenerTiming> timings,
                long startNanos, boolean audit) {
            this.wrappedListeners = wrappedListeners;
            this.timings = timings;
            this.startNanos = startNanos;
            this.audit = audit;
        }

        public void logTop10() {
            long totalWallMs = (System.nanoTime() - startNanos) / 1_000_000L;
            List<ListenerTiming> list = new ArrayList<>(timings);
            list.sort(Comparator.comparingLong(ListenerTiming::totalMs).reversed());

            StringBuilder sb = new StringBuilder(512);
            sb.append("[AtmMemOpt] Resource/Datapack Reload completed in ")
              .append(totalWallMs).append(" ms (listeners=").append(list.size()).append(") - Top 10:\n");
            int limit = Math.min(10, list.size());
            for (int i = 0; i < limit; i++) {
                ListenerTiming t = list.get(i);
                sb.append(String.format("  #%02d %6d ms (prep=%5d ms, apply=%5d ms) | %s\n",
                        i + 1, t.totalMs(), t.prepMs(), t.applyMs(), t.name));
            }
            RELOAD_PROFILES_LOGGED.incrementAndGet();

            if (audit) {
                List<ListenerTiming> growth = new ArrayList<>(list);
                growth.sort(Comparator.comparingLong((ListenerTiming t) -> t.heapDeltaBytes.sum()).reversed());
                List<GrowthSnapshot> top = new ArrayList<>();
                long sessionGrowth = 0L;
                long sessionMax = 0L;
                for (ListenerTiming t : growth) {
                    long delta = t.heapDeltaBytes.sum();
                    sessionGrowth += delta;
                    sessionMax = Math.max(sessionMax, delta);
                    if (top.size() < 5) {
                        top.add(new GrowthSnapshot(t.name, delta, t.totalMs()));
                    }
                }
                lastTopGrowth = List.copyOf(top);
                AUDIT_SESSIONS.increment();
                TOTAL_GROWTH_BYTES.add(sessionGrowth);
                MAX_GROWTH_BYTES.accumulateAndGet(sessionMax, Math::max);
                sb.append("[AtmMemOpt] M25 heap-growth audit (Runtime used-heap deltas, no GC forced, no state cleared):\n");
                for (int i = 0; i < top.size(); i++) {
                    GrowthSnapshot g = top.get(i);
                    sb.append(String.format("  #%02d %+8.2f MB (%6d ms) | %s\n",
                            i + 1, g.deltaBytes() / (1024.0 * 1024.0), g.totalMs(), g.name()));
                }
            }
            AtmMemOpt.LOGGER.info("{}", sb.toString().trim());
        }

        /** Failed reloads are reported but do not count as profiled, completed reloads. */
        public void logFailure(Throwable error) {
            AtmMemOpt.LOGGER.warn("[AtmMemOpt] Resource/Datapack Reload failed in {} ms: {}",
                    (System.nanoTime() - startNanos) / 1_000_000L, error);
        }
    }

    /** Immutable snapshot of one listener's largest observed heap-used delta. */
    public record GrowthSnapshot(String name, long deltaBytes, long totalMs) {
    }

    private static final class ListenerTiming {
        final String name;
        volatile long prepNanos;
        /** Atomic accumulation: several tasks may be submitted by one listener. */
        final LongAdder applyNanos = new LongAdder();
        final boolean audited;
        final LongAdder heapDeltaBytes = new LongAdder();

        ListenerTiming(String name, boolean audited) {
            this.name = name;
            this.audited = audited;
        }

        long prepMs() {
            return prepNanos / 1_000_000L;
        }

        long applyMs() {
            return applyNanos.sum() / 1_000_000L;
        }

        long totalMs() {
            return (prepNanos + applyNanos.sum()) / 1_000_000L;
        }
    }

    private static final class TimedReloadListener implements PreparableReloadListener {
        private final PreparableReloadListener delegate;
        private final ConcurrentLinkedQueue<ListenerTiming> sink;
        private final boolean audit;

        TimedReloadListener(PreparableReloadListener delegate, ConcurrentLinkedQueue<ListenerTiming> sink, boolean audit) {
            this.delegate = delegate;
            this.sink = sink;
            this.audit = audit;
        }

        @Override
        public CompletableFuture<Void> reload(
                PreparationBarrier barrier,
                ResourceManager resourceManager,
                ProfilerFiller preparationsProfiler,
                ProfilerFiller reloadProfiler,
                Executor backgroundExecutor,
                Executor gameExecutor
        ) {
            String label = delegate.getName() + " (" + delegate.getClass().getName() + ")";
            ListenerTiming timing = new ListenerTiming(label, audit);
            sink.add(timing);

            long prepStart = System.nanoTime();
            PreparationBarrier timedBarrier = new PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T backgroundResult) {
                    timing.prepNanos = System.nanoTime() - prepStart;
                    return barrier.wait(backgroundResult);
                }
            };

            Executor timedGameExecutor = task -> gameExecutor.execute(() -> {
                long applyStart = System.nanoTime();
                long heapBefore = timing.audited ? usedHeapBytes() : 0L;
                try {
                    task.run();
                } finally {
                    timing.applyNanos.add(System.nanoTime() - applyStart);
                    if (timing.audited) {
                        long delta = usedHeapBytes() - heapBefore;
                        timing.heapDeltaBytes.add(delta);
                        AUDIT_SAMPLES.increment();
                    }
                }
            });

            return delegate.reload(
                    timedBarrier,
                    resourceManager,
                    preparationsProfiler,
                    reloadProfiler,
                    backgroundExecutor,
                    timedGameExecutor
            );
        }

        @Override
        public String getName() {
            return delegate.getName();
        }
    }

    /** Heap used is an estimate: allocation not yet collected counts as growth. No GC is forced. */
    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
