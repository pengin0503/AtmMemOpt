package com.atmmemopt.opt.tick;

import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.AbstractVillager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 20: Throttles Mob#serverAiStep() for distant non-critical mobs to improve TPS.
 *
 * Safety guarantees:
 * - Never throttles bosses, villagers, tamed pets, persistent/named mobs, vehicles/passengers,
 *   mobs with an active combat target, or classes matching mobAiThrottleWhitelist.
 * - Uses phase staggering (tickCount + entityId) to avoid synchronized tick spikes.
 */
public final class MobAiThrottleHelper {
    private static final LongAdder THROTTLED_TICKS = new LongAdder();

    private static volatile int cachedNearDist = 48;
    private static volatile double cachedNearDistSq = 48.0D * 48.0D;
    private static volatile int cachedFarDist = 64;
    private static volatile double cachedFarDistSq = 64.0D * 64.0D;
    private static volatile List<? extends String> cachedWhitelist = List.of();
    private static volatile boolean configLoaded = false;

    private static final ConcurrentHashMap<Class<?>, Boolean> WHITELIST_CACHE = new ConcurrentHashMap<>();

    private MobAiThrottleHelper() {
    }

    public static void refreshConfig() {
        try {
            int near = AtmMemOptConfig.MOB_AI_THROTTLE_DISTANCE.get();
            int far = Math.max(near, AtmMemOptConfig.MOB_AI_THROTTLE_FAR_DISTANCE.get());
            cachedNearDist = near;
            cachedNearDistSq = (double) near * (double) near;
            cachedFarDist = far;
            cachedFarDistSq = (double) far * (double) far;
            cachedWhitelist = List.copyOf(AtmMemOptConfig.MOB_AI_THROTTLE_WHITELIST.get());
            WHITELIST_CACHE.clear();
            configLoaded = true;
        } catch (IllegalStateException ignored) {
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
        if (EnderDragon.class.isAssignableFrom(type)
                || WitherBoss.class.isAssignableFrom(type)
                || AbstractVillager.class.isAssignableFrom(type)) {
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

    public static boolean shouldThrottle(Mob mob) {
        if (mob == null || !(mob.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ensureConfigLoaded();

        if (mob.getTarget() != null
                || mob.isPersistenceRequired()
                || mob.hasCustomName()
                || mob.isVehicle()
                || mob.isPassenger()
                || mob.hurtTime > 0
                || (mob instanceof TamableAnimal tamable && tamable.isTame())
                || isWhitelisted(mob.getClass())) {
            return false;
        }

        List<ServerPlayer> players = serverLevel.players();
        if (players.isEmpty()) {
            return false;
        }

        double mx = mob.getX();
        double my = mob.getY();
        double mz = mob.getZ();
        double minDistSq = Double.MAX_VALUE;
        for (int i = 0, n = players.size(); i < n; i++) {
            ServerPlayer p = players.get(i);
            if (p.isSpectator()) {
                continue;
            }
            double dx = p.getX() - mx;
            double dy = p.getY() - my;
            double dz = p.getZ() - mz;
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < minDistSq) {
                minDistSq = dSq;
            }
        }

        if (minDistSq <= cachedNearDistSq) {
            return false;
        }

        int phase = mob.tickCount + mob.getId();
        boolean skip = minDistSq > cachedFarDistSq
                ? (phase & 3) != 0
                : (phase & 1) != 0;
        if (skip) {
            THROTTLED_TICKS.increment();
            return true;
        }
        return false;
    }

    public static int getCachedNearDist() {
        ensureConfigLoaded();
        return cachedNearDist;
    }

    public static int getCachedFarDist() {
        ensureConfigLoaded();
        return cachedFarDist;
    }

    public static long getThrottledTicks() {
        return THROTTLED_TICKS.sum();
    }
}
