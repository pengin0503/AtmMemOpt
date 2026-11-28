package com.atmmemopt.opt.texture;

import com.mojang.blaze3d.platform.NativeImage;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 14: Releases mipmap NativeImage instances (levels 1..N) for static (non-animated)
 * SpriteContents immediately after they have been uploaded to the GPU TextureAtlas.
 *
 * Safety guarantees:
 * - Never touches animated sprites (animatedTexture != null).
 * - Never closes originalImage (level 0), preserving isTransparent() and mod pixel reads.
 * - Shrinks byMipLevel to a 1-element array containing originalImage so SpriteContents#close()
 *   remains completely safe and GC can reclaim the closed mipmap NativeImage objects.
 */
public final class SpriteMipmapReleaseHelper {
    private static final LongAdder SPRITES_TRIMMED = new LongAdder();
    private static final LongAdder IMAGES_CLOSED = new LongAdder();
    private static final LongAdder ESTIMATED_BYTES_FREED = new LongAdder();
    private static final Field PIXELS_FIELD = resolvePixelsField();

    private SpriteMipmapReleaseHelper() {
    }

    private static Field resolvePixelsField() {
        try {
            Field f = NativeImage.class.getDeclaredField("pixels");
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isImageLive(NativeImage img) {
        if (PIXELS_FIELD == null) {
            return true;
        }
        try {
            return PIXELS_FIELD.getLong(img) != 0L;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static NativeImage[] releaseStaticMipmaps(NativeImage originalImage, NativeImage[] byMipLevel) {
        if (byMipLevel == null || byMipLevel.length <= 1) {
            return byMipLevel;
        }
        int closed = 0;
        long bytes = 0L;
        for (int i = 1; i < byMipLevel.length; i++) {
            NativeImage mip = byMipLevel[i];
            if (mip != null && mip != originalImage && isImageLive(mip)) {
                try {
                    int components = mip.format() != null ? mip.format().components() : 4;
                    bytes += (long) mip.getWidth() * (long) mip.getHeight() * (long) components;
                } catch (Throwable ignored) {
                }
                try {
                    mip.close();
                    closed++;
                } catch (Throwable ignored) {
                }
            }
        }
        if (closed > 0) {
            SPRITES_TRIMMED.increment();
            IMAGES_CLOSED.add(closed);
            ESTIMATED_BYTES_FREED.add(bytes);
        }
        return originalImage != null ? new NativeImage[]{originalImage} : new NativeImage[0];
    }

    public static long getSpritesTrimmed() {
        return SPRITES_TRIMMED.sum();
    }

    public static long getImagesClosed() {
        return IMAGES_CLOSED.sum();
    }

    public static long getEstimatedBytesFreed() {
        return ESTIMATED_BYTES_FREED.sum();
    }
}
