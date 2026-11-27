package com.atmmemopt.opt.render;

import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.LongAdder;

/**
 * Module 9c: Distance culling and unfocused-window suppression for client particles.
 * Culls before particle allocation in ParticleEngine#createParticle to avoid GC & tick churn.
 */
public final class ParticleCullingHelper {
    private static final LongAdder CULLED_DISTANCE = new LongAdder();
    private static final LongAdder CULLED_UNFOCUSED = new LongAdder();

    private static volatile int cachedMaxDist = 48;
    private static volatile double cachedMaxDistSq = 48.0D * 48.0D;
    private static volatile boolean cachedUnfocusedSkip = true;
    private static volatile boolean configLoaded = false;

    private ParticleCullingHelper() {
    }

    public static void refreshConfig() {
        try {
            int dist = AtmMemOptConfig.PARTICLE_CULLING_DISTANCE.get();
            cachedMaxDist = dist;
            cachedMaxDistSq = (double) dist * (double) dist;
            cachedUnfocusedSkip = AtmMemOptConfig.UNFOCUSED_SKIP_PARTICLES.get();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
        }
    }

    private static void ensureConfigLoaded() {
        if (!configLoaded) {
            refreshConfig();
        }
    }

    public static boolean shouldCull(double x, double y, double z) {
        ensureConfigLoaded();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        if (cachedUnfocusedSkip && !mc.isWindowActive()) {
            CULLED_UNFOCUSED.increment();
            return true;
        }
        if (mc.gameRenderer == null) {
            return false;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return false;
        }
        Vec3 cam = camera.getPosition();
        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        if ((dx * dx + dy * dy + dz * dz) > cachedMaxDistSq) {
            CULLED_DISTANCE.increment();
            return true;
        }
        return false;
    }

    public static int getCachedMaxDist() {
        ensureConfigLoaded();
        return cachedMaxDist;
    }

    public static long getCulledDistance() {
        return CULLED_DISTANCE.sum();
    }

    public static long getCulledUnfocused() {
        return CULLED_UNFOCUSED.sum();
    }

    public static long getTotalCulled() {
        return CULLED_DISTANCE.sum() + CULLED_UNFOCUSED.sum();
    }
}
