package com.atmmemopt.opt.font;

import com.atmmemopt.config.AtmMemOptConfig;
import com.mojang.blaze3d.platform.NativeImage;

import java.util.concurrent.atomic.LongAdder;

/**
 * Module 24: Font glyph NativeImage diagnostics ONLY.
 *
 * Vanilla 1.21.1 BitmapProvider keeps the loaded image in a field and in every Glyph record;
 * Glyph#upload() re-reads it on each font atlas bake and isColored() reads its format.
 * Closing it after the first upload would corrupt glyphs on F3+T / atlas rebuild, so this
 * module intentionally performs NO release: imagesClosed / estBytesFreed stay at zero even
 * when the (opt-in) toggle is enabled. The observed/closed counters show what vanilla owns.
 */
public final class FontNativeImageHelper {
    private static final LongAdder IMAGES_OBSERVED = new LongAdder();
    private static final LongAdder PROVIDER_CLOSES = new LongAdder();
    private static final LongAdder ESTIMATED_OBSERVED_BYTES = new LongAdder();

    /** Reserved for a future, proven single-use release path; never incremented in Stage 3. */
    private static final LongAdder IMAGES_CLOSED = new LongAdder();
    private static final LongAdder ESTIMATED_BYTES_FREED = new LongAdder();

    private static volatile boolean releaseRequested = false;
    private static volatile boolean configLoaded = false;

    private FontNativeImageHelper() {
    }

    public static void refreshConfig() {
        try {
            releaseRequested = AtmMemOptConfig.ENABLE_FONT_NATIVE_IMAGE_RELEASE.get();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
        }
    }

    private static boolean isReleaseRequested() {
        if (!configLoaded) {
            refreshConfig();
        }
        return releaseRequested;
    }

    public static void recordObserved(NativeImage image) {
        if (!isReleaseRequested() || image == null) {
            return;
        }
        IMAGES_OBSERVED.increment();
        try {
            int components = image.format() != null ? image.format().components() : 4;
            ESTIMATED_OBSERVED_BYTES.add((long) image.getWidth() * (long) image.getHeight() * (long) components);
        } catch (Throwable ignored) {
            // Diagnostic estimate only; never let it affect font loading.
        }
    }

    public static void recordProviderClose(NativeImage image) {
        if (!isReleaseRequested()) {
            return;
        }
        PROVIDER_CLOSES.increment();
    }

    public static void reset() {
        IMAGES_OBSERVED.reset();
        PROVIDER_CLOSES.reset();
        ESTIMATED_OBSERVED_BYTES.reset();
        IMAGES_CLOSED.reset();
        ESTIMATED_BYTES_FREED.reset();
    }

    public static long getImagesObserved() {
        return IMAGES_OBSERVED.sum();
    }

    public static long getProviderCloses() {
        return PROVIDER_CLOSES.sum();
    }

    public static long getEstimatedObservedBytes() {
        return ESTIMATED_OBSERVED_BYTES.sum();
    }

    public static long getImagesClosed() {
        return IMAGES_CLOSED.sum();
    }

    public static long getEstimatedBytesFreed() {
        return ESTIMATED_BYTES_FREED.sum();
    }
}
