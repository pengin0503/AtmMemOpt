package com.atmmemopt.opt.irons;

import com.atmmemopt.mixin.irons.SpellContainerAccessor;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 3: Interns immutable Iron's Spellbooks SpellData, SpellSlot, empty SpellSlot[],
 * and SpellContainer instances.
 *
 * NOTE: SpellData#equals intentionally ignores the 'locked' field in Iron's Spellbooks,
 * so our interning keys explicitly include (spell, spellLevel, locked).
 */
public final class SpellContainerOptHelper {
    private static final int MAX_STRONG_CACHE = 8192;

    private static final ConcurrentHashMap<SpellDataKey, SpellData> SPELL_DATA_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SpellSlotKey, SpellSlot> SPELL_SLOT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EmptyContainerKey, SpellContainer> EMPTY_CONTAINER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, SpellSlot[]> EMPTY_SLOT_ARRAYS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<ContainerKey, KeyedContainerRef> CONTAINER_CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<SpellContainer> CONTAINER_QUEUE = new ReferenceQueue<>();

    private static final LongAdder SPELL_DATA_HITS = new LongAdder();
    private static final LongAdder SPELL_SLOT_HITS = new LongAdder();
    private static final LongAdder CONTAINER_HITS = new LongAdder();
    private static final LongAdder GET_OR_CREATE_AVOIDED = new LongAdder();

    private SpellContainerOptHelper() {
    }

    public static SpellData internSpellData(SpellData data) {
        if (data == null) {
            return null;
        }
        AbstractSpell spell = data.getSpell();
        int level = data.getLevel();
        boolean locked = data.isLocked();
        if (data == SpellData.EMPTY) {
            return SpellData.EMPTY;
        }
        if (spell == SpellData.EMPTY.getSpell() && level == 0 && !locked) {
            SPELL_DATA_HITS.increment();
            return SpellData.EMPTY;
        }
        SpellDataKey key = new SpellDataKey(spell, level, locked);
        SpellData existing = SPELL_DATA_CACHE.get(key);
        if (existing != null) {
            SPELL_DATA_HITS.increment();
            return existing;
        }
        if (SPELL_DATA_CACHE.size() < MAX_STRONG_CACHE) {
            SpellData prev = SPELL_DATA_CACHE.putIfAbsent(key, data);
            if (prev != null) {
                SPELL_DATA_HITS.increment();
                return prev;
            }
        }
        return data;
    }

    public static SpellSlot internSpellSlot(SpellData spellData, int index) {
        SpellData internedData = internSpellData(spellData);
        SpellSlotKey key = new SpellSlotKey(
                internedData != null ? internedData.getSpell() : null,
                internedData != null ? internedData.getLevel() : 0,
                internedData != null && internedData.isLocked(),
                index
        );
        SpellSlot existing = SPELL_SLOT_CACHE.get(key);
        if (existing != null) {
            SPELL_SLOT_HITS.increment();
            return existing;
        }
        SpellSlot created = new SpellSlot(internedData, index);
        if (SPELL_SLOT_CACHE.size() < MAX_STRONG_CACHE) {
            SpellSlot prev = SPELL_SLOT_CACHE.putIfAbsent(key, created);
            if (prev != null) {
                SPELL_SLOT_HITS.increment();
                return prev;
            }
        }
        return created;
    }

    public static SpellSlot internSpellSlot(SpellSlot slot) {
        if (slot == null) {
            return null;
        }
        return internSpellSlot(slot.spellData(), slot.index());
    }

    public static SpellSlot[] getEmptySlotArray(int length) {
        if (length < 0) {
            return new SpellSlot[0];
        }
        return EMPTY_SLOT_ARRAYS.computeIfAbsent(length, SpellSlot[]::new);
    }

    public static SpellContainer getEmptyContainer(int maxSpells, boolean spellWheel, boolean mustEquip, boolean improved) {
        EmptyContainerKey key = new EmptyContainerKey(maxSpells, spellWheel, mustEquip, improved);
        SpellContainer existing = EMPTY_CONTAINER_CACHE.get(key);
        if (existing != null) {
            CONTAINER_HITS.increment();
            return existing;
        }
        SpellSlot[] sharedEmptySlots = getEmptySlotArray(maxSpells);
        SpellContainer created = new SpellContainer(maxSpells, spellWheel, mustEquip, improved, sharedEmptySlots);
        if (EMPTY_CONTAINER_CACHE.size() < MAX_STRONG_CACHE) {
            SpellContainer prev = EMPTY_CONTAINER_CACHE.putIfAbsent(key, created);
            if (prev != null) {
                CONTAINER_HITS.increment();
                return prev;
            }
        }
        return created;
    }

    public static void recordGetOrCreateAvoided() {
        GET_OR_CREATE_AVOIDED.increment();
    }

    public static ISpellContainer internContainer(ISpellContainer container) {
        return internContainerInternal(container, false);
    }

    /**
     * Used by SpellContainer.Mutable#toImmutable() where the slots array may still be
     * referenced by the Mutable instance; always copies non-empty arrays before caching.
     */
    public static ISpellContainer internContainerFromMutable(ISpellContainer container) {
        return internContainerInternal(container, true);
    }

    private static ISpellContainer internContainerInternal(ISpellContainer container, boolean defensiveCopy) {
        if (!(container instanceof SpellContainer sc)) {
            return container;
        }
        drainQueue();

        SpellContainerAccessor acc = (SpellContainerAccessor) sc;
        int maxSpells = acc.atmmemopt$getMaxSpells();
        boolean spellWheel = acc.atmmemopt$isSpellWheel();
        boolean mustEquip = acc.atmmemopt$isMustEquip();
        boolean improved = acc.atmmemopt$isImproved();
        SpellSlot[] rawSlots = acc.atmmemopt$getSlots();

        if (sc.isEmpty() && (rawSlots == null || rawSlots.length == maxSpells)) {
            return getEmptyContainer(maxSpells, spellWheel, mustEquip, improved);
        }

        int len = rawSlots != null ? rawSlots.length : 0;
        SpellSlot[] normalized = defensiveCopy ? new SpellSlot[len] : rawSlots;
        for (int i = 0; i < len; i++) {
            SpellSlot slot = rawSlots[i];
            normalized[i] = slot != null ? internSpellSlot(slot) : null;
        }

        ContainerKey key = new ContainerKey(maxSpells, spellWheel, mustEquip, improved, normalized);
        KeyedContainerRef fastRef = CONTAINER_CACHE.get(key);
        if (fastRef != null) {
            SpellContainer cached = fastRef.get();
            if (cached != null) {
                if (cached != sc) {
                    CONTAINER_HITS.increment();
                }
                return cached;
            }
        }

        final SpellContainer[] resultHolder = new SpellContainer[1];
        CONTAINER_CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                SpellContainer cached = existingRef.get();
                if (cached != null) {
                    if (cached != sc) {
                        CONTAINER_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            SpellContainer toCache = defensiveCopy
                    ? new SpellContainer(maxSpells, spellWheel, mustEquip, improved, normalized)
                    : sc;
            resultHolder[0] = toCache;
            return new KeyedContainerRef(k, toCache, CONTAINER_QUEUE);
        });
        return resultHolder[0];
    }

    private static void drainQueue() {
        Reference<? extends SpellContainer> ref;
        while ((ref = CONTAINER_QUEUE.poll()) != null) {
            KeyedContainerRef keyed = (KeyedContainerRef) ref;
            CONTAINER_CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getSpellDataHits() {
        return SPELL_DATA_HITS.sum();
    }

    public static long getSpellSlotHits() {
        return SPELL_SLOT_HITS.sum();
    }

    public static long getContainerHits() {
        return CONTAINER_HITS.sum();
    }

    public static long getGetOrCreateAvoided() {
        return GET_OR_CREATE_AVOIDED.sum();
    }

    private static final class KeyedContainerRef extends WeakReference<SpellContainer> {
        final ContainerKey key;

        KeyedContainerRef(ContainerKey key, SpellContainer referent, ReferenceQueue<SpellContainer> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private record SpellDataKey(AbstractSpell spell, int level, boolean locked) {
    }

    private record SpellSlotKey(AbstractSpell spell, int level, boolean locked, int index) {
    }

    private record EmptyContainerKey(int maxSpells, boolean spellWheel, boolean mustEquip, boolean improved) {
    }

    private static final class ContainerKey {
        final int maxSpells;
        final boolean spellWheel;
        final boolean mustEquip;
        final boolean improved;
        final SpellSlot[] slots;
        final int hash;

        ContainerKey(int maxSpells, boolean spellWheel, boolean mustEquip, boolean improved, SpellSlot[] slots) {
            this.maxSpells = maxSpells;
            this.spellWheel = spellWheel;
            this.mustEquip = mustEquip;
            this.improved = improved;
            this.slots = slots;

            int h = maxSpells;
            h = 31 * h + (spellWheel ? 1 : 0);
            h = 31 * h + (mustEquip ? 1 : 0);
            h = 31 * h + (improved ? 1 : 0);
            for (SpellSlot slot : slots) {
                if (slot == null) {
                    h = 31 * h;
                } else {
                    SpellData sd = slot.spellData();
                    h = 31 * h + Objects.hashCode(sd.getSpell());
                    h = 31 * h + sd.getLevel();
                    h = 31 * h + (sd.isLocked() ? 1 : 0);
                    h = 31 * h + slot.index();
                }
            }
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ContainerKey other)) return false;
            if (this.hash != other.hash
                    || this.maxSpells != other.maxSpells
                    || this.spellWheel != other.spellWheel
                    || this.mustEquip != other.mustEquip
                    || this.improved != other.improved
                    || this.slots.length != other.slots.length) {
                return false;
            }
            for (int i = 0; i < this.slots.length; i++) {
                SpellSlot a = this.slots[i];
                SpellSlot b = other.slots[i];
                if (a == b) continue;
                if (a == null || b == null) return false;
                if (a.index() != b.index()) return false;
                SpellData da = a.spellData();
                SpellData db = b.spellData();
                if (da.getLevel() != db.getLevel()
                        || da.isLocked() != db.isLocked()
                        || !Objects.equals(da.getSpell(), db.getSpell())) {
                    return false;
                }
            }
            return true;
        }
    }
}
