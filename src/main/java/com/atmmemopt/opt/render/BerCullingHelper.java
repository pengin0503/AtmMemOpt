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

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 9a: Zero-allocation distance culling for BlockEntityRenderer dispatch.
 *
 * Safety guarantees:
 * - Respects BlockEntityRenderer#shouldRenderOffScreen(blockEntity) automatically.
 * - Excludes vanilla long-range BEs (Beacon, EndGateway, Structure, Conduit) and
 *   configurable mod multiblock / contraption class patterns (cached per class via ClassValue).
 */
public final class BerCullingHelper {
    private static final LongAdder CULLED_COUNT = new LongAdder();

    private static final ClassValue<Boolean> WHITELISTED_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (BeaconBlockEntity.class.isAssignableFrom(type)
                    || TheEndGatewayBlockEntity.class.isAssignableFrom(type)
                    || StructureBlockEntity.class.isAssignableFrom(type)
                    || ConduitBlockEntity.class.isAssignableFrom(type)) {
                return Boolean.TRUE;
            }
            String className = type.getName();
            List<? extends String> patterns = AtmMemOptConfig.BER_CULLING_WHITELIST.get();
            for (String pattern : patterns) {
                if (pattern != null && !pattern.isEmpty() && className.contains(pattern)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
    };

    private BerCullingHelper() {
    }

    public static <E extends BlockEntity> boolean shouldCull(
            E blockEntity,
            BlockEntityRenderer<E> renderer,
            Camera camera
    ) {
        if (blockEntity == null || camera == null) {
            return false;
        }
        if (renderer != null && renderer.shouldRenderOffScreen(blockEntity)) {
            return false;
        }
        if (WHITELISTED_CLASS.get(blockEntity.getClass())) {
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
            CULLED_COUNT.increment();
            return true;
        }
        return false;
    }

    public static long getCulledCount() {
        return CULLED_COUNT.sum();
    }
}
