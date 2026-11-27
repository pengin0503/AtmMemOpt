package com.atmmemopt.opt.model;

import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 15 & 17: Deduplicates identical BakedQuad vertex int[] arrays (typically int[32] = 144 bytes)
 * and interns identical immutable BakedQuad instances within bounded weak-reference caches.
 */
public final class BakedQuadOptHelper {
    private static final ConcurrentHashMap<VertexKey, KeyedVertexRef> CACHE = new ConcurrentHashMap<>(8192);
    private static final ReferenceQueue<int[]> QUEUE = new ReferenceQueue<>();

    private static final ConcurrentHashMap<QuadKey, KeyedQuadRef> QUAD_CACHE = new ConcurrentHashMap<>(8192);
    private static final ReferenceQueue<BakedQuad> QUAD_QUEUE = new ReferenceQueue<>();

    private static final LongAdder VERTEX_HITS = new LongAdder();
    private static final LongAdder QUAD_HITS = new LongAdder();
    private static final LongAdder ESTIMATED_BYTES_SAVED = new LongAdder();

    private static volatile int cachedMaxEntries = 65536;
    private static volatile boolean configLoaded = false;

    private BakedQuadOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.BAKED_QUAD_MAX_CACHE_ENTRIES.get();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
        }
    }

    private static int getMaxEntries() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedMaxEntries;
    }

    public static int[] internVertices(int[] vertices) {
        if (vertices == null || vertices.length == 0) {
            return vertices;
        }
        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return vertices;
        }

        drainQueue();

        VertexKey key = new VertexKey(vertices);
        KeyedVertexRef fastRef = CACHE.get(key);
        if (fastRef != null) {
            int[] cached = fastRef.get();
            if (cached != null) {
                if (cached != vertices) {
                    VERTEX_HITS.increment();
                    ESTIMATED_BYTES_SAVED.add(16L + (long) vertices.length * 4L);
                }
                return cached;
            }
        }

        if (CACHE.size() >= maxEntries) {
            return vertices;
        }

        // Clone before inserting so caller-side post-construction mutations to the original
        // array cannot corrupt either the map key or the cached canonical array.
        int[] canonical = vertices.clone();
        VertexKey canonicalKey = new VertexKey(canonical);
        final int[][] resultHolder = new int[1][];
        CACHE.compute(canonicalKey, (k, existingRef) -> {
            if (existingRef != null) {
                int[] cached = existingRef.get();
                if (cached != null) {
                    if (cached != vertices) {
                        VERTEX_HITS.increment();
                        ESTIMATED_BYTES_SAVED.add(16L + (long) vertices.length * 4L);
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (CACHE.size() >= maxEntries) {
                resultHolder[0] = vertices;
                return null;
            }
            resultHolder[0] = canonical;
            return new KeyedVertexRef(k, canonical, QUEUE);
        });
        return resultHolder[0];
    }

    public static BakedQuad internQuad(BakedQuad quad) {
        if (quad == null || quad.getClass() != BakedQuad.class) {
            return quad;
        }
        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return quad;
        }

        drainQuadQueue();

        QuadKey key = new QuadKey(
                quad.getVertices(),
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                quad.hasAmbientOcclusion()
        );
        KeyedQuadRef fastRef = QUAD_CACHE.get(key);
        if (fastRef != null) {
            BakedQuad cached = fastRef.get();
            if (cached != null) {
                if (cached != quad) {
                    QUAD_HITS.increment();
                    ESTIMATED_BYTES_SAVED.add(40L);
                }
                return cached;
            }
        }

        if (QUAD_CACHE.size() >= maxEntries) {
            return quad;
        }

        final BakedQuad[] resultHolder = new BakedQuad[1];
        QUAD_CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                BakedQuad cached = existingRef.get();
                if (cached != null) {
                    if (cached != quad) {
                        QUAD_HITS.increment();
                        ESTIMATED_BYTES_SAVED.add(40L);
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (QUAD_CACHE.size() >= maxEntries) {
                resultHolder[0] = quad;
                return null;
            }
            resultHolder[0] = quad;
            return new KeyedQuadRef(k, quad, QUAD_QUEUE);
        });
        return resultHolder[0];
    }

    public static int clearCache() {
        int removed = CACHE.size() + QUAD_CACHE.size();
        CACHE.clear();
        QUAD_CACHE.clear();
        while (QUEUE.poll() != null) {
            // drain
        }
        while (QUAD_QUEUE.poll() != null) {
            // drain
        }
        return removed;
    }

    private static void drainQueue() {
        Reference<? extends int[]> ref;
        while ((ref = QUEUE.poll()) != null) {
            KeyedVertexRef keyed = (KeyedVertexRef) ref;
            CACHE.remove(keyed.key, keyed);
        }
    }

    private static void drainQuadQueue() {
        Reference<? extends BakedQuad> ref;
        while ((ref = QUAD_QUEUE.poll()) != null) {
            KeyedQuadRef keyed = (KeyedQuadRef) ref;
            QUAD_CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getVertexHits() {
        return VERTEX_HITS.sum();
    }

    public static long getQuadHits() {
        return QUAD_HITS.sum();
    }

    public static long getEstimatedBytesSaved() {
        return ESTIMATED_BYTES_SAVED.sum();
    }

    public static int getCacheSize() {
        return CACHE.size();
    }

    public static int getQuadCacheSize() {
        return QUAD_CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    private static final class KeyedQuadRef extends WeakReference<BakedQuad> {
        final QuadKey key;

        KeyedQuadRef(QuadKey key, BakedQuad referent, ReferenceQueue<BakedQuad> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private static final class QuadKey {
        final int[] vertices;
        final int tintIndex;
        final Direction direction;
        final TextureAtlasSprite sprite;
        final boolean shade;
        final boolean hasAo;
        final int hash;

        QuadKey(int[] vertices, int tintIndex, Direction direction, TextureAtlasSprite sprite, boolean shade, boolean hasAo) {
            this.vertices = vertices;
            this.tintIndex = tintIndex;
            this.direction = direction;
            this.sprite = sprite;
            this.shade = shade;
            this.hasAo = hasAo;

            int h = Arrays.hashCode(vertices);
            h = 31 * h + tintIndex;
            h = 31 * h + (direction != null ? direction.ordinal() : -1);
            h = 31 * h + System.identityHashCode(sprite);
            h = 31 * h + (shade ? 1 : 0);
            h = 31 * h + (hasAo ? 1 : 0);
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof QuadKey other)) return false;
            return this.hash == other.hash
                    && this.tintIndex == other.tintIndex
                    && this.direction == other.direction
                    && this.sprite == other.sprite
                    && this.shade == other.shade
                    && this.hasAo == other.hasAo
                    && (this.vertices == other.vertices || Arrays.equals(this.vertices, other.vertices));
        }
    }

    private static final class KeyedVertexRef extends WeakReference<int[]> {
        final VertexKey key;

        KeyedVertexRef(VertexKey key, int[] referent, ReferenceQueue<int[]> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private static final class VertexKey {
        final int[] data;
        final int hash;

        VertexKey(int[] data) {
            this.data = data;
            this.hash = Arrays.hashCode(data);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof VertexKey other)) return false;
            return this.hash == other.hash && Arrays.equals(this.data, other.data);
        }
    }
}
