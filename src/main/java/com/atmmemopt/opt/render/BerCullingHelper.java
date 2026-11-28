package com.atmmemopt.opt.render;

import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 9a: distance culling for BlockEntityRenderer dispatch.
 *
 * Phase 5 improvements:
 * - Caches config distance & whitelist patterns and refreshes on ModConfigEvent.Reloading.
 * - Tracks per-BlockEntity-class rendered vs culled counts for /atmmemopt diag ber.
 *
 * The culling arithmetic itself performs no allocations (field reads + primitive math),
 * but the exemption checks below may call renderer-provided methods; whitelisted
 * renderers are therefore skipped first and the bounding-box check runs only for
 * block entities that are actually beyond the culling distance.
 *
 * Exemptions:
 * - vanilla long-range BEs (Beacon, EndGateway, Structure, Conduit) - config independent;
 * - renderers with shouldRenderOffScreen() == true;
 * - renderers declaring getViewDistance() > 64 (the vanilla default);
 * - renderers whose getRenderBoundingBox() is AABB.INFINITE;
 * - BE or renderer class names matching the current berCullingWhitelist patterns.
 */
public final class BerCullingHelper {
    private static final int VANILLA_DEFAULT_VIEW_DISTANCE = 64;

    private static final LongAdder CULLED_COUNT = new LongAdder();
    private static final LongAdder RENDERED_COUNT = new LongAdder();

    private static volatile int cachedMaxDist = 48;
    private static volatile double cachedMaxDistSq = 48.0D * 48.0D;
    private static volatile List<? extends String> cachedWhitelist = List.of();
    private static volatile boolean configLoaded = false;

    private static final ConcurrentHashMap<WhitelistKey, Boolean> WHITELIST_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, BerStats> CLASS_STATS = new ConcurrentHashMap<>();

    private BerCullingHelper() {
    }

    /**
     * Refreshes cached distance and clears whitelist classification cache.
     * Called on ModConfigEvent.Loading and ModConfigEvent.Reloading.
     */
    public static void refreshConfig() {
        try {
            int dist = AtmMemOptConfig.BER_CULLING_DISTANCE.get();
            cachedMaxDist = dist;
            cachedMaxDistSq = (double) dist * (double) dist;
            cachedWhitelist = List.copyOf(AtmMemOptConfig.BER_CULLING_WHITELIST.get());
            WHITELIST_CACHE.clear();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
            // Config spec not yet loaded
        }
    }

    private static void ensureConfigLoaded() {
        if (!configLoaded) {
            refreshConfig();
        }
    }

    /** Both the block entity class and the renderer class take part in whitelist matching. */
    private static boolean isWhitelisted(Class<?> blockEntityClass, Class<?> rendererClass) {
        WhitelistKey key = new WhitelistKey(blockEntityClass, rendererClass);
        Boolean cached = WHITELIST_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result = computeWhitelisted(blockEntityClass, rendererClass);
        WHITELIST_CACHE.put(key, result);
        return result;
    }

    private static boolean computeWhitelisted(Class<?> blockEntityClass, Class<?> rendererClass) {
        if (BeaconBlockEntity.class.isAssignableFrom(blockEntityClass)
                || TheEndGatewayBlockEntity.class.isAssignableFrom(blockEntityClass)
                || StructureBlockEntity.class.isAssignableFrom(blockEntityClass)
                || ConduitBlockEntity.class.isAssignableFrom(blockEntityClass)) {
            return true;
        }
        String beName = blockEntityClass.getName();
        String rendererName = rendererClass != null ? rendererClass.getName() : null;
        // Use the cached (reload-refreshed) patterns; simple loop, the list is small.
        List<? extends String> patterns = cachedWhitelist;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isEmpty()
                    && (beName.contains(pattern) || (rendererName != null && rendererName.contains(pattern)))) {
                return true;
            }
        }
        return false;
    }

    private static <E extends BlockEntity> boolean hasInfiniteRenderBox(BlockEntityRenderer<E> renderer, E blockEntity) {
        try {
            return renderer.getRenderBoundingBox(blockEntity) == AABB.INFINITE;
        } catch (Throwable t) {
            // A renderer we cannot query is a renderer we must not cull.
            return true;
        }
    }

    public static <E extends BlockEntity> boolean shouldCull(
            E blockEntity,
            BlockEntityRenderer<E> renderer,
            Camera camera
    ) {
        if (blockEntity == null || camera == null) {
            return false;
        }
        ensureConfigLoaded();
        Class<?> type = blockEntity.getClass();
        // Decide the whitelist first so exempt renderers avoid shouldRenderOffScreen().
        if (isWhitelisted(type, renderer != null ? renderer.getClass() : null)
                || (renderer != null && renderer.shouldRenderOffScreen(blockEntity))) {
            recordRendered(type);
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cam = camera.getPosition();
        double dx = (pos.getX() + 0.5D) - cam.x;
        double dy = (pos.getY() + 0.5D) - cam.y;
        double dz = (pos.getZ() + 0.5D) - cam.z;

        if ((dx * dx + dy * dy + dz * dz) > cachedMaxDistSq) {
            // Only a *declared* view distance above the vanilla default warrants an exemption;
            // Math.max(maxDist, getViewDistance()) would exempt every default renderer (64).
            if (renderer != null && renderer.getViewDistance() > VANILLA_DEFAULT_VIEW_DISTANCE) {
                return false;
            }
            // AABB.INFINITE declares "visible everywhere"; check it only for BEs that
            // would otherwise be culled, avoiding an AABB allocation on the near path.
            if (renderer != null && hasInfiniteRenderBox(renderer, blockEntity)) {
                return false;
            }
            CULLED_COUNT.increment();
            getOrCreateStats(type).culled.increment();
            return true;
        }
        recordRendered(type);
        return false;
    }

    private static void recordRendered(Class<?> type) {
        RENDERED_COUNT.increment();
        getOrCreateStats(type).rendered.increment();
    }

    private static BerStats getOrCreateStats(Class<?> type) {
        BerStats stats = CLASS_STATS.get(type);
        if (stats != null) {
            return stats;
        }
        return CLASS_STATS.computeIfAbsent(type, k -> new BerStats());
    }

    public static int getCachedMaxDist() {
        ensureConfigLoaded();
        return cachedMaxDist;
    }

    public static long getCulledCount() {
        return CULLED_COUNT.sum();
    }

    public static long getRenderedCount() {
        return RENDERED_COUNT.sum();
    }

    public static void resetDiag() {
        CLASS_STATS.clear();
        CULLED_COUNT.reset();
        RENDERED_COUNT.reset();
    }

    public static List<BerDiagSnapshot> getTopBerStats(int limit) {
        ensureConfigLoaded();
        return CLASS_STATS.entrySet().stream()
                .map(e -> {
                    Class<?> cls = e.getKey();
                    long rendered = e.getValue().rendered.sum();
                    long culled = e.getValue().culled.sum();
                    return new BerDiagSnapshot(
                            cls.getName(),
                            rendered,
                            culled,
                            isWhitelisted(cls, null)
                    );
                })
                .sorted(Comparator.comparingLong((BerDiagSnapshot s) -> s.rendered() + s.culled()).reversed())
                .limit(limit)
                .toList();
    }

    /** Cache key for whitelist classification: patterns may match either class. */
    private record WhitelistKey(Class<?> blockEntityClass, Class<?> rendererClass) {
    }

    private static final class BerStats {
        final LongAdder rendered = new LongAdder();
        final LongAdder culled = new LongAdder();
    }

    public record BerDiagSnapshot(String className, long rendered, long culled, boolean whitelisted) {
    }
}
