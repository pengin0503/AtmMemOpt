package com.atmmemopt.opt.keys;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 23: Interns TagKey / ResourceKey instances in two bounded weak caches.
 *
 * Keys are (canonical registry identity, canonical ResourceLocation) pairs. The registry
 * identity is a stable ResourceKey (for TagKey) or its registry name ResourceLocation
 * (for ResourceKey); dynamic-registry Holder objects are never used as keys. Both inner
 * ResourceLocations are canonicalized through M12. Mod subclasses are excluded via exact
 * class checks, and both caches are cleared on TagsUpdatedEvent / world-scope lifecycle
 * events. One shared cap ({@code tagKeyMaxCacheEntries}) applies to each cache.
 */
public final class KeyInternOptHelper {
    private static final ConcurrentHashMap<TagKeyCacheKey, KeyedTagKeyRef> TAG_CACHE = new ConcurrentHashMap<>(4096);
    private static final ConcurrentHashMap<ResourceKeyCacheKey, KeyedResourceKeyRef> RESOURCE_CACHE = new ConcurrentHashMap<>(4096);
    private static final ReferenceQueue<TagKey<?>> TAG_QUEUE = new ReferenceQueue<>();
    private static final ReferenceQueue<ResourceKey<?>> RESOURCE_QUEUE = new ReferenceQueue<>();

    private static final LongAdder TAG_HITS = new LongAdder();
    private static final LongAdder RESOURCE_HITS = new LongAdder();
    private static final LongAdder EVICTIONS = new LongAdder();

    private static volatile int cachedMaxEntries = 32768;
    private static volatile boolean configLoaded = false;

    private KeyInternOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.TAG_KEY_MAX_CACHE_ENTRIES.get();
            configLoaded = true;
            drainQueues();
            if (TAG_CACHE.size() > cachedMaxEntries) {
                EVICTIONS.add(TAG_CACHE.size());
                TAG_CACHE.clear();
            }
            if (RESOURCE_CACHE.size() > cachedMaxEntries) {
                EVICTIONS.add(RESOURCE_CACHE.size());
                RESOURCE_CACHE.clear();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private static int getMaxEntries() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedMaxEntries;
    }

    @SuppressWarnings("unchecked")
    public static <T> TagKey<T> internTagKey(TagKey<T> key) {
        if (key == null || key.getClass() != TagKey.class) {
            return key;
        }
        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return key;
        }
        ResourceKey<? extends Registry<T>> registry = key.registry();
        ResourceLocation location = ResourceLocationOptHelper.intern(key.location());
        if (registry == null || location == null) {
            return key;
        }
        drainQueues();

        ResourceKey<?> canonicalRegistry = internResourceKey(registry);
        TagKeyCacheKey cacheKey = new TagKeyCacheKey(canonicalRegistry, location);
        KeyedTagKeyRef fastRef = TAG_CACHE.get(cacheKey);
        if (fastRef != null) {
            TagKey<?> cached = fastRef.get();
            if (cached != null) {
                if (cached != key) {
                    TAG_HITS.increment();
                }
                return (TagKey<T>) cached;
            }
        }
        if (TAG_CACHE.size() >= maxEntries) {
            return key;
        }

        final TagKey<?>[] resultHolder = new TagKey<?>[1];
        TAG_CACHE.compute(cacheKey, (k, existingRef) -> {
            if (existingRef != null) {
                TagKey<?> cached = existingRef.get();
                if (cached != null) {
                    if (cached != key) {
                        TAG_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (TAG_CACHE.size() >= maxEntries) {
                resultHolder[0] = key;
                return null;
            }
            resultHolder[0] = key;
            return new KeyedTagKeyRef(k, key, TAG_QUEUE);
        });
        return (TagKey<T>) resultHolder[0];
    }

    @SuppressWarnings("unchecked")
    public static <T> ResourceKey<T> internResourceKey(ResourceKey<T> key) {
        if (key == null || key.getClass() != ResourceKey.class) {
            return key;
        }
        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return key;
        }
        drainQueues();

        ResourceLocation registryName = ResourceLocationOptHelper.intern(key.registry());
        ResourceLocation location = ResourceLocationOptHelper.intern(key.location());
        if (registryName == null || location == null) {
            return key;
        }
        ResourceKeyCacheKey cacheKey = new ResourceKeyCacheKey(registryName, location);
        KeyedResourceKeyRef fastRef = RESOURCE_CACHE.get(cacheKey);
        if (fastRef != null) {
            ResourceKey<?> cached = fastRef.get();
            if (cached != null) {
                if (cached != key) {
                    RESOURCE_HITS.increment();
                }
                return (ResourceKey<T>) cached;
            }
        }
        if (RESOURCE_CACHE.size() >= maxEntries) {
            return key;
        }

        final ResourceKey<?>[] resultHolder = new ResourceKey<?>[1];
        RESOURCE_CACHE.compute(cacheKey, (k, existingRef) -> {
            if (existingRef != null) {
                ResourceKey<?> cached = existingRef.get();
                if (cached != null) {
                    if (cached != key) {
                        RESOURCE_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (RESOURCE_CACHE.size() >= maxEntries) {
                resultHolder[0] = key;
                return null;
            }
            resultHolder[0] = key;
            return new KeyedResourceKeyRef(k, key, RESOURCE_QUEUE);
        });
        return (ResourceKey<T>) resultHolder[0];
    }

    public static int clearCache() {
        int removed = TAG_CACHE.size() + RESOURCE_CACHE.size();
        TAG_CACHE.clear();
        RESOURCE_CACHE.clear();
        while (TAG_QUEUE.poll() != null) {
            // drain
        }
        while (RESOURCE_QUEUE.poll() != null) {
            // drain
        }
        return removed;
    }

    private static void drainQueues() {
        Reference<? extends TagKey<?>> tagRef;
        while ((tagRef = TAG_QUEUE.poll()) != null) {
            KeyedTagKeyRef keyed = (KeyedTagKeyRef) tagRef;
            TAG_CACHE.remove(keyed.key, keyed);
        }
        Reference<? extends ResourceKey<?>> resourceRef;
        while ((resourceRef = RESOURCE_QUEUE.poll()) != null) {
            KeyedResourceKeyRef keyed = (KeyedResourceKeyRef) resourceRef;
            RESOURCE_CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getTagHits() {
        return TAG_HITS.sum();
    }

    public static long getResourceKeyHits() {
        return RESOURCE_HITS.sum();
    }

    public static long getEvictions() {
        return EVICTIONS.sum();
    }

    public static int getTagCacheSize() {
        drainQueues();
        return TAG_CACHE.size();
    }

    public static int getResourceKeyCacheSize() {
        drainQueues();
        return RESOURCE_CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    private record TagKeyCacheKey(ResourceKey<?> registry, ResourceLocation location) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TagKeyCacheKey other)) return false;
            // Identity semantics for the registry key (vanilla ResourceKey uses identity equals).
            return this.registry == other.registry && Objects.equals(this.location, other.location);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(registry) + location.hashCode();
        }
    }

    private record ResourceKeyCacheKey(ResourceLocation registryName, ResourceLocation location) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ResourceKeyCacheKey other)) return false;
            return Objects.equals(this.registryName, other.registryName)
                    && Objects.equals(this.location, other.location);
        }

        @Override
        public int hashCode() {
            return 31 * registryName.hashCode() + location.hashCode();
        }
    }

    private static final class KeyedTagKeyRef extends WeakReference<TagKey<?>> {
        final TagKeyCacheKey key;

        KeyedTagKeyRef(TagKeyCacheKey key, TagKey<?> referent, ReferenceQueue<TagKey<?>> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private static final class KeyedResourceKeyRef extends WeakReference<ResourceKey<?>> {
        final ResourceKeyCacheKey key;

        KeyedResourceKeyRef(ResourceKeyCacheKey key, ResourceKey<?> referent, ReferenceQueue<ResourceKey<?>> queue) {
            super(referent, queue);
            this.key = key;
        }
    }
}
