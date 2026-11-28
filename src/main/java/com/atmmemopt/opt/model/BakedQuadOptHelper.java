package com.atmmemopt.opt.model;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.model.BakedQuadAccessor;
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
 *
 * Vertex interning is only invoked from FaceBakery#bakeQuad RETURN, after NeoForge's
 * QuadTransformers.processInPlace mutations are complete. Interning from the BakedQuad
 * constructor was incorrect because the canonical array was mutated by those transformers.
 */
public final class BakedQuadOptHelper {
    private static final ConcurrentHashMap<WeakVertexRef, WeakVertexRef> CACHE = new ConcurrentHashMap<>(8192);
    private static final ReferenceQueue<int[]> QUEUE = new ReferenceQueue<>();

    private static final ConcurrentHashMap<WeakQuadRef, WeakQuadRef> QUAD_CACHE = new ConcurrentHashMap<>(8192);
    private static final ReferenceQueue<BakedQuad> QUAD_QUEUE = new ReferenceQueue<>();

    private static final LongAdder VERTEX_HITS = new LongAdder();
    private static final LongAdder QUAD_HITS = new LongAdder();
    private static final LongAdder ESTIMATED_BYTES_SAVED = new LongAdder();
    private static final LongAdder INTERNED_POST_BAKE = new LongAdder();
    private static final LongAdder EVICTIONS = new LongAdder();

    private static volatile int cachedMaxEntries = 65536;
    private static volatile boolean configLoaded = false;

    private BakedQuadOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.BAKED_QUAD_MAX_CACHE_ENTRIES.get();
            configLoaded = true;
            drainQueue();
            drainQuadQueue();
            // Lowered caps must take effect immediately, not only on the next insertion.
            if (CACHE.size() > cachedMaxEntries) {
                EVICTIONS.add(CACHE.size());
                CACHE.clear();
            }
            if (QUAD_CACHE.size() > cachedMaxEntries) {
                EVICTIONS.add(QUAD_CACHE.size());
                QUAD_CACHE.clear();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    /**
     * Called once per FaceBakery#bakeQuad return, after all in-place quad transformations.
     * The quad cache key uses the final vertex contents, so it is computed after interning.
     */
    public static BakedQuad internPostBake(BakedQuad quad) {
        if (quad == null || quad.getClass() != BakedQuad.class) {
            return quad;
        }
        if (getMaxEntries() > 0 && quad instanceof BakedQuadAccessor acc) {
            int[] finalVertices = acc.atmmemopt$getVertices();
            int[] canonical = internVertices(finalVertices);
            if (canonical != finalVertices) {
                acc.atmmemopt$setVertices(canonical);
            }
            INTERNED_POST_BAKE.increment();
        }
        return internQuad(quad);
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

        int hash = Arrays.hashCode(vertices);
        WeakVertexRef lookup = new WeakVertexRef(vertices, hash, null);
        WeakVertexRef fastRef = CACHE.get(lookup);
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

        // Clone before inserting: the cache key and canonical array never alias the caller's
        // array, so any later mutation by a non-FaceBakery caller cannot corrupt the cache.
        int[] canonical = vertices.clone();
        WeakVertexRef canonicalRef = new WeakVertexRef(canonical, hash, QUEUE);
        final int[][] resultHolder = new int[1][];
        CACHE.compute(canonicalRef, (k, existingRef) -> {
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
            return canonicalRef;
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

        int hash = hashQuad(quad);
        WeakQuadRef lookup = new WeakQuadRef(quad, hash, null);
        WeakQuadRef fastRef = QUAD_CACHE.get(lookup);
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

        WeakQuadRef newRef = new WeakQuadRef(quad, hash, QUAD_QUEUE);
        final BakedQuad[] resultHolder = new BakedQuad[1];
        QUAD_CACHE.compute(newRef, (k, existingRef) -> {
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
            return newRef;
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
            WeakVertexRef keyed = (WeakVertexRef) ref;
            CACHE.remove(keyed, keyed);
        }
    }

    private static void drainQuadQueue() {
        Reference<? extends BakedQuad> ref;
        while ((ref = QUAD_QUEUE.poll()) != null) {
            WeakQuadRef keyed = (WeakQuadRef) ref;
            QUAD_CACHE.remove(keyed, keyed);
        }
    }

    private static int hashQuad(BakedQuad quad) {
        int h = Arrays.hashCode(quad.getVertices());
        h = 31 * h + quad.getTintIndex();
        Direction dir = quad.getDirection();
        h = 31 * h + (dir != null ? dir.ordinal() : -1);
        h = 31 * h + System.identityHashCode(quad.getSprite());
        h = 31 * h + (quad.isShade() ? 1 : 0);
        h = 31 * h + (quad.hasAmbientOcclusion() ? 1 : 0);
        return h;
    }

    private static boolean quadsEqual(BakedQuad a, BakedQuad b) {
        if (a == b) {
            return true;
        }
        if (a.getTintIndex() != b.getTintIndex()
                || a.getDirection() != b.getDirection()
                || a.getSprite() != b.getSprite()
                || a.isShade() != b.isShade()
                || a.hasAmbientOcclusion() != b.hasAmbientOcclusion()) {
            return false;
        }
        int[] va = a.getVertices();
        int[] vb = b.getVertices();
        return va == vb || Arrays.equals(va, vb);
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

    public static long getInternedPostBake() {
        return INTERNED_POST_BAKE.sum();
    }

    public static long getEvictions() {
        return EVICTIONS.sum();
    }

    public static int getCacheSize() {
        drainQueue();
        return CACHE.size();
    }

    public static int getQuadCacheSize() {
        drainQuadQueue();
        return QUAD_CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    /**
     * Weak canonical key/value for BakedQuad instances. Holds no strong references to
     * vertices or TextureAtlasSprite so resource reloads can reclaim all cached quads.
     */
    private static final class WeakQuadRef extends WeakReference<BakedQuad> {
        final int hash;

        WeakQuadRef(BakedQuad referent, int hash, ReferenceQueue<BakedQuad> queue) {
            super(referent, queue);
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WeakQuadRef other)) return false;
            if (this.hash != other.hash) return false;
            BakedQuad a = this.get();
            BakedQuad b = other.get();
            return a != null && b != null && quadsEqual(a, b);
        }
    }

    /**
     * Weak canonical key/value for int[] vertex arrays. Holds no strong reference to
     * the array so GC can reclaim unreferenced vertex arrays and enqueue removal.
     */
    private static final class WeakVertexRef extends WeakReference<int[]> {
        final int hash;

        WeakVertexRef(int[] referent, int hash, ReferenceQueue<int[]> queue) {
            super(referent, queue);
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof WeakVertexRef other)) return false;
            if (this.hash != other.hash) return false;
            int[] a = this.get();
            int[] b = other.get();
            return a != null && b != null && (a == b || Arrays.equals(a, b));
        }
    }
}
