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

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 9a: distance culling for BlockEntityRenderer dispatch.
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

    /** Config-independent vanilla long-range classes; cached forever is intentional. */
    private static final ClassValue<Boolean> VANILLA_LONG_RANGE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return BeaconBlockEntity.class.isAssignableFrom(type)
                    || TheEndGatewayBlockEntity.class.isAssignableFrom(type)
                    || StructureBlockEntity.class.isAssignableFrom(type)
                    || ConduitBlockEntity.class.isAssignableFrom(type);
        }
    };

    /** Only the immutable class name is cached; the whitelist is re-evaluated on every call. */
    private static final ClassValue<String> CLASS_NAME = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            return type.getName();
        }
    };

    private BerCullingHelper() {
    }

    private static boolean isWhitelisted(Class<?> blockEntityClass, Class<?> rendererClass) {
        if (VANILLA_LONG_RANGE.get(blockEntityClass)) {
            return true;
        }
        List<? extends String> patterns = AtmMemOptConfig.BER_CULLING_WHITELIST.get();
        if (patterns.isEmpty()) {
            return false;
        }
        String beName = CLASS_NAME.get(blockEntityClass);
        String rendererName = rendererClass != null ? CLASS_NAME.get(rendererClass) : null;
        // Simple loop (no streams): the pattern list is small and this is a hot path.
        for (int i = 0; i < patterns.size(); i++) {
            String pattern = patterns.get(i);
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (beName.contains(pattern) || (rendererName != null && rendererName.contains(pattern))) {
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
        // B4: decide the whitelist first so exempt renderers avoid shouldRenderOffScreen().
        if (isWhitelisted(blockEntity.getClass(), renderer != null ? renderer.getClass() : null)) {
            return false;
        }
        if (renderer != null && renderer.shouldRenderOffScreen(blockEntity)) {
            return false;
        }
        // Only a *declared* view distance above the vanilla default warrants an exemption.
        // Using Math.max(maxDist, getViewDistance()) would disable culling for every
        // default renderer (getViewDistance() == 64).
        if (renderer != null && renderer.getViewDistance() > VANILLA_DEFAULT_VIEW_DISTANCE) {
            return false;
        }

        int maxDist = AtmMemOptConfig.BER_CULLING_DISTANCE.get();
        double maxDistSq = (double) maxDist * (double) maxDist;

        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cam = camera.getPosition();
        double dx = (pos.getX() + 0.5D) - cam.x;
        double dy = (pos.getY() + 0.5D) - cam.y;
        double dz = (pos.getZ() + 0.5D) - cam.z;

        if ((dx * dx + dy * dy + dz * dz) > maxDistSq) {
            // AABB.INFINITE declares "visible everywhere"; check it only for BEs that
            // would otherwise be culled, avoiding an AABB allocation on the near path.
            if (renderer != null && hasInfiniteRenderBox(renderer, blockEntity)) {
                return false;
            }
            CULLED_COUNT.increment();
            return true;
        }
        return false;
    }

    public static long getCulledCount() {
        return CULLED_COUNT.sum();
    }
}
