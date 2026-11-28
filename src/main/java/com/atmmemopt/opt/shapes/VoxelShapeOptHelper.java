package com.atmmemopt.opt.shapes;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.shapes.ArrayVoxelShapeAccessor;
import com.atmmemopt.mixin.shapes.BitSetDiscreteVoxelShapeAccessor;
import com.atmmemopt.mixin.shapes.VoxelShapeAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 7: Deduplicates immutable DoubleArrayList coordinate arrays inside ArrayVoxelShape
 * and interns identical ArrayVoxelShape instances within a bounded cache cap.
 *
 * Phase 4 memory & CPU optimizations:
 * - Zero-allocation fast paths for [0.0, 1.0], [0.0, 0.5], [0.5, 1.0], and [0.0, 0.5, 1.0].
 * - Primitive fixed-arity keys (2..4 elements) avoid allocating temporary long[] on hot paths.
 * - Bounded SHAPE_CACHE cap (default 32,768) prevents one-off rare shapes from bloating the map
 *   (saves ~50MB of ShapeKey / KeyedShapeRef / CHM$Node overhead).
 */
public final class VoxelShapeOptHelper {
    private static final int MAX_DOUBLE_LIST_CACHE = 16384;
    private static final int MAX_DISCRETE_CACHE = 16384;

    private static final DoubleArrayList UNIT_0_1 = DoubleArrayList.wrap(new double[]{0.0D, 1.0D});
    private static final DoubleArrayList HALF_0_05 = DoubleArrayList.wrap(new double[]{0.0D, 0.5D});
    private static final DoubleArrayList HALF_05_1 = DoubleArrayList.wrap(new double[]{0.5D, 1.0D});
    private static final DoubleArrayList SPLIT_0_05_1 = DoubleArrayList.wrap(new double[]{0.0D, 0.5D, 1.0D});

    private static final long BITS_0 = Double.doubleToLongBits(0.0D);
    private static final long BITS_05 = Double.doubleToLongBits(0.5D);
    private static final long BITS_1 = Double.doubleToLongBits(1.0D);

    private static final ConcurrentHashMap<Object, DoubleArrayList> DOUBLE_LIST_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<DiscreteKey, KeyedDiscreteRef> DISCRETE_CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<BitSetDiscreteVoxelShape> DISCRETE_QUEUE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<ShapeKey, KeyedShapeRef> SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<ArrayVoxelShape> SHAPE_QUEUE = new ReferenceQueue<>();

    private static final LongAdder DOUBLE_LIST_HITS = new LongAdder();
    private static final LongAdder DISCRETE_HITS = new LongAdder();
    private static final LongAdder SHAPE_HITS = new LongAdder();

    private static volatile int cachedMaxShapeEntries = 32768;
    private static volatile boolean configLoaded = false;

    private VoxelShapeOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxShapeEntries = AtmMemOptConfig.VOXEL_SHAPE_MAX_CACHE_ENTRIES.get();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
            // Config spec not yet loaded during very early bootstrap
        }
    }

    public static DoubleList internDoubleList(DoubleList list) {
        if (!(list instanceof DoubleArrayList)) {
            return list;
        }
        int size = list.size();
        if (size == 2) {
            long b0 = Double.doubleToLongBits(list.getDouble(0));
            long b1 = Double.doubleToLongBits(list.getDouble(1));
            if (b0 == BITS_0 && b1 == BITS_1) {
                if (list != UNIT_0_1) DOUBLE_LIST_HITS.increment();
                return UNIT_0_1;
            }
            if (b0 == BITS_0 && b1 == BITS_05) {
                if (list != HALF_0_05) DOUBLE_LIST_HITS.increment();
                return HALF_0_05;
            }
            if (b0 == BITS_05 && b1 == BITS_1) {
                if (list != HALF_05_1) DOUBLE_LIST_HITS.increment();
                return HALF_05_1;
            }
            return internWithKey(list, new Key2(b0, b1), size);
        }
        if (size == 3) {
            long b0 = Double.doubleToLongBits(list.getDouble(0));
            long b1 = Double.doubleToLongBits(list.getDouble(1));
            long b2 = Double.doubleToLongBits(list.getDouble(2));
            if (b0 == BITS_0 && b1 == BITS_05 && b2 == BITS_1) {
                if (list != SPLIT_0_05_1) DOUBLE_LIST_HITS.increment();
                return SPLIT_0_05_1;
            }
            return internWithKey(list, new Key3(b0, b1, b2), size);
        }
        if (size == 4) {
            long b0 = Double.doubleToLongBits(list.getDouble(0));
            long b1 = Double.doubleToLongBits(list.getDouble(1));
            long b2 = Double.doubleToLongBits(list.getDouble(2));
            long b3 = Double.doubleToLongBits(list.getDouble(3));
            return internWithKey(list, new Key4(b0, b1, b2, b3), size);
        }

        long[] bits = new long[size];
        for (int i = 0; i < size; i++) {
            bits[i] = Double.doubleToLongBits(list.getDouble(i));
        }
        return internWithKey(list, new ArrayKey(bits), size);
    }

    private static DoubleList internWithKey(DoubleList list, Object key, int size) {
        DoubleArrayList existing = DOUBLE_LIST_CACHE.get(key);
        if (existing != null) {
            if (existing != list) {
                DOUBLE_LIST_HITS.increment();
            }
            return existing;
        }
        if (DOUBLE_LIST_CACHE.size() < MAX_DOUBLE_LIST_CACHE) {
            double[] exact = new double[size];
            for (int i = 0; i < size; i++) {
                exact[i] = list.getDouble(i);
            }
            DoubleArrayList trimmed = DoubleArrayList.wrap(exact);
            DoubleArrayList prev = DOUBLE_LIST_CACHE.putIfAbsent(key, trimmed);
            if (prev != null) {
                DOUBLE_LIST_HITS.increment();
                return prev;
            }
            return trimmed;
        }
        return list;
    }

    public static VoxelShape internShape(VoxelShape shape) {
        if (!(shape instanceof ArrayVoxelShape avs)) {
            return shape;
        }
        int maxEntries = getMaxShapeCacheEntries();
        if (maxEntries <= 0) {
            return shape;
        }
        DiscreteVoxelShape dvs = ((VoxelShapeAccessor) avs).atmmemopt$getShape();
        if (!(dvs instanceof BitSetDiscreteVoxelShape bsd)) {
            return shape;
        }
        drainShapeQueue();

        ArrayVoxelShapeAccessor avsAcc = (ArrayVoxelShapeAccessor) avs;
        BitSetDiscreteVoxelShapeAccessor bsdAcc = (BitSetDiscreteVoxelShapeAccessor) (Object) bsd;

        BitSet storage = bsdAcc.atmmemopt$getStorage();
        long[] words = storage != null ? storage.toLongArray() : new long[0];

        ShapeKey key = new ShapeKey(
                avsAcc.atmmemopt$getXs(),
                avsAcc.atmmemopt$getYs(),
                avsAcc.atmmemopt$getZs(),
                dvs.getXSize(),
                dvs.getYSize(),
                dvs.getZSize(),
                bsdAcc.atmmemopt$getXMin(),
                bsdAcc.atmmemopt$getYMin(),
                bsdAcc.atmmemopt$getZMin(),
                bsdAcc.atmmemopt$getXMax(),
                bsdAcc.atmmemopt$getYMax(),
                bsdAcc.atmmemopt$getZMax(),
                words
        );

        KeyedShapeRef fastRef = SHAPE_CACHE.get(key);
        if (fastRef != null) {
            ArrayVoxelShape cached = fastRef.get();
            if (cached != null) {
                if (cached != avs) {
                    SHAPE_HITS.increment();
                }
                return cached;
            }
        }

        // Enforce cache cap so one-off rare shapes do not bloat CHM nodes & keys
        if (SHAPE_CACHE.size() >= maxEntries) {
            return avs;
        }

        final ArrayVoxelShape[] resultHolder = new ArrayVoxelShape[1];
        SHAPE_CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                ArrayVoxelShape cached = existingRef.get();
                if (cached != null) {
                    if (cached != avs) {
                        SHAPE_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (SHAPE_CACHE.size() >= maxEntries) {
                resultHolder[0] = avs;
                return null;
            }
            resultHolder[0] = avs;
            return new KeyedShapeRef(k, avs, SHAPE_QUEUE);
        });
        return resultHolder[0];
    }

    private static int getMaxShapeCacheEntries() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedMaxShapeEntries;
    }

    public static int getCachedMaxShapeEntries() {
        return getMaxShapeCacheEntries();
    }

    public static DiscreteVoxelShape internDiscreteShape(DiscreteVoxelShape dvs) {
        if (!(dvs instanceof BitSetDiscreteVoxelShape bsd)) {
            return dvs;
        }
        drainDiscreteQueue();

        BitSetDiscreteVoxelShapeAccessor bsdAcc = (BitSetDiscreteVoxelShapeAccessor) (Object) bsd;
        BitSet storage = bsdAcc.atmmemopt$getStorage();
        long[] words = storage != null ? storage.toLongArray() : new long[0];

        DiscreteKey key = new DiscreteKey(
                dvs.getXSize(), dvs.getYSize(), dvs.getZSize(),
                bsdAcc.atmmemopt$getXMin(), bsdAcc.atmmemopt$getYMin(), bsdAcc.atmmemopt$getZMin(),
                bsdAcc.atmmemopt$getXMax(), bsdAcc.atmmemopt$getYMax(), bsdAcc.atmmemopt$getZMax(),
                words
        );

        KeyedDiscreteRef fastRef = DISCRETE_CACHE.get(key);
        if (fastRef != null) {
            BitSetDiscreteVoxelShape cached = fastRef.get();
            if (cached != null) {
                if (cached != bsd) {
                    DISCRETE_HITS.increment();
                }
                return cached;
            }
        }

        if (DISCRETE_CACHE.size() >= MAX_DISCRETE_CACHE) {
            return bsd;
        }

        final BitSetDiscreteVoxelShape[] resultHolder = new BitSetDiscreteVoxelShape[1];
        DISCRETE_CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                BitSetDiscreteVoxelShape cached = existingRef.get();
                if (cached != null) {
                    if (cached != bsd) {
                        DISCRETE_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (DISCRETE_CACHE.size() >= MAX_DISCRETE_CACHE) {
                resultHolder[0] = bsd;
                return null;
            }
            resultHolder[0] = bsd;
            return new KeyedDiscreteRef(k, bsd, DISCRETE_QUEUE);
        });
        return resultHolder[0];
    }

    public static int clearWeakCaches() {
        int removed = SHAPE_CACHE.size() + DISCRETE_CACHE.size();
        SHAPE_CACHE.clear();
        DISCRETE_CACHE.clear();
        while (SHAPE_QUEUE.poll() != null) {
            // drain
        }
        while (DISCRETE_QUEUE.poll() != null) {
            // drain
        }
        return removed;
    }

    private static void drainDiscreteQueue() {
        Reference<? extends BitSetDiscreteVoxelShape> ref;
        while ((ref = DISCRETE_QUEUE.poll()) != null) {
            KeyedDiscreteRef keyed = (KeyedDiscreteRef) ref;
            DISCRETE_CACHE.remove(keyed.key, keyed);
        }
    }

    private static void drainShapeQueue() {
        Reference<? extends ArrayVoxelShape> ref;
        while ((ref = SHAPE_QUEUE.poll()) != null) {
            KeyedShapeRef keyed = (KeyedShapeRef) ref;
            SHAPE_CACHE.remove(keyed.key, keyed);
        }
    }

    public static long getDoubleListHits() {
        return DOUBLE_LIST_HITS.sum();
    }

    public static long getDiscreteHits() {
        return DISCRETE_HITS.sum();
    }

    public static long getShapeHits() {
        return SHAPE_HITS.sum();
    }

    public static int getShapeCacheSize() {
        drainShapeQueue();
        return SHAPE_CACHE.size();
    }

    private static final class KeyedDiscreteRef extends WeakReference<BitSetDiscreteVoxelShape> {
        final DiscreteKey key;

        KeyedDiscreteRef(DiscreteKey key, BitSetDiscreteVoxelShape referent, ReferenceQueue<BitSetDiscreteVoxelShape> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private static final class DiscreteKey {
        final int xSize, ySize, zSize;
        final int xMin, yMin, zMin, xMax, yMax, zMax;
        final long[] words;
        final int hash;

        DiscreteKey(
                int xSize, int ySize, int zSize,
                int xMin, int yMin, int zMin, int xMax, int yMax, int zMax,
                long[] words
        ) {
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
            this.xMin = xMin;
            this.yMin = yMin;
            this.zMin = zMin;
            this.xMax = xMax;
            this.yMax = yMax;
            this.zMax = zMax;
            this.words = words;

            int h = xSize;
            h = 31 * h + ySize;
            h = 31 * h + zSize;
            h = 31 * h + xMin;
            h = 31 * h + yMin;
            h = 31 * h + zMin;
            h = 31 * h + xMax;
            h = 31 * h + yMax;
            h = 31 * h + zMax;
            h = 31 * h + Arrays.hashCode(words);
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof DiscreteKey other)) return false;
            return this.hash == other.hash
                    && this.xSize == other.xSize
                    && this.ySize == other.ySize
                    && this.zSize == other.zSize
                    && this.xMin == other.xMin
                    && this.yMin == other.yMin
                    && this.zMin == other.zMin
                    && this.xMax == other.xMax
                    && this.yMax == other.yMax
                    && this.zMax == other.zMax
                    && Arrays.equals(this.words, other.words);
        }
    }

    private static final class KeyedShapeRef extends WeakReference<ArrayVoxelShape> {
        final ShapeKey key;

        KeyedShapeRef(ShapeKey key, ArrayVoxelShape referent, ReferenceQueue<ArrayVoxelShape> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private record Key2(long b0, long b1) {
    }

    private record Key3(long b0, long b1, long b2) {
    }

    private record Key4(long b0, long b1, long b2, long b3) {
    }

    private static final class ArrayKey {
        final long[] bits;
        final int hash;

        ArrayKey(long[] bits) {
            this.bits = bits;
            this.hash = Arrays.hashCode(bits);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ArrayKey other)) return false;
            return this.hash == other.hash && Arrays.equals(this.bits, other.bits);
        }
    }

    private static final class ShapeKey {
        final DoubleList xs;
        final DoubleList ys;
        final DoubleList zs;
        final int xSize, ySize, zSize;
        final int xMin, yMin, zMin, xMax, yMax, zMax;
        final long[] words;
        final int hash;

        ShapeKey(
                DoubleList xs, DoubleList ys, DoubleList zs,
                int xSize, int ySize, int zSize,
                int xMin, int yMin, int zMin, int xMax, int yMax, int zMax,
                long[] words
        ) {
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
            this.xMin = xMin;
            this.yMin = yMin;
            this.zMin = zMin;
            this.xMax = xMax;
            this.yMax = yMax;
            this.zMax = zMax;
            this.words = words;

            int h = xs.hashCode();
            h = 31 * h + ys.hashCode();
            h = 31 * h + zs.hashCode();
            h = 31 * h + xSize;
            h = 31 * h + ySize;
            h = 31 * h + zSize;
            h = 31 * h + xMin;
            h = 31 * h + yMin;
            h = 31 * h + zMin;
            h = 31 * h + xMax;
            h = 31 * h + yMax;
            h = 31 * h + zMax;
            h = 31 * h + Arrays.hashCode(words);
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ShapeKey other)) return false;
            return this.hash == other.hash
                    && this.xSize == other.xSize
                    && this.ySize == other.ySize
                    && this.zSize == other.zSize
                    && this.xMin == other.xMin
                    && this.yMin == other.yMin
                    && this.zMin == other.zMin
                    && this.xMax == other.xMax
                    && this.yMax == other.yMax
                    && this.zMax == other.zMax
                    && (this.xs == other.xs || this.xs.equals(other.xs))
                    && (this.ys == other.ys || this.ys.equals(other.ys))
                    && (this.zs == other.zs || this.zs.equals(other.zs))
                    && Arrays.equals(this.words, other.words);
        }
    }
}
