package com.atmmemopt.opt.climate;

import net.minecraft.world.level.biome.Climate;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 13 & 18: Interns immutable Biome Climate.Parameter(min, max) instances and
 * Climate.Parameter[] parameter-space arrays used in Climate.RTree nodes.
 */
public final class ClimateParameterOptHelper {
    private static final int MAX_CACHE_ENTRIES = 32768;
    private static final int MAX_ARRAY_CACHE_ENTRIES = 32768;

    private static final ConcurrentHashMap<ParamKey, KeyedParamRef> CACHE = new ConcurrentHashMap<>(2048);
    private static final ReferenceQueue<Climate.Parameter> QUEUE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<WeakParamArrayRef, WeakParamArrayRef> ARRAY_CACHE = new ConcurrentHashMap<>(4096);
    private static final ReferenceQueue<Climate.Parameter[]> ARRAY_QUEUE = new ReferenceQueue<>();

    private static final LongAdder INTERN_HITS = new LongAdder();
    private static final LongAdder ARRAY_HITS = new LongAdder();

    private ClimateParameterOptHelper() {
    }

    public static Climate.Parameter intern(Climate.Parameter param) {
        if (param == null) {
            return null;
        }
        drainQueue();

        ParamKey key = new ParamKey(param.min(), param.max());
        KeyedParamRef fastRef = CACHE.get(key);
        if (fastRef != null) {
            Climate.Parameter cached = fastRef.get();
            if (cached != null) {
                if (cached != param) {
                    INTERN_HITS.increment();
                }
                return cached;
            }
        }

        if (CACHE.size() >= MAX_CACHE_ENTRIES) {
            return param;
        }

        final Climate.Parameter[] resultHolder = new Climate.Parameter[1];
        CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                Climate.Parameter cached = existingRef.get();
                if (cached != null) {
                    if (cached != param) {
                        INTERN_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (CACHE.size() >= MAX_CACHE_ENTRIES) {
                resultHolder[0] = param;
                return null;
            }
            resultHolder[0] = param;
            return new KeyedParamRef(k, param, QUEUE);
        });
        return resultHolder[0];
    }

    public static Climate.Parameter[] internArray(Climate.Parameter[] space) {
        if (space == null || space.length == 0) {
            return space;
        }
        drainArrayQueue();

        for (int i = 0; i < space.length; i++) {
            space[i] = intern(space[i]);
        }

        int hash = Arrays.hashCode(space);
        WeakParamArrayRef lookup = new WeakParamArrayRef(space, hash, null);
        WeakParamArrayRef fastRef = ARRAY_CACHE.get(lookup);
        if (fastRef != null) {
            Climate.Parameter[] cached = fastRef.get();
            if (cached != null) {
                if (cached != space) {
                    ARRAY_HITS.increment();
                }
                return cached;
            }
        }

        if (ARRAY_CACHE.size() >= MAX_ARRAY_CACHE_ENTRIES) {
            return space;
        }

        WeakParamArrayRef newRef = new WeakParamArrayRef(space, hash, ARRAY_QUEUE);
        final Climate.Parameter[][] resultHolder = new Climate.Parameter[1][];
        ARRAY_CACHE.compute(newRef, (k, existingRef) -> {
            if (existingRef != null) {
                Climate.Parameter[] cached = existingRef.get();
                if (cached != null) {
                    if (cached != space) {
                        ARRAY_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (ARRAY_CACHE.size() >= MAX_ARRAY_CACHE_ENTRIES) {
                resultHolder[0] = space;
                return null;
            }
            resultHolder[0] = space;
            return newRef;
        });
        return resultHolder[0];
    }

    public static int clearCache() {
        int removed = CACHE.size() + ARRAY_CACHE.size();
        CACHE.clear();
        ARRAY_CACHE.clear();
        while (QUEUE.poll() != null) {
            // drain
        }
        while (ARRAY_QUEUE.poll() != null) {
            // drain
        }
        return removed;
    }

    private static void drainQueue() {
        Reference<? extends Climate.Parameter> ref;
        while ((ref = QUEUE.poll()) != null) {
            KeyedParamRef keyed = (KeyedParamRef) ref;
            CACHE.remove(keyed.key, keyed);
        }
    }

    private static void drainArrayQueue() {
        Reference<? extends Climate.Parameter[]> ref;
        while ((ref = ARRAY_QUEUE.poll()) != null) {
            WeakParamArrayRef keyed = (WeakParamArrayRef) ref;
            ARRAY_CACHE.remove(keyed, keyed);
        }
    }

    public static long getInternHits() {
        return INTERN_HITS.sum();
    }

    public static long getArrayHits() {
        return ARRAY_HITS.sum();
    }

    public static int getCacheSize() {
        drainQueue();
        return CACHE.size();
    }

    public static int getArrayCacheSize() {
        drainArrayQueue();
        return ARRAY_CACHE.size();
    }

    private record ParamKey(long min, long max) {
    }

    private static final class KeyedParamRef extends WeakReference<Climate.Parameter> {
        final ParamKey key;

        KeyedParamRef(ParamKey key, Climate.Parameter referent, ReferenceQueue<Climate.Parameter> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    /**
     * Weak canonical key/value for Climate.Parameter[] arrays. Holds no strong reference
     * so GC can reclaim unreferenced arrays and enqueue cache eviction.
     */
    private static final class WeakParamArrayRef extends WeakReference<Climate.Parameter[]> {
        final int hash;

        WeakParamArrayRef(Climate.Parameter[] referent, int hash, ReferenceQueue<Climate.Parameter[]> queue) {
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
            if (!(obj instanceof WeakParamArrayRef other)) return false;
            if (this.hash != other.hash) return false;
            Climate.Parameter[] a = this.get();
            Climate.Parameter[] b = other.get();
            return a != null && b != null && (a == b || Arrays.equals(a, b));
        }
    }
}
