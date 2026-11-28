package com.atmmemopt.opt.startup;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.startup.JeiElementPrefixParserInvoker;
import mezz.jei.common.search.PrefixInfo;
import mezz.jei.common.search.PrefixedSearchable;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementSearchIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Module 8a: Parallelizes JEI ElementSearch index building across CPU cores
 * and supports lazy (on-demand) building of the heavy TOOLTIPS index (15.9s -> ~1.0s).
 *
 * Thread-safety design:
 * - Main-thread-only indexes: TOOLTIPS, COLORS, CREATIVE_TABS.
 * - Background worker indexes: NAMES_AND_ALIASES, MOD_NAMES, TAGS, IDENTIFIERS.
 * - Any worker exception falls back cleanly to main-thread construction.
 */
public final class JeiFilterOptHelper {
    private static final AtomicLong LAST_BUILD_MS = new AtomicLong();
    private static final AtomicLong BUILD_COUNT = new AtomicLong();
    private static final AtomicLong LAZY_TOOLTIP_BUILDS = new AtomicLong();

    private JeiFilterOptHelper() {
    }

    private static boolean isMainThreadOnly(ElementSearchIndex idx) {
        return idx == ElementSearchIndex.TOOLTIPS
                || idx == ElementSearchIndex.COLORS
                || idx == ElementSearchIndex.CREATIVE_TABS;
    }

    /**
     * Builds all JEI search indexes. Returns the snapshot list if TOOLTIPS was deferred
     * for lazy on-demand construction, or null if TOOLTIPS was built or permanently skipped.
     */
    public static List<IListElementInfo<?>> buildAllIndexesParallel(
            JeiElementPrefixParserInvoker invoker,
            Collection<IListElementInfo<?>> elements,
            Map<PrefixInfo<IListElementInfo<?>, IListElement<?>>, PrefixedSearchable<IListElementInfo<?>, IListElement<?>>> targetMap,
            BiFunction<PrefixInfo<IListElementInfo<?>, IListElement<?>>, Collection<IListElementInfo<?>>, PrefixedSearchable<IListElementInfo<?>, IListElement<?>>> builderFn
    ) {
        long totalStart = System.nanoTime();
        ElementSearchIndex[] allIndexes = ElementSearchIndex.values();
        boolean skipTooltips = AtmMemOptConfig.JEI_SKIP_TOOLTIP_INDEX.get();
        boolean lazyTooltips = !skipTooltips && AtmMemOptConfig.JEI_LAZY_TOOLTIP_INDEX.get();

        List<IListElementInfo<?>> snapshot = new ArrayList<>(elements);
        // The caller passes an already instanceof-checked invoker, so no unchecked cast
        // to the invoker interface happens here (JEI version drift cannot CCE at runtime).

        int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "AtmMemOpt-JEI-Index");
            t.setDaemon(true);
            return t;
        });

        Map<ElementSearchIndex, CompletableFuture<IndexResult>> futures = new EnumMap<>(ElementSearchIndex.class);
        Map<ElementSearchIndex, PrefixInfo<IListElementInfo<?>, IListElement<?>>> submittedPrefixes = new EnumMap<>(ElementSearchIndex.class);
        Map<ElementSearchIndex, Long> timingsMs = new EnumMap<>(ElementSearchIndex.class);
        boolean aborted = false;

        try {
            for (ElementSearchIndex idx : allIndexes) {
                if (isMainThreadOnly(idx)) {
                    continue;
                }
                PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo = invoker.atmmemopt$getPrefixInfo(idx);
                submittedPrefixes.put(idx, prefixInfo);
                futures.put(idx, CompletableFuture.supplyAsync(() -> {
                    long s = System.nanoTime();
                    PrefixedSearchable<IListElementInfo<?>, IListElement<?>> searchable = builderFn.apply(prefixInfo, snapshot);
                    long elapsed = (System.nanoTime() - s) / 1_000_000L;
                    return new IndexResult(prefixInfo, searchable, elapsed);
                }, pool));
            }

            for (ElementSearchIndex idx : allIndexes) {
                if (!isMainThreadOnly(idx)) {
                    continue;
                }
                PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo = invoker.atmmemopt$getPrefixInfo(idx);
                long s = System.nanoTime();
                Collection<IListElementInfo<?>> input = (idx == ElementSearchIndex.TOOLTIPS && (skipTooltips || lazyTooltips))
                        ? List.of()
                        : snapshot;
                PrefixedSearchable<IListElementInfo<?>, IListElement<?>> searchable = builderFn.apply(prefixInfo, input);
                long elapsed = (System.nanoTime() - s) / 1_000_000L;
                targetMap.put(prefixInfo, searchable);
                timingsMs.put(idx, elapsed);
            }

            for (Map.Entry<ElementSearchIndex, CompletableFuture<IndexResult>> entry : futures.entrySet()) {
                ElementSearchIndex idx = entry.getKey();
                try {
                    IndexResult res = entry.getValue().join();
                    targetMap.put(res.prefixInfo(), res.searchable());
                    timingsMs.put(idx, res.elapsedMs());
                } catch (Throwable t) {
                    AtmMemOpt.LOGGER.warn("[AtmMemOpt] Parallel JEI index {} failed, falling back to main thread", idx, t);
                    PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo = submittedPrefixes.get(idx);
                    long s = System.nanoTime();
                    PrefixedSearchable<IListElementInfo<?>, IListElement<?>> fallback = builderFn.apply(prefixInfo, snapshot);
                    long elapsed = (System.nanoTime() - s) / 1_000_000L;
                    targetMap.put(prefixInfo, fallback);
                    timingsMs.put(idx, elapsed);
                }
            }
        } catch (RuntimeException | Error t) {
            // An exception escaping the build loop (e.g. from getPrefixInfo) used to leave
            // daemon workers running with discarded results. Drain them on the failure path.
            aborted = true;
            throw t;
        } finally {
            if (aborted) {
                pool.shutdownNow();
                try {
                    if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                        AtmMemOpt.LOGGER.warn("[AtmMemOpt] JEI index worker pool did not terminate within 2s after failure.");
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } else {
                pool.shutdown();
            }
        }

        long totalMs = (System.nanoTime() - totalStart) / 1_000_000L;
        LAST_BUILD_MS.set(totalMs);
        BUILD_COUNT.incrementAndGet();

        StringBuilder sb = new StringBuilder();
        sb.append("[AtmMemOpt] JEI parallel filter build finished in ").append(totalMs)
          .append(" ms (items=").append(snapshot.size())
          .append(", threads=").append(threads)
          .append(", lazyTooltips=").append(lazyTooltips)
          .append(", skipTooltips=").append(skipTooltips).append(") | per-index ms: ");
        for (ElementSearchIndex idx : allIndexes) {
            sb.append(idx.name()).append('=').append(timingsMs.getOrDefault(idx, -1L)).append("ms ");
        }
        AtmMemOpt.LOGGER.info("{}", sb.toString().trim());

        return lazyTooltips ? snapshot : null;
    }

    public static void recordLazyTooltipBuild(long elapsedMs, int itemCount) {
        LAZY_TOOLTIP_BUILDS.incrementAndGet();
        AtmMemOpt.LOGGER.info("[AtmMemOpt] Built deferred JEI TOOLTIPS index on-demand in {} ms (items={})", elapsedMs, itemCount);
    }

    public static long getLastBuildMs() {
        return LAST_BUILD_MS.get();
    }

    public static long getBuildCount() {
        return BUILD_COUNT.get();
    }

    public static long getLazyTooltipBuilds() {
        return LAZY_TOOLTIP_BUILDS.get();
    }

    private record IndexResult(
            PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo,
            PrefixedSearchable<IListElementInfo<?>, IListElement<?>> searchable,
            long elapsedMs
    ) {
    }
}
