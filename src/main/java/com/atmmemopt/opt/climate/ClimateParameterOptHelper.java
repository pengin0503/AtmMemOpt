package com.atmmemopt.opt.climate;

import net.minecraft.world.level.biome.Climate;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 13: Interns immutable Biome Climate.Parameter(min, max) instances.
 */
public final class ClimateParameterOptHelper {
    private static final int MAX_CACHE_ENTRIES = 32768;

    private static final ConcurrentHashMap<ParamKey, KeyedParamRef> CACHE = new ConcurrentHashMap<>(2048);
    private static final ReferenceQueue<Climate.Parameter> QUEUE = new ReferenceQueue<>();
    private static final LongAdder INTERN_HITS = new LongAdder();

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

    public static int clearCache() {
        int removed = CACHE.size();
        CACHE.clear();
        while (QUEUE.poll() != null) {
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

    public static long getInternHits() {
        return INTERN_HITS.sum();
    }

    public static int getCacheSize() {
        return CACHE.size();
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
}
