package com.atmmemopt.diag;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, zero-runtime-overhead diagnostics for TextureAtlas / SpriteContents
 * NativeImage retention (for /atmmemopt diag sprites).
 */
public final class SpriteMemoryDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("AtmMemOptSprites");

    private SpriteMemoryDiagnostics() {
    }

    @SuppressWarnings("unchecked")
    public static Report inspect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) {
            return new Report(List.of(), 0, 0, 0, 0, 0L);
        }

        List<AtlasSummary> summaries = new ArrayList<>();
        int totalSprites = 0;
        int totalAnimated = 0;
        int totalLiveImages = 0;
        int totalClosedImages = 0;
        long totalNativeBytes = 0L;

        try {
            TextureManager tm = mc.getTextureManager();
            Map<ResourceLocation, AbstractTexture> byPath =
                    (Map<ResourceLocation, AbstractTexture>) getField(tm, TextureManager.class, "byPath");
            if (byPath == null) {
                return new Report(List.of(), 0, 0, 0, 0, 0L);
            }

            Field texturesByNameField = findField(TextureAtlas.class, "texturesByName");
            Field originalImageField = findField(SpriteContents.class, "originalImage");
            Field byMipLevelField = findField(SpriteContents.class, "byMipLevel");
            Field animatedTextureField = findField(SpriteContents.class, "animatedTexture");
            Field pixelsField = findField(NativeImage.class, "pixels");

            for (Map.Entry<ResourceLocation, AbstractTexture> entry : byPath.entrySet()) {
                if (!(entry.getValue() instanceof TextureAtlas atlas)) {
                    continue;
                }
                Map<ResourceLocation, TextureAtlasSprite> sprites = texturesByNameField != null
                        ? (Map<ResourceLocation, TextureAtlasSprite>) texturesByNameField.get(atlas)
                        : null;
                if (sprites == null) {
                    continue;
                }

                int atlasSprites = 0;
                int atlasAnimated = 0;
                int atlasLive = 0;
                int atlasClosed = 0;
                long atlasBytes = 0L;

                IdentityHashMap<NativeImage, Boolean> seen = new IdentityHashMap<>();
                for (TextureAtlasSprite sprite : sprites.values()) {
                    if (sprite == null) continue;
                    SpriteContents contents = sprite.contents();
                    if (contents == null) continue;
                    atlasSprites++;

                    if (animatedTextureField != null && animatedTextureField.get(contents) != null) {
                        atlasAnimated++;
                    }

                    NativeImage orig = originalImageField != null ? (NativeImage) originalImageField.get(contents) : null;
                    if (orig != null && seen.put(orig, Boolean.TRUE) == null) {
                        long bytes = estimateNativeBytes(orig, pixelsField);
                        if (bytes > 0) {
                            atlasLive++;
                            atlasBytes += bytes;
                        } else {
                            atlasClosed++;
                        }
                    }

                    NativeImage[] mips = byMipLevelField != null ? (NativeImage[]) byMipLevelField.get(contents) : null;
                    if (mips != null) {
                        for (NativeImage mip : mips) {
                            if (mip != null && seen.put(mip, Boolean.TRUE) == null) {
                                long bytes = estimateNativeBytes(mip, pixelsField);
                                if (bytes > 0) {
                                    atlasLive++;
                                    atlasBytes += bytes;
                                } else {
                                    atlasClosed++;
                                }
                            }
                        }
                    }
                }

                summaries.add(new AtlasSummary(
                        entry.getKey().toString(),
                        atlasSprites,
                        atlasAnimated,
                        atlasLive,
                        atlasClosed,
                        atlasBytes
                ));
                totalSprites += atlasSprites;
                totalAnimated += atlasAnimated;
                totalLiveImages += atlasLive;
                totalClosedImages += atlasClosed;
                totalNativeBytes += atlasBytes;
            }
        } catch (Throwable t) {
            LOGGER.warn("[AtmMemOpt] Failed to inspect SpriteContents NativeImage usage", t);
        }

        sortSummaries(summaries);

        return new Report(summaries, totalSprites, totalAnimated, totalLiveImages, totalClosedImages, totalNativeBytes);
    }

    public static void sortSummaries(List<AtlasSummary> summaries) {
        summaries.sort(Comparator.comparingLong(AtlasSummary::nativeBytes).reversed()
                .thenComparing(Comparator.comparingInt(AtlasSummary::sprites).reversed()));
    }

    private static long estimateNativeBytes(NativeImage img, Field pixelsField) throws IllegalAccessException {
        if (pixelsField != null) {
            long ptr = pixelsField.getLong(img);
            if (ptr == 0L) {
                return 0L;
            }
        }
        int components = img.format() != null ? img.format().components() : 4;
        return (long) img.getWidth() * (long) img.getHeight() * (long) components;
    }

    private static Field findField(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getField(Object target, Class<?> cls, String name) {
        Field f = findField(cls, name);
        if (f == null) return null;
        try {
            return f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public record AtlasSummary(
            String atlasId,
            int sprites,
            int animated,
            int liveImages,
            int closedImages,
            long nativeBytes
    ) {
    }

    public record Report(
            List<AtlasSummary> atlases,
            int totalSprites,
            int totalAnimated,
            int totalLiveImages,
            int totalClosedImages,
            long totalNativeBytes
    ) {
    }
}
