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
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 9a: Zero-allocation distance culling for BlockEntityRenderer dispatch.
 *
 * Phase 5 improvements:
 * - Caches config distance & whitelist patterns and refreshes on ModConfigEvent.Reloading.
 * - Tracks per-BlockEntity-class rendered vs culled counts for /atmmemopt diag ber.
 */
public final class BerCullingHelper {
    private static final LongAdder CULLED_COUNT = new LongAdder();
    private static final LongAdder RENDERED_COUNT = new LongAdder();

    private static volatile int cachedMaxDist = 48;
    private static volatile double cachedMaxDistSq = 48.0D * 48.0D;
    private static volatile List<? extends String> cachedWhitelist = List.of();
    private static volatile boolean configLoaded = false;

    private static final ConcurrentHashMap<Class<?>, Boolean> WHITELIST_CACHE = new ConcurrentHashMap<>();
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

    private static boolean isWhitelisted(Class<?> type) {
        Boolean cached = WHITELIST_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        boolean result = computeWhitelisted(type);
        WHITELIST_CACHE.put(type, result);
        return result;
    }

    private static boolean computeWhitelisted(Class<?> type) {
        if (BeaconBlockEntity.class.isAssignableFrom(type)
                || TheEndGatewayBlockEntity.class.isAssignableFrom(type)
                || StructureBlockEntity.class.isAssignableFrom(type)
                || ConduitBlockEntity.class.isAssignableFrom(type)) {
            return true;
        }
        String className = type.getName();
        List<? extends String> patterns = cachedWhitelist;
        for (String pattern : patterns) {
            if (pattern != null && !pattern.isEmpty() && className.contains(pattern)) {
                return true;
            }
        }
        return false;
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
        if ((renderer != null && renderer.shouldRenderOffScreen(blockEntity)) || isWhitelisted(type)) {
            recordRendered(type);
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cam = camera.getPosition();
        double dx = (pos.getX() + 0.5D) - cam.x;
        double dy = (pos.getY() + 0.5D) - cam.y;
        double dz = (pos.getZ() + 0.5D) - cam.z;

        if ((dx * dx + dy * dy + dz * dz) > cachedMaxDistSq) {
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
                            isWhitelisted(cls)
                    );
                })
                .sorted(Comparator.comparingLong((BerDiagSnapshot s) -> s.rendered() + s.culled()).reversed())
                .limit(limit)
                .toList();
    }

    private static final class BerStats {
        final LongAdder rendered = new LongAdder();
        final LongAdder culled = new LongAdder();
    }

    public record BerDiagSnapshot(String className, long rendered, long culled, boolean whitelisted) {
    }
}
