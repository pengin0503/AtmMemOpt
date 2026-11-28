package com.atmmemopt.opt.shapes;

import com.atmmemopt.config.AtmMemOptConfig;
import com.atmmemopt.mixin.shapes.ArrayVoxelShapeAccessor;
import com.atmmemopt.mixin.shapes.BitSetDiscreteVoxelShapeAccessor;
import com.atmmemopt.mixin.shapes.VoxelShapeAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
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
 * - Stage 1: the strong DOUBLE_LIST_CACHE is bounded/cleared, long coordinate lists are skipped,
 *   the discrete cache has its own cap and allocation-free probe, and immutable CubeVoxelShape
 *   instances are interned by bounds + grid resolution.
 */
public final class VoxelShapeOptHelper {
    private static final DoubleArrayList UNIT_0_1 = DoubleArrayList.wrap(new double[]{0.0D, 1.0D});
    private static final DoubleArrayList HALF_0_05 = DoubleArrayList.wrap(new double[]{0.0D, 0.5D});
    private static final DoubleArrayList HALF_05_1 = DoubleArrayList.wrap(new double[]{0.5D, 1.0D});
    private static final DoubleArrayList SPLIT_0_05_1 = DoubleArrayList.wrap(new double[]{0.0D, 0.5D, 1.0D});

    private static final long BITS_0 = Double.doubleToLongBits(0.0D);
    private static final long BITS_05 = Double.doubleToLongBits(0.5D);
    private static final long BITS_1 = Double.doubleToLongBits(1.0D);

    private static final ConcurrentHashMap<Object, DoubleArrayList> DOUBLE_LIST_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<DiscreteKey, KeyedDiscreteRef> DISCRETE_CACHE = new ConcurrentHashMap<>();
    // Cheap allocation-free probe: a shared weak ref for shapes with the same bounds/bit hash.
    private static final ConcurrentHashMap<Integer, KeyedDiscreteRef> DISCRETE_PROBE_CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<BitSetDiscreteVoxelShape> DISCRETE_QUEUE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<ShapeKey, KeyedShapeRef> SHAPE_CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<ArrayVoxelShape> SHAPE_QUEUE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<CubeKey, KeyedCubeRef> CUBE_CACHE = new ConcurrentHashMap<>();
    private static final ReferenceQueue<VoxelShape> CUBE_QUEUE = new ReferenceQueue<>();

    private static final LongAdder DOUBLE_LIST_HITS = new LongAdder();
    private static final LongAdder DISCRETE_HITS = new LongAdder();
    private static final LongAdder SHAPE_HITS = new LongAdder();
    private static final LongAdder SHAPE_CAP_REJECTIONS = new LongAdder();
    private static final LongAdder SHAPE_STALE_REFS = new LongAdder();
    private static final LongAdder SHAPE_DRAINED = new LongAdder();
    private static final LongAdder DOUBLE_LIST_CAP_REJECTIONS = new LongAdder();
    private static final LongAdder LONG_COORD_LIST_SKIPS = new LongAdder();
    private static final LongAdder DISCRETE_CAP_REJECTIONS = new LongAdder();
    private static final LongAdder DISCRETE_PROBE_HITS = new LongAdder();
    private static final LongAdder CUBE_HITS = new LongAdder();
    private static final LongAdder CUBE_SKIPPED = new LongAdder();
    private static final LongAdder EVICTIONS = new LongAdder();

    private static volatile int cachedMaxShapeEntries = 32768;
    private static volatile int cachedMaxDoubleListEntries = 16384;
    private static volatile int cachedCoordListMaxLength = 4096;
    private static volatile int cachedMaxDiscreteEntries = 16384;
    private static volatile boolean cubeInternEnabled = true;
    private static volatile boolean configLoaded = false;

    private VoxelShapeOptHelper() {
    }

    public static void refreshConfig() {
        try {
            cachedMaxShapeEntries = AtmMemOptConfig.VOXEL_SHAPE_MAX_CACHE_ENTRIES.get();
            cachedMaxDoubleListEntries = AtmMemOptConfig.VOXEL_SHAPE_DOUBLE_LIST_MAX_ENTRIES.get();
            cachedCoordListMaxLength = AtmMemOptConfig.VOXEL_SHAPE_COORD_LIST_MAX_LENGTH.get();
            cachedMaxDiscreteEntries = AtmMemOptConfig.DISCRETE_SHAPE_MAX_CACHE_ENTRIES.get();
            cubeInternEnabled = AtmMemOptConfig.CUBE_SHAPE_INTERN_ENABLED.get();
            configLoaded = true;
            evictOverCaps();
        } catch (IllegalStateException ignored) {
            // Config spec not yet loaded during very early bootstrap
        }
    }

    private static void evictOverCaps() {
        drainShapeQueue();
        drainDiscreteQueue();
        drainCubeQueue();
        if (DOUBLE_LIST_CACHE.size() > cachedMaxDoubleListEntries) {
            EVICTIONS.add(DOUBLE_LIST_CACHE.size());
            DOUBLE_LIST_CACHE.clear();
        }
        if (SHAPE_CACHE.size() > cachedMaxShapeEntries) {
            EVICTIONS.add(SHAPE_CACHE.size());
            SHAPE_CACHE.clear();
        }
        if (CUBE_CACHE.size() > cachedMaxShapeEntries) {
            EVICTIONS.add(CUBE_CACHE.size());
            CUBE_CACHE.clear();
        }
        if (DISCRETE_CACHE.size() > cachedMaxDiscreteEntries) {
            EVICTIONS.add(DISCRETE_CACHE.size());
            DISCRETE_CACHE.clear();
            DISCRETE_PROBE_CACHE.clear();
        }
    }

    private static int getMaxDoubleListEntries() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedMaxDoubleListEntries;
    }

    private static int getMaxDiscreteEntries() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedMaxDiscreteEntries;
    }

    private static boolean isCubeInternEnabled() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cubeInternEnabled;
    }

    public static DoubleList internDoubleList(DoubleList list) {
        if (!(list instanceof DoubleArrayList)) {
            return list;
        }
        int size = list.size();
        if (size > cachedCoordListMaxLength) {
            // Oversized modded coordinate lists must not be retained by the strong cache.
            LONG_COORD_LIST_SKIPS.increment();
            return list;
        }
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
        if (DOUBLE_LIST_CACHE.size() < getMaxDoubleListEntries()) {
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
        DOUBLE_LIST_CAP_REJECTIONS.increment();
        return list;
    }

    public static VoxelShape internShape(VoxelShape shape) {
        if (shape instanceof CubeVoxelShape cube) {
            if (!isCubeInternEnabled() || getMaxShapeCacheEntries() <= 0) {
                CUBE_SKIPPED.increment();
                return shape;
            }
            return internCube(cube);
        }
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
        ArrayVoxelShape fastCached = fastRef == null ? null : fastRef.get();
        // Effectively final so the compute lambda can consult it without a holder allocation:
        // a stale ref counted here is never counted again in the lambda (at most once per call).
        final boolean staleCountedOnFastPath = fastRef != null && fastCached == null;
        if (fastCached != null) {
            if (fastCached != avs) {
                SHAPE_HITS.increment();
            }
            return fastCached;
        }
        if (staleCountedOnFastPath) {
            SHAPE_STALE_REFS.increment();
        }

        // Enforce cache cap so one-off rare shapes do not bloat CHM nodes & keys
        if (SHAPE_CACHE.size() >= maxEntries) {
            SHAPE_CAP_REJECTIONS.increment();
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
                if (!staleCountedOnFastPath) {
                    SHAPE_STALE_REFS.increment();
                }
            }
            if (SHAPE_CACHE.size() >= maxEntries) {
                SHAPE_CAP_REJECTIONS.increment();
                resultHolder[0] = avs;
                return null;
            }
            resultHolder[0] = avs;
            return new KeyedShapeRef(k, avs, SHAPE_QUEUE);
        });
        return resultHolder[0];
    }

    /**
     * CubeVoxelShape is immutable; its six bounds plus discrete grid resolution fully
     * determine its collision behaviour. Grid sizes are part of the key so that a coarse
     * block() shape is never substituted for a finer cube with the same bounds.
     */
    private static VoxelShape internCube(CubeVoxelShape cube) {
        drainCubeQueue();
        int max = getMaxShapeCacheEntries();
        CubeKey key = cubeKey(cube);

        KeyedCubeRef fastRef = CUBE_CACHE.get(key);
        VoxelShape fastCached = fastRef == null ? null : fastRef.get();
        if (fastCached != null) {
            if (fastCached != cube) {
                CUBE_HITS.increment();
            }
            return fastCached;
        }
        if (CUBE_CACHE.size() >= max) {
            CUBE_SKIPPED.increment();
            return cube;
        }

        final VoxelShape[] resultHolder = new VoxelShape[1];
        CUBE_CACHE.compute(key, (k, existingRef) -> {
            if (existingRef != null) {
                VoxelShape cached = existingRef.get();
                if (cached != null) {
                    if (cached != cube) {
                        CUBE_HITS.increment();
                    }
                    resultHolder[0] = cached;
                    return existingRef;
                }
            }
            if (CUBE_CACHE.size() >= max) {
                CUBE_SKIPPED.increment();
                resultHolder[0] = cube;
                return null;
            }
            resultHolder[0] = cube;
            return new KeyedCubeRef(k, cube, CUBE_QUEUE);
        });
        return resultHolder[0];
    }

    private static CubeKey cubeKey(VoxelShape shape) {
        DiscreteVoxelShape dvs = ((VoxelShapeAccessor) shape).atmmemopt$getShape();
        return new CubeKey(
                Double.doubleToLongBits(shape.min(Direction.Axis.X)),
                Double.doubleToLongBits(shape.min(Direction.Axis.Y)),
                Double.doubleToLongBits(shape.min(Direction.Axis.Z)),
                Double.doubleToLongBits(shape.max(Direction.Axis.X)),
                Double.doubleToLongBits(shape.max(Direction.Axis.Y)),
                Double.doubleToLongBits(shape.max(Direction.Axis.Z)),
                dvs != null ? dvs.getXSize() : 0,
                dvs != null ? dvs.getYSize() : 0,
                dvs != null ? dvs.getZSize() : 0
        );
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
        int max = getMaxDiscreteEntries();
        if (max <= 0) {
            return dvs;
        }
        drainDiscreteQueue();

        // Cheapest guard first: no toLongArray()/DiscreteKey allocation when already at cap.
        if (DISCRETE_CACHE.size() >= max) {
            DISCRETE_CAP_REJECTIONS.increment();
            return dvs;
        }

        BitSetDiscreteVoxelShapeAccessor bsdAcc = (BitSetDiscreteVoxelShapeAccessor) (Object) bsd;
        BitSet storage = bsdAcc.atmmemopt$getStorage();
        int probe = discreteProbe(bsd, bsdAcc, storage);

        // Allocation-free probe: compare bounds and the existing BitSet directly, so a hit
        // never needs storage.toLongArray() plus a new DiscreteKey.
        KeyedDiscreteRef probeRef = DISCRETE_PROBE_CACHE.get(probe);
        if (probeRef != null) {
            BitSetDiscreteVoxelShape cached = probeRef.get();
            if (cached != null && probeShapeMatches(cached, bsd, bsdAcc, storage)) {
                if (cached != bsd) {
                    DISCRETE_HITS.increment();
                    DISCRETE_PROBE_HITS.increment();
                }
                return cached;
            }
        }

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

        final BitSetDiscreteVoxelShape[] resultHolder = new BitSetDiscreteVoxelShape[1];
        KeyedDiscreteRef stored = DISCRETE_CACHE.compute(key, (k, existingRef) -> {
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
            if (DISCRETE_CACHE.size() >= max) {
                DISCRETE_CAP_REJECTIONS.increment();
                resultHolder[0] = bsd;
                return null;
            }
            resultHolder[0] = bsd;
            return new KeyedDiscreteRef(k, bsd, DISCRETE_QUEUE, probe);
        });
        // Only replace a stale/missing probe entry; a live probe for a different shape
        // (hash collision) must keep pointing at its own exact-match cache ref.
        if (stored != null && stored.get() == bsd) {
            KeyedDiscreteRef current = DISCRETE_PROBE_CACHE.get(probe);
            if (current == null || current.get() == null) {
                DISCRETE_PROBE_CACHE.put(probe, stored);
            }
        }
        return resultHolder[0];
    }

    private static int discreteProbe(
            BitSetDiscreteVoxelShape bsd, BitSetDiscreteVoxelShapeAccessor acc, BitSet storage
    ) {
        int h = bsd.getXSize();
        h = 31 * h + bsd.getYSize();
        h = 31 * h + bsd.getZSize();
        h = 31 * h + acc.atmmemopt$getXMin();
        h = 31 * h + acc.atmmemopt$getYMin();
        h = 31 * h + acc.atmmemopt$getZMin();
        h = 31 * h + acc.atmmemopt$getXMax();
        h = 31 * h + acc.atmmemopt$getYMax();
        h = 31 * h + acc.atmmemopt$getZMax();
        return 31 * h + (storage != null ? storage.hashCode() : 0);
    }

    private static boolean probeShapeMatches(
            BitSetDiscreteVoxelShape cached, BitSetDiscreteVoxelShape bsd,
            BitSetDiscreteVoxelShapeAccessor acc, BitSet storage
    ) {
        BitSetDiscreteVoxelShapeAccessor cachedAcc = (BitSetDiscreteVoxelShapeAccessor) (Object) cached;
        if (cached.getXSize() != bsd.getXSize()
                || cached.getYSize() != bsd.getYSize()
                || cached.getZSize() != bsd.getZSize()
                || cachedAcc.atmmemopt$getXMin() != acc.atmmemopt$getXMin()
                || cachedAcc.atmmemopt$getYMin() != acc.atmmemopt$getYMin()
                || cachedAcc.atmmemopt$getZMin() != acc.atmmemopt$getZMin()
                || cachedAcc.atmmemopt$getXMax() != acc.atmmemopt$getXMax()
                || cachedAcc.atmmemopt$getYMax() != acc.atmmemopt$getYMax()
                || cachedAcc.atmmemopt$getZMax() != acc.atmmemopt$getZMax()) {
            return false;
        }
        BitSet other = cachedAcc.atmmemopt$getStorage();
        if (other == storage) {
            return true;
        }
        if (other == null) {
            return storage == null || storage.isEmpty();
        }
        if (storage == null) {
            return other.isEmpty();
        }
        return other.equals(storage);
    }

    public static int clearWeakCaches() {
        int removed = SHAPE_CACHE.size() + DISCRETE_CACHE.size() + DOUBLE_LIST_CACHE.size() + CUBE_CACHE.size();
        SHAPE_CACHE.clear();
        DISCRETE_CACHE.clear();
        DISCRETE_PROBE_CACHE.clear();
        DOUBLE_LIST_CACHE.clear();
        CUBE_CACHE.clear();
        while (SHAPE_QUEUE.poll() != null) {
            // drain
        }
        while (DISCRETE_QUEUE.poll() != null) {
            // drain
        }
        while (CUBE_QUEUE.poll() != null) {
            // drain
        }
        return removed;
    }

    private static void drainDiscreteQueue() {
        Reference<? extends BitSetDiscreteVoxelShape> ref;
        while ((ref = DISCRETE_QUEUE.poll()) != null) {
            KeyedDiscreteRef keyed = (KeyedDiscreteRef) ref;
            DISCRETE_CACHE.remove(keyed.key, keyed);
            DISCRETE_PROBE_CACHE.remove(keyed.probe, keyed);
        }
    }

    private static void drainCubeQueue() {
        Reference<? extends VoxelShape> ref;
        while ((ref = CUBE_QUEUE.poll()) != null) {
            KeyedCubeRef keyed = (KeyedCubeRef) ref;
            CUBE_CACHE.remove(keyed.key, keyed);
        }
    }

    private static void drainShapeQueue() {
        Reference<? extends ArrayVoxelShape> ref;
        while ((ref = SHAPE_QUEUE.poll()) != null) {
            KeyedShapeRef keyed = (KeyedShapeRef) ref;
            if (SHAPE_CACHE.remove(keyed.key, keyed)) {
                SHAPE_DRAINED.increment();
            }
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

    public static long getShapeCapRejections() {
        return SHAPE_CAP_REJECTIONS.sum();
    }

    public static long getShapeStaleRefs() {
        return SHAPE_STALE_REFS.sum();
    }

    public static long getShapeDrained() {
        return SHAPE_DRAINED.sum();
    }

    public static long getDoubleListCapRejections() {
        return DOUBLE_LIST_CAP_REJECTIONS.sum();
    }

    public static long getLongCoordListSkips() {
        return LONG_COORD_LIST_SKIPS.sum();
    }

    public static long getDiscreteCapRejections() {
        return DISCRETE_CAP_REJECTIONS.sum();
    }

    public static long getDiscreteProbeHits() {
        return DISCRETE_PROBE_HITS.sum();
    }

    public static long getCubeInternHits() {
        return CUBE_HITS.sum();
    }

    public static long getCubeSkipped() {
        return CUBE_SKIPPED.sum();
    }

    public static long getEvictions() {
        return EVICTIONS.sum();
    }

    public static int getShapeCacheSize() {
        drainShapeQueue();
        return SHAPE_CACHE.size();
    }

    public static int getDoubleListCacheSize() {
        return DOUBLE_LIST_CACHE.size();
    }

    public static int getDiscreteCacheSize() {
        drainDiscreteQueue();
        return DISCRETE_CACHE.size();
    }

    public static int getCubeCacheSize() {
        drainCubeQueue();
        return CUBE_CACHE.size();
    }

    public static int getCachedMaxDoubleListEntries() {
        return getMaxDoubleListEntries();
    }

    public static int getCachedMaxDiscreteEntries() {
        return getMaxDiscreteEntries();
    }

    public static int getCachedCoordListMaxLength() {
        if (!configLoaded) {
            refreshConfig();
        }
        return cachedCoordListMaxLength;
    }

    private static final class KeyedDiscreteRef extends WeakReference<BitSetDiscreteVoxelShape> {
        final DiscreteKey key;
        final int probe;

        KeyedDiscreteRef(
                DiscreteKey key, BitSetDiscreteVoxelShape referent,
                ReferenceQueue<BitSetDiscreteVoxelShape> queue, int probe
        ) {
            super(referent, queue);
            this.key = key;
            this.probe = probe;
        }
    }

    private static final class KeyedCubeRef extends WeakReference<VoxelShape> {
        final CubeKey key;

        KeyedCubeRef(CubeKey key, VoxelShape referent, ReferenceQueue<VoxelShape> queue) {
            super(referent, queue);
            this.key = key;
        }
    }

    private static final class CubeKey {
        final long minX, minY, minZ, maxX, maxY, maxZ;
        final int xSize, ySize, zSize;
        final int hash;

        CubeKey(
                long minX, long minY, long minZ, long maxX, long maxY, long maxZ,
                int xSize, int ySize, int zSize
        ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.xSize = xSize;
            this.ySize = ySize;
            this.zSize = zSize;
            int h = (int) (minX ^ (minX >>> 32));
            h = 31 * h + (int) (minY ^ (minY >>> 32));
            h = 31 * h + (int) (minZ ^ (minZ >>> 32));
            h = 31 * h + (int) (maxX ^ (maxX >>> 32));
            h = 31 * h + (int) (maxY ^ (maxY >>> 32));
            h = 31 * h + (int) (maxZ ^ (maxZ >>> 32));
            h = 31 * h + xSize;
            h = 31 * h + ySize;
            h = 31 * h + zSize;
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CubeKey other)) return false;
            return this.hash == other.hash
                    && this.minX == other.minX && this.minY == other.minY && this.minZ == other.minZ
                    && this.maxX == other.maxX && this.maxY == other.maxY && this.maxZ == other.maxZ
                    && this.xSize == other.xSize && this.ySize == other.ySize && this.zSize == other.zSize;
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
