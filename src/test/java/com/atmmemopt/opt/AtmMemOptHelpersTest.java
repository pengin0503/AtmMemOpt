package com.atmmemopt.opt;

import com.atmmemopt.command.AtmMemOptServerCommands;
import com.atmmemopt.diag.NativeImageTracker;
import com.atmmemopt.diag.SpriteMemoryDiagnostics;
import com.atmmemopt.opt.chunk.PalettedContainerOptHelper;
import com.atmmemopt.opt.climate.ClimateParameterOptHelper;
import com.atmmemopt.opt.model.BakedQuadOptHelper;
import com.atmmemopt.opt.render.BerCullingHelper;
import com.atmmemopt.opt.render.EntityCullingHelper;
import com.atmmemopt.opt.render.UnfocusedFpsLimitHelper;
import com.atmmemopt.opt.resloc.ResourceLocationOptHelper;
import com.atmmemopt.opt.shapes.VoxelShapeOptHelper;
import com.atmmemopt.opt.tick.EntityMergeOptHelper;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class AtmMemOptHelpersTest {

    private static boolean awaitGcCondition(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            System.gc();
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25);
        }
        return condition.getAsBoolean();
    }

    @Test
    void testResourceLocationNamespaceAndInstanceInterning() {
        ResourceLocationOptHelper.clearWeakCache();

        String mcCopy = new String("minecraft");
        assertSame(ResourceLocation.DEFAULT_NAMESPACE, ResourceLocationOptHelper.internNamespace(mcCopy));

        String modNs1 = new String("mekanism_test_ns");
        String modNs2 = new String("mekanism_test_ns");
        String internedNs1 = ResourceLocationOptHelper.internNamespace(modNs1);
        String internedNs2 = ResourceLocationOptHelper.internNamespace(modNs2);
        assertSame(internedNs1, internedNs2);

        ResourceLocation a = ResourceLocation.fromNamespaceAndPath("testmod", "ingot_steel");
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath("testmod", "ingot_steel");
        ResourceLocation c = ResourceLocation.fromNamespaceAndPath("testmod", "ingot_copper");

        ResourceLocation ia = ResourceLocationOptHelper.intern(a);
        ResourceLocation ib = ResourceLocationOptHelper.intern(b);
        ResourceLocation ic = ResourceLocationOptHelper.intern(c);

        assertSame(ia, ib);
        assertNotSame(ia, ic);
        assertTrue(ResourceLocationOptHelper.getCacheSize() >= 2);
        assertTrue(ResourceLocationOptHelper.clearWeakCache() >= 2);
        assertEquals(0, ResourceLocationOptHelper.getCacheSize());
    }

    @Test
    void testResourceLocationInterningDedupsByNamespaceAndPath() {
        ResourceLocationOptHelper.clearWeakCache();

        ResourceLocation a = ResourceLocation.fromNamespaceAndPath(new String("dedup_mod"), new String("shared_path"));
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath(new String("dedup_mod"), new String("shared_path"));
        ResourceLocation other = ResourceLocation.fromNamespaceAndPath(new String("other_mod"), new String("shared_path"));

        assertSame(a, ResourceLocationOptHelper.intern(a));
        assertSame(ResourceLocationOptHelper.intern(a), ResourceLocationOptHelper.intern(b));
        assertNotSame(ResourceLocationOptHelper.intern(a), ResourceLocationOptHelper.intern(other));
        assertEquals(2, ResourceLocationOptHelper.getCacheSize());
        ResourceLocationOptHelper.clearWeakCache();
    }

    @Test
    void testResourceLocationConcurrentInterning() throws Exception {
        ResourceLocationOptHelper.clearWeakCache();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ResourceLocation>> tasks = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                tasks.add(() -> ResourceLocationOptHelper.intern(
                        ResourceLocation.fromNamespaceAndPath("concurrent_mod", "shared_path")
                ));
            }
            List<Future<ResourceLocation>> futures = pool.invokeAll(tasks);
            ResourceLocation first = futures.get(0).get();
            assertNotNull(first);
            for (Future<ResourceLocation> f : futures) {
                assertSame(first, f.get());
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void testClimateParameterAndArrayInterningAndGcEviction() throws Exception {
        ClimateParameterOptHelper.clearCache();

        Climate.Parameter p1 = ClimateParameterOptHelper.intern(new Climate.Parameter(-1000L, 1000L));
        Climate.Parameter p2 = ClimateParameterOptHelper.intern(new Climate.Parameter(-1000L, 1000L));
        Climate.Parameter p3 = ClimateParameterOptHelper.intern(new Climate.Parameter(-500L, 500L));

        assertSame(p1, p2);
        assertNotSame(p1, p3);

        Climate.Parameter[] arr1 = ClimateParameterOptHelper.internArray(new Climate.Parameter[]{
                new Climate.Parameter(-1000L, 1000L),
                new Climate.Parameter(-500L, 500L)
        });
        Climate.Parameter[] arr2 = ClimateParameterOptHelper.internArray(new Climate.Parameter[]{
                new Climate.Parameter(-1000L, 1000L),
                new Climate.Parameter(-500L, 500L)
        });
        assertSame(arr1, arr2);
        assertSame(p1, arr1[0]);
        assertSame(p3, arr1[1]);

        WeakReference<Climate.Parameter[]> weakArr = new WeakReference<>(arr1);
        arr1 = null;
        arr2 = null;
        assertTrue(
                awaitGcCondition(() -> weakArr.get() == null && ClimateParameterOptHelper.getArrayCacheSize() == 0),
                "Climate.Parameter[] weak cache entry should be reclaimed by GC (no strong key reference)"
        );

        assertTrue(ClimateParameterOptHelper.clearCache() >= 2);
    }

    @Test
    void testBakedQuadVertexDefensiveCopyQuadInterningAndGcEviction() throws Exception {
        BakedQuadOptHelper.clearCache();

        int[] raw1 = new int[]{1, 2, 3, 4, 5, 6, 7, 8};
        int[] raw2 = new int[]{1, 2, 3, 4, 5, 6, 7, 8};

        int[] canonical1 = BakedQuadOptHelper.internVertices(raw1);
        // Mutating caller's original array must NOT corrupt the cached canonical array
        raw1[0] = 9999;
        assertEquals(1, canonical1[0]);

        int[] canonical2 = BakedQuadOptHelper.internVertices(raw2);
        assertSame(canonical1, canonical2);

        BakedQuad q1 = new BakedQuad(canonical1, -1, Direction.UP, null, true, true);
        BakedQuad q2 = new BakedQuad(canonical2, -1, Direction.UP, null, true, true);
        BakedQuad qDiffDir = new BakedQuad(canonical2, -1, Direction.DOWN, null, true, true);
        BakedQuad qDiffAo = new BakedQuad(canonical2, -1, Direction.UP, null, true, false);

        BakedQuad iq1 = BakedQuadOptHelper.internQuad(q1);
        BakedQuad iq2 = BakedQuadOptHelper.internQuad(q2);
        assertSame(iq1, iq2);
        assertNotSame(iq1, BakedQuadOptHelper.internQuad(qDiffDir));
        assertNotSame(iq1, BakedQuadOptHelper.internQuad(qDiffAo));

        WeakReference<int[]> weakVerts = new WeakReference<>(canonical1);
        WeakReference<BakedQuad> weakQuad = new WeakReference<>(iq1);
        canonical1 = null;
        canonical2 = null;
        q1 = null;
        q2 = null;
        qDiffDir = null;
        qDiffAo = null;
        iq1 = null;
        iq2 = null;

        assertTrue(
                awaitGcCondition(() -> weakVerts.get() == null
                        && weakQuad.get() == null
                        && BakedQuadOptHelper.getCacheSize() == 0
                        && BakedQuadOptHelper.getQuadCacheSize() == 0),
                "BakedQuad vertex and quad weak cache entries should be reclaimed by GC"
        );
    }

    @Test
    void testVoxelShapeDoubleListInterning() {
        DoubleList u1 = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 1.0D}));
        DoubleList u2 = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 1.0D}));
        assertSame(u1, u2);

        DoubleList h1 = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 0.5D, 1.0D}));
        DoubleList h2 = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 0.5D, 1.0D}));
        assertSame(h1, h2);

        DoubleList custom4a = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 0.25D, 0.75D, 1.0D}));
        DoubleList custom4b = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 0.25D, 0.75D, 1.0D}));
        assertSame(custom4a, custom4b);

        DoubleList custom5a = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 0.1D, 0.2D, 0.3D, 1.0D}));
        DoubleList custom5b = VoxelShapeOptHelper.internDoubleList(new DoubleArrayList(new double[]{0.0D, 0.1D, 0.2D, 0.3D, 1.0D}));
        assertSame(custom5a, custom5b);
        assertNotSame(custom4a, custom5a);
        assertEquals(32768, VoxelShapeOptHelper.getCachedMaxShapeEntries());
    }

    @Test
    void testPalettedContainerSharedNoOpDetector() {
        ThreadingDetector d1 = PalettedContainerOptHelper.getSharedNoOpDetector();
        ThreadingDetector d2 = PalettedContainerOptHelper.getSharedNoOpDetector();
        ThreadingDetector other = new ThreadingDetector("Other");

        assertSame(d1, d2);
        assertTrue(PalettedContainerOptHelper.isSharedNoOp(d1));
        assertFalse(PalettedContainerOptHelper.isSharedNoOp(other));
        assertTrue(PalettedContainerOptHelper.getDetectorsAvoided() >= 2);
    }

    @Test
    void testEntityMergeDefaultsAndExplicitCounters() {
        long beforeItem = EntityMergeOptHelper.getItemScansExpanded();
        long beforeXp = EntityMergeOptHelper.getXpScansExpanded();

        double itemR = EntityMergeOptHelper.getItemMergeRadius();
        double xpR = EntityMergeOptHelper.getXpMergeRadius();
        assertEquals(1.5D, itemR, 1e-9);
        assertEquals(1.5D, xpR, 1e-9);
        // Reading the radius alone must not increment expanded-scan counters
        assertEquals(beforeItem, EntityMergeOptHelper.getItemScansExpanded());
        assertEquals(beforeXp, EntityMergeOptHelper.getXpScansExpanded());

        EntityMergeOptHelper.recordItemScanExpanded();
        EntityMergeOptHelper.recordXpScanExpanded();
        assertEquals(beforeItem + 1, EntityMergeOptHelper.getItemScansExpanded());
        assertEquals(beforeXp + 1, EntityMergeOptHelper.getXpScansExpanded());
    }

    @Test
    void testUnfocusedFpsLimitSafeDefaultBeforeConfigLoad() {
        assertEquals(15, UnfocusedFpsLimitHelper.getCachedLimit());
    }

    @Test
    void testSpriteMemoryDiagnosticsDescendingSort() {
        List<SpriteMemoryDiagnostics.AtlasSummary> list = new ArrayList<>(List.of(
                new SpriteMemoryDiagnostics.AtlasSummary("small", 10, 0, 10, 0, 100L),
                new SpriteMemoryDiagnostics.AtlasSummary("tie_fewer_sprites", 5, 0, 5, 0, 5000L),
                new SpriteMemoryDiagnostics.AtlasSummary("tie_more_sprites", 50, 1, 50, 0, 5000L),
                new SpriteMemoryDiagnostics.AtlasSummary("largest", 100, 2, 100, 0, 99999L)
        ));

        SpriteMemoryDiagnostics.sortSummaries(list);

        assertEquals("largest", list.get(0).atlasId());
        assertEquals("tie_more_sprites", list.get(1).atlasId());
        assertEquals("tie_fewer_sprites", list.get(2).atlasId());
        assertEquals("small", list.get(3).atlasId());
    }

    @Test
    void testNativeImageTrackerStackWalkerAndReset() {
        NativeImageTracker.reset();
        NativeImageTracker.markMixinApplied();

        String site1 = NativeImageTracker.recordAllocation();
        String site2 = NativeImageTracker.recordAllocation();
        assertNotNull(site1);
        assertNotNull(site2);
        NativeImageTracker.recordClose(site1);

        NativeImageTracker.Totals totals = NativeImageTracker.getTotals();
        assertEquals(2L, totals.created());
        assertEquals(1L, totals.closed());
        assertEquals(1L, totals.unclosed());
        assertFalse(NativeImageTracker.getTopUnclosed(5).isEmpty());

        NativeImageTracker.setRuntimeEnabled(false);
        assertNull(NativeImageTracker.recordAllocation());

        NativeImageTracker.reset();
        assertEquals(0L, NativeImageTracker.getTotals().created());
    }

    @Test
    void testBerAndEntityDiagResetClearsTotals() {
        BerCullingHelper.resetDiag();
        assertEquals(0L, BerCullingHelper.getCulledCount());
        assertEquals(0L, BerCullingHelper.getRenderedCount());

        EntityCullingHelper.resetDiag();
        assertEquals(0L, EntityCullingHelper.getCulledCount());
        assertEquals(0L, EntityCullingHelper.getRenderedCount());
    }

    @Test
    void testModuleStateFormattingDetectsPendingRestart() {
        // In unit test runtime MixinPlugin has not applied modules
        assertEquals("OFF", AtmMemOptServerCommands.formatModuleState("M1", false));
        assertEquals("OFF (on on restart)", AtmMemOptServerCommands.formatModuleState("M1", true));
    }
}
