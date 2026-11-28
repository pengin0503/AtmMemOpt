package com.atmmemopt.opt.patch;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.patch.DataComponentPatchAccessor;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 10: Interns immutable DataComponentPatch instances and enables sharing of their
 * backing Reference2ObjectMap (copy-on-write) across PatchedDataComponentMap instances.
 *
 * Safety guarantees:
 * - Bounded weak-reference cache with ReferenceQueue eviction prevents CHM bloat.
 * - Cleared automatically on ServerStoppedEvent / TagsUpdatedEvent / LoggingOut to prevent
 *   stale dynamic-registry Holder references across world reloads.
 * - Skips oversized patches (> 8 entries) and high-uniqueness / heavy container or NBT components
 *   (e.g. CUSTOM_DATA, CONTAINER, BLOCK_ENTITY_DATA) to avoid deep-hashing overhead and retaining
 *   large object graphs in cache keys.
 * - Guards hashCode/equals with try-catch in case a modded component throws unexpectedly.
 */
public final class DataComponentPatchOptHelper {
    private static final int MAX_ENTRIES_PER_PATCH = 8;

    private static final ConcurrentHashMap<Object, KeyedPatchRef> CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<DataComponentPatch> QUEUE = new ReferenceQueue<>();

    private static final LongAdder EMPTY_UNIFIED = new LongAdder();
    private static final LongAdder INTERN_HITS = new LongAdder();
    private static final LongAdder MAP_SHARES = new LongAdder();

    private static volatile int cachedMaxEntries = 65536;
    private static volatile boolean configLoaded = false;

    private DataComponentPatchOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.DATA_COMPONENT_PATCH_MAX_CACHE_ENTRIES.get();
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

    private static boolean isHeavyOrUniqueComponent(DataComponentType<?> type) {
        return type == DataComponents.CUSTOM_DATA
                || type == DataComponents.CONTAINER
                || type == DataComponents.BLOCK_ENTITY_DATA
                || type == DataComponents.ENTITY_DATA
                || type == DataComponents.BUNDLE_CONTENTS
                || type == DataComponents.WRITTEN_BOOK_CONTENT
                || type == DataComponents.WRITABLE_BOOK_CONTENT;
    }

    public static DataComponentPatch intern(DataComponentPatch patch) {
        if (patch == null) {
            return null;
        }
        if (patch == DataComponentPatch.EMPTY) {
            return DataComponentPatch.EMPTY;
        }
        if (patch.isEmpty()) {
            EMPTY_UNIFIED.increment();
            return DataComponentPatch.EMPTY;
        }

        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return patch;
        }

        Reference2ObjectMap<DataComponentType<?>, Optional<?>> map =
                ((DataComponentPatchAccessor) (Object) patch).atmmemopt$getMap();
        if (map == null || map.isEmpty()) {
            EMPTY_UNIFIED.increment();
            return DataComponentPatch.EMPTY;
        }

        int size = map.size();
        if (size > MAX_ENTRIES_PER_PATCH) {
            return patch;
        }

        drainQueue();

        Object key;
        try {
            if (size == 1) {
                Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> only =
                        Reference2ObjectMaps.fastIterable(map).iterator().next();
                if (isHeavyOrUniqueComponent(only.getKey())) {
                    return patch;
                }
                key = new SinglePatchKey(only.getKey(), only.getValue());
            } else {
                for (Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(map)) {
                    if (isHeavyOrUniqueComponent(entry.getKey())) {
                        return patch;
                    }
                }
                key = MultiPatchKey.from(map, size);
            }
        } catch (Throwable ignored) {
            // Defensive fallback if a modded component value throws in hashCode()
            return patch;
        }

        try {
            KeyedPatchRef fastRef = CACHE.get(key);
            if (fastRef != null) {
                DataComponentPatch cached = fastRef.get();
                if (cached != null) {
                    if (cached != patch) {
                        INTERN_HITS.increment();
                    }
                    return cached;
                }
            }

            if (CACHE.size() >= maxEntries) {
                return patch;
            }

            final DataComponentPatch[] resultHolder = new DataComponentPatch[1];
            CACHE.compute(key, (k, existingRef) -> {
                if (existingRef != null) {
                    DataComponentPatch cached = existingRef.get();
                    if (cached != null) {
                        if (cached != patch) {
                            INTERN_HITS.increment();
                        }
                        resultHolder[0] = cached;
                        return existingRef;
                    }
                }
                if (CACHE.size() >= maxEntries) {
                    resultHolder[0] = patch;
                    return null;
                }
                resultHolder[0] = patch;
                return new KeyedPatchRef(k, patch, QUEUE);
            });
            return resultHolder[0];
        } catch (Throwable ignored) {
            // Defensive fallback if a modded component value throws in equals()
            return patch;
        }
    }

    public static void recordMapShare() {
        MAP_SHARES.increment();
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
        Reference<? extends DataComponentPatch> ref;
        while ((ref = QUEUE.poll()) != null) {
            KeyedPatchRef keyed = (KeyedPatchRef) ref;
            CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getEmptyUnified() {
        return EMPTY_UNIFIED.sum();
    }

    public static long getInternHits() {
        return INTERN_HITS.sum();
    }

    public static long getMapShares() {
        return MAP_SHARES.sum();
    }

    public static int getCacheSize() {
        drainQueue();
        return CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    private static final class KeyedPatchRef extends WeakReference<DataComponentPatch> {
        final Object key;

        KeyedPatchRef(Object key, DataComponentPatch referent, ReferenceQueue<DataComponentPatch> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    /**
     * Zero-array fast-path key for 1-entry patches (e.g. damage, enchantments, custom_name, removals).
     */
    private static final class SinglePatchKey {
        final DataComponentType<?> type;
        final Optional<?> value;
        final int hash;

        SinglePatchKey(DataComponentType<?> type, Optional<?> value) {
            this.type = type;
            this.value = value;
            this.hash = System.identityHashCode(type) * 31 + Objects.hashCode(value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SinglePatchKey other)) return false;
            return this.hash == other.hash
                    && this.type == other.type
                    && Objects.equals(this.value, other.value);
        }
    }

    /**
     * Order-independent key for multi-entry DataComponentPatch instances.
     */
    private static final class MultiPatchKey {
        final DataComponentType<?>[] types;
        final Optional<?>[] values;
        final int hash;

        private MultiPatchKey(DataComponentType<?>[] types, Optional<?>[] values, int hash) {
            this.types = types;
            this.values = values;
            this.hash = hash;
        }

        static MultiPatchKey from(Reference2ObjectMap<DataComponentType<?>, Optional<?>> map, int size) {
            DataComponentType<?>[] types = new DataComponentType<?>[size];
            Optional<?>[] values = new Optional<?>[size];
            int idx = 0;
            int orderIndependentHash = size;
            for (Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(map)) {
                DataComponentType<?> t = entry.getKey();
                Optional<?> v = entry.getValue();
                types[idx] = t;
                values[idx] = v;
                idx++;
                orderIndependentHash += System.identityHashCode(t) * 31 + Objects.hashCode(v);
            }
            return new MultiPatchKey(types, values, orderIndependentHash);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MultiPatchKey other)) return false;
            int len = this.types.length;
            if (this.hash != other.hash || len != other.types.length) {
                return false;
            }
            for (int i = 0; i < len; i++) {
                DataComponentType<?> targetType = this.types[i];
                Optional<?> targetVal = this.values[i];
                boolean matched = false;
                for (int j = 0; j < len; j++) {
                    if (other.types[j] == targetType) {
                        matched = Objects.equals(targetVal, other.values[j]);
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            return true;
        }
    }
}
