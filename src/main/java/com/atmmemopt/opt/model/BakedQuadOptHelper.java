package com.atmmemopt.opt.model;

import com.atmmemopt.config.AtmMemOptConfig;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 15: Deduplicates identical BakedQuad vertex int[] arrays (typically int[32] = 144 bytes)
 * within a bounded weak-reference cache.
 */
public final class BakedQuadOptHelper {
    private static final ConcurrentHashMap<VertexKey, KeyedVertexRef> CACHE = new ConcurrentHashMap<>(8192);
    private static final ReferenceQueue<int[]> QUEUE = new ReferenceQueue<>();

    private static final LongAdder VERTEX_HITS = new LongAdder();
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

        final int[][] resultHolder = new int[1][];
        CACHE.compute(key, (k, existingRef) -> {
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
            // Store a defensive copy as canonical array so caller-side post-construction mutations
            // to the original array cannot corrupt shared entries.
            int[] canonical = vertices.clone();
            resultHolder[0] = canonical;
            return new KeyedVertexRef(new VertexKey(canonical), canonical, QUEUE);
        });
        return resultHolder[0];
    }

    public static int clearCache() {
        int removed = CACHE.size();
        CACHE.clear();
        while (QUEUE.poll() != null) {
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

    public static long getVertexHits() {
        return VERTEX_HITS.sum();
    }

    public static long getEstimatedBytesSaved() {
        return ESTIMATED_BYTES_SAVED.sum();
    }

    public static int getCacheSize() {
        return CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
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
