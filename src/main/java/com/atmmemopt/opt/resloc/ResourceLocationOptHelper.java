package com.atmmemopt.opt.resloc;

import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.resources.ResourceLocation;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 12: Interns ResourceLocation namespace strings (zero-alloc fast path for "minecraft")
 * and deduplicates identical ResourceLocation instances within a bounded weak cache.
 */
public final class ResourceLocationOptHelper {
    private static final int MAX_NAMESPACE_CACHE = 4096;

    private static final ConcurrentHashMap<String, String> NAMESPACE_CACHE = new ConcurrentHashMap<>(512);
    private static final ConcurrentHashMap<ResLocKey, KeyedResLocRef> CACHE = new ConcurrentHashMap<>(16384);
    private static final ReferenceQueue<ResourceLocation> QUEUE = new ReferenceQueue<>();

    private static final LongAdder NAMESPACE_HITS = new LongAdder();
    private static final LongAdder INSTANCE_HITS = new LongAdder();

    private static volatile int cachedMaxEntries = 131072;
    private static volatile boolean configLoaded = false;

    static {
        NAMESPACE_CACHE.put(ResourceLocation.DEFAULT_NAMESPACE, ResourceLocation.DEFAULT_NAMESPACE);
        NAMESPACE_CACHE.put(ResourceLocation.REALMS_NAMESPACE, ResourceLocation.REALMS_NAMESPACE);
    }

    private ResourceLocationOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.RESOURCE_LOCATION_MAX_CACHE_ENTRIES.get();
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

    public static String internNamespace(String namespace) {
        if (namespace == null) {
            return null;
        }
        if (namespace == ResourceLocation.DEFAULT_NAMESPACE) {
            return ResourceLocation.DEFAULT_NAMESPACE;
        }
        if (ResourceLocation.DEFAULT_NAMESPACE.equals(namespace)) {
            NAMESPACE_HITS.increment();
            return ResourceLocation.DEFAULT_NAMESPACE;
        }
        String existing = NAMESPACE_CACHE.get(namespace);
        if (existing != null) {
            if (existing != namespace) {
                NAMESPACE_HITS.increment();
            }
            return existing;
        }
        if (NAMESPACE_CACHE.size() < MAX_NAMESPACE_CACHE) {
            String prev = NAMESPACE_CACHE.putIfAbsent(namespace, namespace);
            if (prev != null) {
                NAMESPACE_HITS.increment();
                return prev;
            }
        }
        return namespace;
    }

    public static ResourceLocation intern(ResourceLocation rl) {
        if (rl == null) {
            return null;
        }
        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return rl;
        }

        drainQueue();

        ResLocKey key = new ResLocKey(rl.getNamespace(), rl.getPath());
        KeyedResLocRef fastRef = CACHE.get(key);
        if (fastRef != null) {
            ResourceLocation cached = fastRef.get();
            if (cached != null) {
                if (cached != rl) {
                    INSTANCE_HITS.increment();
                }
                return cached;
            }
        }

        if (CACHE.size() >= maxEntries) {
            return rl;
        }

        final ResourceLocation[] resultHolder = new ResourceLocation[1];
        CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                ResourceLocation cached = existingRef.get();
                if (cached != null) {
                    if (cached != rl) {
                        INSTANCE_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (CACHE.size() >= maxEntries) {
                resultHolder[0] = rl;
                return null;
            }
            resultHolder[0] = rl;
            return new KeyedResLocRef(k, rl, QUEUE);
        });
        return resultHolder[0];
    }

    public static int clearWeakCache() {
        int removed = CACHE.size();
        CACHE.clear();
        while (QUEUE.poll() != null) {
            // drain
        }
        return removed;
    }

    private static void drainQueue() {
        Reference<? extends ResourceLocation> ref;
        while ((ref = QUEUE.poll()) != null) {
            KeyedResLocRef keyed = (KeyedResLocRef) ref;
            CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getNamespaceHits() {
        return NAMESPACE_HITS.sum();
    }

    public static long getInstanceHits() {
        return INSTANCE_HITS.sum();
    }

    public static int getCacheSize() {
        drainQueue();
        return CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    private static final class KeyedResLocRef extends WeakReference<ResourceLocation> {
        final ResLocKey key;

        KeyedResLocRef(ResLocKey key, ResourceLocation referent, ReferenceQueue<ResourceLocation> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private static final class ResLocKey {
        final String namespace;
        final String path;
        final int hash;

        ResLocKey(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
            this.hash = 31 * namespace.hashCode() + path.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ResLocKey other)) return false;
            return this.hash == other.hash
                    && (this.namespace == other.namespace || this.namespace.equals(other.namespace))
                    && (this.path == other.path || this.path.equals(other.path));
        }
    }
}
