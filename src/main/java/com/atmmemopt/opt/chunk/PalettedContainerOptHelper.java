package com.atmmemopt.opt.chunk;

import net.minecraft.util.ThreadingDetector;

import java.util.concurrent.atomic.LongAdder;

/**
 * Module 11: Replaces per-PalettedContainer ThreadingDetector allocations
 * (each owning a Semaphore + NonfairSync + ReentrantLock + NonfairSync) with a single
 * shared no-op detector.
 *
 * ThreadingDetectorMixin also short-circuits checkAndLock()/checkAndUnlock() if any mod
 * invokes them directly on the shared instance, preventing both NPEs and lock contention.
 */
public final class PalettedContainerOptHelper {
    private static final ThreadingDetector SHARED_NOOP_DETECTOR = new ThreadingDetector("AtmMemOpt-NoOp");
    private static final LongAdder DETECTORS_AVOIDED = new LongAdder();

    private PalettedContainerOptHelper() {
    }

    public static ThreadingDetector getSharedNoOpDetector() {
        DETECTORS_AVOIDED.increment();
        return SHARED_NOOP_DETECTOR;
    }

    public static boolean isSharedNoOp(ThreadingDetector detector) {
        return detector == SHARED_NOOP_DETECTOR;
    }

    public static long getDetectorsAvoided() {
        return DETECTORS_AVOIDED.sum();
    }
}
