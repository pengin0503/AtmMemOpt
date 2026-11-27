package com.atmmemopt.opt.components;

import java.util.concurrent.atomic.LongAdder;

/**
 * Counters for Module 2 (PatchedDataComponentMap empty-patch normalization).
 */
public final class PatchedMapOptStats {
    private static final LongAdder SKIPPED_EMPTY_APPLY = new LongAdder();
    private static final LongAdder NORMALIZED_EMPTY_RESTORE = new LongAdder();
    private static final LongAdder NORMALIZED_EMPTY_COPY = new LongAdder();
    private static final LongAdder FAST_FROM_EMPTY_PATCH = new LongAdder();

    private PatchedMapOptStats() {
    }

    public static void recordSkippedEmptyApply() {
        SKIPPED_EMPTY_APPLY.increment();
    }

    public static void recordNormalizedEmptyRestore() {
        NORMALIZED_EMPTY_RESTORE.increment();
    }

    public static void recordNormalizedEmptyCopy() {
        NORMALIZED_EMPTY_COPY.increment();
    }

    public static void recordFastFromEmptyPatch() {
        FAST_FROM_EMPTY_PATCH.increment();
    }

    public static long getTotalHits() {
        return SKIPPED_EMPTY_APPLY.sum()
                + NORMALIZED_EMPTY_RESTORE.sum()
                + NORMALIZED_EMPTY_COPY.sum()
                + FAST_FROM_EMPTY_PATCH.sum();
    }

    public static long getSkippedEmptyApply() {
        return SKIPPED_EMPTY_APPLY.sum();
    }

    public static long getNormalizedEmptyRestore() {
        return NORMALIZED_EMPTY_RESTORE.sum();
    }

    public static long getFastFromEmptyPatch() {
        return FAST_FROM_EMPTY_PATCH.sum();
    }
}
