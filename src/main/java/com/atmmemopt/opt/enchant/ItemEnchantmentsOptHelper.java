package com.atmmemopt.opt.enchant;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.enchant.ItemEnchantmentsAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 6: Interns immutable ItemEnchantments instances safely across dynamic registries.
 *
 * Phase 4 optimization:
 * - Dedicated SingleEnchantKey fast path for 1-enchantment items avoids allocating
 *   WeakReference[] and int[] arrays on the most frequent path.
 */
public final class ItemEnchantmentsOptHelper {
    private static volatile ItemEnchantments emptyNoTooltip;

    private static final ConcurrentHashMap<Object, KeyedEnchantRef> CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<ItemEnchantments> QUEUE = new ReferenceQueue<>();

    private static final LongAdder EMPTY_UNIFIED = new LongAdder();
    private static final LongAdder INTERN_HITS = new LongAdder();

    private static volatile int cachedMaxEntries = 32768;
    private static volatile boolean configLoaded = false;

    private ItemEnchantmentsOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxEntries = AtmMemOptConfig.ITEM_ENCHANTMENTS_MAX_CACHE_ENTRIES.get();
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

    public static ItemEnchantments intern(ItemEnchantments value) {
        if (value == null) {
            return null;
        }
        ItemEnchantmentsAccessor acc = (ItemEnchantmentsAccessor) value;
        boolean showInTooltip = acc.atmmemopt$isShowInTooltip();

        if (value.isEmpty()) {
            if (showInTooltip) {
                if (value != ItemEnchantments.EMPTY) {
                    EMPTY_UNIFIED.increment();
                }
                return ItemEnchantments.EMPTY;
            }
            ItemEnchantments cached = emptyNoTooltip;
            if (cached != null) {
                if (value != cached) {
                    EMPTY_UNIFIED.increment();
                }
                return cached;
            }
            Object2IntOpenHashMap<Holder<Enchantment>> map = acc.atmmemopt$getEnchantments();
            if (map != null) {
                map.trim();
            }
            emptyNoTooltip = value;
            return value;
        }

        int maxEntries = getMaxEntries();
        if (maxEntries <= 0) {
            return value;
        }

        Object2IntOpenHashMap<Holder<Enchantment>> map = acc.atmmemopt$getEnchantments();
        if (map == null) {
            return value;
        }

        drainQueue();

        Object key;
        if (map.size() == 1) {
            Object2IntMap.Entry<Holder<Enchantment>> only = map.object2IntEntrySet().iterator().next();
            key = new SingleEnchantKey(showInTooltip, only.getKey(), only.getIntValue());
        } else {
            key = MultiEnchantKey.from(map, showInTooltip);
        }

        KeyedEnchantRef fastRef = CACHE.get(key);
        if (fastRef != null) {
            ItemEnchantments cached = fastRef.get();
            if (cached != null) {
                if (cached != value) {
                    INTERN_HITS.increment();
                }
                return cached;
            }
        }

        if (CACHE.size() >= maxEntries) {
            return value;
        }

        final ItemEnchantments[] resultHolder = new ItemEnchantments[1];
        CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                ItemEnchantments cached = existingRef.get();
                if (cached != null) {
                    if (cached != value) {
                        INTERN_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (CACHE.size() >= maxEntries) {
                resultHolder[0] = value;
                return null;
            }
            map.trim();
            resultHolder[0] = value;
            return new KeyedEnchantRef(k, value, QUEUE);
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
        Reference<? extends ItemEnchantments> ref;
        while ((ref = QUEUE.poll()) != null) {
            KeyedEnchantRef keyed = (KeyedEnchantRef) ref;
            CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getEmptyUnified() {
        return EMPTY_UNIFIED.sum();
    }

    public static long getInternHits() {
        return INTERN_HITS.sum();
    }

    public static long getTotalHits() {
        return EMPTY_UNIFIED.sum() + INTERN_HITS.sum();
    }

    public static int getCacheSize() {
        drainQueue();
        return CACHE.size();
    }

    public static int getCachedMaxEntries() {
        return getMaxEntries();
    }

    private static final class KeyedEnchantRef extends WeakReference<ItemEnchantments> {
        final Object key;

        KeyedEnchantRef(Object key, ItemEnchantments referent, ReferenceQueue<ItemEnchantments> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    /**
     * Lightweight array-free key for the most common 1-enchantment case.
     */
    private static final class SingleEnchantKey {
        final boolean showInTooltip;
        final WeakReference<Holder<Enchantment>> holderRef;
        final int level;
        final int hash;

        SingleEnchantKey(boolean showInTooltip, Holder<Enchantment> holder, int level) {
            this.showInTooltip = showInTooltip;
            this.holderRef = new WeakReference<>(holder);
            this.level = level;
            this.hash = (showInTooltip ? 1231 : 1237) + System.identityHashCode(holder) * 31 + level;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SingleEnchantKey other)) return false;
            if (this.hash != other.hash
                    || this.showInTooltip != other.showInTooltip
                    || this.level != other.level) {
                return false;
            }
            Holder<Enchantment> h1 = this.holderRef.get();
            return h1 != null && h1 == other.holderRef.get();
        }
    }

    private static final class MultiEnchantKey {
        final boolean showInTooltip;
        final WeakReference<Holder<Enchantment>>[] holderRefs;
        final int[] levels;
        final int hash;

        private MultiEnchantKey(
                boolean showInTooltip,
                WeakReference<Holder<Enchantment>>[] holderRefs,
                int[] levels,
                int hash
        ) {
            this.showInTooltip = showInTooltip;
            this.holderRefs = holderRefs;
            this.levels = levels;
            this.hash = hash;
        }

        @SuppressWarnings("unchecked")
        static MultiEnchantKey from(Object2IntOpenHashMap<Holder<Enchantment>> map, boolean showInTooltip) {
            int size = map.size();
            WeakReference<Holder<Enchantment>>[] refs = new WeakReference[size];
            int[] levels = new int[size];
            int idx = 0;
            int orderIndependentHash = showInTooltip ? 1231 : 1237;

            for (Object2IntMap.Entry<Holder<Enchantment>> entry : map.object2IntEntrySet()) {
                Holder<Enchantment> h = entry.getKey();
                int lvl = entry.getIntValue();
                refs[idx] = new WeakReference<>(h);
                levels[idx] = lvl;
                idx++;
                orderIndependentHash += System.identityHashCode(h) * 31 + lvl;
            }

            return new MultiEnchantKey(showInTooltip, refs, levels, orderIndependentHash);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MultiEnchantKey other)) return false;
            if (this.hash != other.hash
                    || this.showInTooltip != other.showInTooltip
                    || this.holderRefs.length != other.holderRefs.length) {
                return false;
            }
            int len = this.holderRefs.length;
            for (int i = 0; i < len; i++) {
                Holder<Enchantment> target = this.holderRefs[i].get();
                if (target == null) {
                    return false;
                }
                int targetLvl = this.levels[i];
                boolean matched = false;
                for (int j = 0; j < len; j++) {
                    if (other.holderRefs[j].get() == target && other.levels[j] == targetLvl) {
                        matched = true;
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
