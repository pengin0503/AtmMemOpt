package com.atmmemopt.opt.tags;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.tags.TagEntryAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 16: Interns immutable TagEntry(id, tag, required) instances across datapack tags.
 */
public final class TagEntryOptHelper {
    private static final ConcurrentHashMap<TagEntryKey, KeyedTagEntryRef> CACHE = new ConcurrentHashMap<>(4096);
    private static final ReferenceQueue<TagEntry> QUEUE = new ReferenceQueue<>();
    private static final LongAdder INTERN_HITS = new LongAdder();
    private static final LongAdder EVICTIONS = new LongAdder();

    private static volatile int cachedMaxEntries = 65536;
    private static volatile boolean configLoaded = false;

    private TagEntryOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.TAG_ENTRY_MAX_CACHE_ENTRIES.get();
            configLoaded = true;
            drainQueue();
            if (CACHE.size() > cachedMaxEntries) {
                EVICTIONS.add(CACHE.size());
                CACHE.clear();
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

    public static TagEntry intern(TagEntry entry) {
        if (entry == null) {
            return null;
        }
        drainQueue();

        TagEntryAccessor acc = (TagEntryAccessor) entry;
        ResourceLocation id = acc.atmmemopt$getId();
        if (id == null) {
            return entry;
        }
        TagEntryKey key = new TagEntryKey(id, acc.atmmemopt$isTag(), acc.atmmemopt$isRequired());

        KeyedTagEntryRef fastRef = CACHE.get(key);
        if (fastRef != null) {
            TagEntry cached = fastRef.get();
            if (cached != null) {
                if (cached != entry) {
                    INTERN_HITS.increment();
                }
                return cached;
            }
        }

        if (CACHE.size() >= getMaxEntries()) {
            return entry;
        }

        final TagEntry[] resultHolder = new TagEntry[1];
        CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                TagEntry cached = existingRef.get();
                if (cached != null) {
                    if (cached != entry) {
                        INTERN_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (CACHE.size() >= getMaxEntries()) {
                resultHolder[0] = entry;
                return null;
            }
            resultHolder[0] = entry;
            return new KeyedTagEntryRef(k, entry, QUEUE);
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
        Reference<? extends TagEntry> ref;
        while ((ref = QUEUE.poll()) != null) {
            KeyedTagEntryRef keyed = (KeyedTagEntryRef) ref;
            CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getInternHits() {
        return INTERN_HITS.sum();
    }

    public static int getCacheSize() {
        drainQueue();
        return CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    public static long getEvictions() {
        return EVICTIONS.sum();
    }

    private record TagEntryKey(ResourceLocation id, boolean tag, boolean required) {
    }

    private static final class KeyedTagEntryRef extends WeakReference<TagEntry> {
        final TagEntryKey key;

        KeyedTagEntryRef(TagEntryKey key, TagEntry referent, ReferenceQueue<TagEntry> queue) {
            super(referent, queue);
            this.key = key;
        }
    }
}
