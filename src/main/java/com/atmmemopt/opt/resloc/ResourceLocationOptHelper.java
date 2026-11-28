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
    private static final ConcurrentHashMap<String, String> NAMESPACE_CACHE = new ConcurrentHashMap<>(512);
    private static final ConcurrentHashMap<ResLocKey, KeyedResLocRef> CACHE = new ConcurrentHashMap<>(16384);
    private static final ReferenceQueue<ResourceLocation> QUEUE = new ReferenceQueue<>();

    private static final LongAdder NAMESPACE_HITS = new LongAdder();
    private static final LongAdder INSTANCE_HITS = new LongAdder();
    private static final LongAdder INSTANCE_EVICTIONS = new LongAdder();
    private static final LongAdder NAMESPACE_EVICTIONS = new LongAdder();

    /** Reusable lookup key so cache hits do not allocate a ResLocKey per call. */
    private static final ThreadLocal<LookupKey> LOOKUP_KEY = ThreadLocal.withInitial(LookupKey::new);

    private static volatile int cachedMaxEntries = 131072;
    private static volatile int cachedMaxNamespaceEntries = 4096;
    private static volatile boolean configLoaded = false;
    private static volatile boolean conservativeCache = false;

    static {
        NAMESPACE_CACHE.put(ResourceLocation.DEFAULT_NAMESPACE, ResourceLocation.DEFAULT_NAMESPACE);
        NAMESPACE_CACHE.put(ResourceLocation.REALMS_NAMESPACE, ResourceLocation.REALMS_NAMESPACE);
    }

    private ResourceLocationOptHelper() {
    }

    public static void refreshConfig() {
        try {
            conservativeCache = AtmMemOptConfig.RESOURCE_LOCATION_CONSERVATIVE_CACHE.get();
            int configured = AtmMemOptConfig.RESOURCE_LOCATION_MAX_CACHE_ENTRIES.get();
            // Conservative mode halves the effective instance-cache cap on every refresh.
            cachedMaxEntries = conservativeCache ? configured / 2 : configured;
            cachedMaxNamespaceEntries = AtmMemOptConfig.RESOURCE_LOCATION_NAMESPACE_MAX_ENTRIES.get();
            configLoaded = true;
            drainQueue();
            if (CACHE.size() > cachedMaxEntries) {
                INSTANCE_EVICTIONS.add(CACHE.size());
                CACHE.clear();
            }
            if (NAMESPACE_CACHE.size() > cachedMaxNamespaceEntries) {
                NAMESPACE_EVICTIONS.add(NAMESPACE_CACHE.size());
                NAMESPACE_CACHE.clear();
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

    private static int getMaxNamespaceEntries() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedMaxNamespaceEntries;
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
        if (NAMESPACE_CACHE.size() < getMaxNamespaceEntries()) {
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

        // Probe with a thread-local mutable key first; the ResLocKey + result holder are
        // only allocated on the miss/insert path.
        String namespace = rl.getNamespace();
        String path = rl.getPath();
        LookupKey lookupKey = LOOKUP_KEY.get().set(namespace, path);
        KeyedResLocRef fastRef;
        try {
            fastRef = CACHE.get(lookupKey);
        } finally {
            // Do not keep mod namespace/path strings alive in the thread-local between lookups.
            // The key is re-set() before each reuse, so clearing it here is always safe.
            lookupKey.clear();
        }
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

        ResLocKey key = new ResLocKey(namespace, path);
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
        int removed = CACHE.size() + NAMESPACE_CACHE.size();
        CACHE.clear();
        NAMESPACE_CACHE.clear();
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

    public static int getNamespaceCacheSize() {
        return NAMESPACE_CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    public static int getCachedMaxNamespaceEntries() {
        return getMaxNamespaceEntries();
    }

    public static long getInstanceEvictions() {
        return INSTANCE_EVICTIONS.sum();
    }

    public static long getNamespaceEvictions() {
        return NAMESPACE_EVICTIONS.sum();
    }

    private static final class KeyedResLocRef extends WeakReference<ResourceLocation> {
        final ResLocKey key;

        KeyedResLocRef(ResLocKey key, ResourceLocation referent, ReferenceQueue<ResourceLocation> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    /**
     * Common view of a cache key: the immutable stored key and the mutable lookup probe
     * compare through this type so equals() is symmetric in both directions.
     */
    private interface LocKey {
        String namespace();
        String path();
        int hash();
    }

    private static boolean keysEqual(LocKey self, Object obj) {
        if (self == obj) return true;
        if (!(obj instanceof LocKey other)) return false;
        return self.hash() == other.hash()
                && (self.namespace() == other.namespace() || self.namespace().equals(other.namespace()))
                && (self.path() == other.path() || self.path().equals(other.path()));
    }

    private static final class ResLocKey implements LocKey {
        final String namespace;
        final String path;
        final int hash;

        ResLocKey(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
            this.hash = 31 * namespace.hashCode() + path.hashCode();
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public int hash() {
            return hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return keysEqual(this, obj);
        }
    }

    /**
     * Mutable, thread-local CHM lookup key. Only ever used for get(); insertion always
     * allocates a fresh immutable ResLocKey so the map never sees a mutated key.
     */
    private static final class LookupKey implements LocKey {
        private String namespace;
        private String path;
        private int hash;

        LookupKey set(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
            this.hash = 31 * namespace.hashCode() + path.hashCode();
            return this;
        }

        void clear() {
            this.namespace = null;
            this.path = null;
            this.hash = 0;
        }

        @Override
        public String namespace() {
            return namespace;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public int hash() {
            return hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return keysEqual(this, obj);
        }
    }
}
