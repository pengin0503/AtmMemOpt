package com.atmmemopt.opt.startup;

import com.atmmemopt.AtmMemOpt;
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
 */
public final class ReloadProfiler {
    private static final LongAdder JEISTUFF_LOOT_SKIPPED = new LongAdder();
    private static final AtomicLong RELOAD_PROFILES_LOGGED = new AtomicLong();

    private ReloadProfiler() {
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

    public static Session createSession(List<PreparableReloadListener> listeners) {
        ConcurrentLinkedQueue<ListenerTiming> timings = new ConcurrentLinkedQueue<>();
        List<PreparableReloadListener> wrapped = new ArrayList<>(listeners.size());
        for (PreparableReloadListener delegate : listeners) {
            wrapped.add(new TimedReloadListener(delegate, timings));
        }
        return new Session(wrapped, timings, System.nanoTime());
    }

    public static final class Session {
        public final List<PreparableReloadListener> wrappedListeners;
        private final ConcurrentLinkedQueue<ListenerTiming> timings;
        private final long startNanos;

        Session(List<PreparableReloadListener> wrappedListeners, ConcurrentLinkedQueue<ListenerTiming> timings, long startNanos) {
            this.wrappedListeners = wrappedListeners;
            this.timings = timings;
            this.startNanos = startNanos;
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
            AtmMemOpt.LOGGER.info("{}", sb.toString().trim());
        }

        /** Failed reloads are reported but do not count as profiled, completed reloads. */
        public void logFailure(Throwable error) {
            AtmMemOpt.LOGGER.warn("[AtmMemOpt] Resource/Datapack Reload failed in {} ms: {}",
                    (System.nanoTime() - startNanos) / 1_000_000L, error);
        }
    }

    private static final class ListenerTiming {
        final String name;
        volatile long prepNanos;
        /** Atomic accumulation: several tasks may be submitted by one listener. */
        final LongAdder applyNanos = new LongAdder();

        ListenerTiming(String name) {
            this.name = name;
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

        TimedReloadListener(PreparableReloadListener delegate, ConcurrentLinkedQueue<ListenerTiming> sink) {
            this.delegate = delegate;
            this.sink = sink;
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
            ListenerTiming timing = new ListenerTiming(label);
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
                try {
                    task.run();
                } finally {
                    timing.applyNanos.add(System.nanoTime() - applyStart);
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
}
