package com.atmmemopt.opt.render;

import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Module 9d: Zero-allocation distance culling for EntityRenderDispatcher#shouldRender,
 * with separate tighter limits for ItemEntity and ExperienceOrb and automatic exclusion
 * of players, bosses, noCulling entities, and configurable class whitelist patterns.
 */
public final class EntityCullingHelper {
    private static final LongAdder CULLED_COUNT = new LongAdder();
    private static final LongAdder RENDERED_COUNT = new LongAdder();

    private static volatile int cachedGeneralDist = 64;
    private static volatile double cachedGeneralDistSq = 64.0D * 64.0D;
    private static volatile int cachedItemDist = 32;
    private static volatile double cachedItemDistSq = 32.0D * 32.0D;
    private static volatile int cachedXpDist = 24;
    private static volatile double cachedXpDistSq = 24.0D * 24.0D;
    private static volatile List<? extends String> cachedWhitelist = List.of();
    private static volatile boolean configLoaded = false;

    private static final ConcurrentHashMap<Class<?>, Boolean> WHITELIST_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, EntityStats> CLASS_STATS = new ConcurrentHashMap<>();

    private EntityCullingHelper() {
    }

    public static void refreshConfig() {
        try {
            int general = AtmMemOptConfig.ENTITY_CULLING_DISTANCE.get();
            int item = AtmMemOptConfig.ITEM_ENTITY_CULLING_DISTANCE.get();
            int xp = AtmMemOptConfig.XP_ORB_CULLING_DISTANCE.get();
            cachedGeneralDist = general;
            cachedGeneralDistSq = (double) general * (double) general;
            cachedItemDist = item;
            cachedItemDistSq = (double) item * (double) item;
            cachedXpDist = xp;
            cachedXpDistSq = (double) xp * (double) xp;
            cachedWhitelist = List.copyOf(AtmMemOptConfig.ENTITY_CULLING_WHITELIST.get());
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
        if (Player.class.isAssignableFrom(type)
                || EnderDragon.class.isAssignableFrom(type)
                || WitherBoss.class.isAssignableFrom(type)) {
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

    public static boolean shouldCull(Entity entity, double camX, double camY, double camZ) {
        if (entity == null) {
            return false;
        }
        ensureConfigLoaded();

        Class<?> type = entity.getClass();
        if (entity.noCulling || isWhitelisted(type)) {
            recordRendered(type);
            return false;
        }

        double maxDistSq;
        if (entity instanceof ItemEntity) {
            maxDistSq = cachedItemDistSq;
        } else if (entity instanceof ExperienceOrb) {
            maxDistSq = cachedXpDistSq;
        } else {
            maxDistSq = cachedGeneralDistSq;
        }

        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        if ((dx * dx + dy * dy + dz * dz) > maxDistSq) {
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

    private static EntityStats getOrCreateStats(Class<?> type) {
        EntityStats stats = CLASS_STATS.get(type);
        if (stats != null) {
            return stats;
        }
        return CLASS_STATS.computeIfAbsent(type, k -> new EntityStats());
    }

    public static int getCachedGeneralDist() {
        ensureConfigLoaded();
        return cachedGeneralDist;
    }

    public static int getCachedItemDist() {
        ensureConfigLoaded();
        return cachedItemDist;
    }

    public static int getCachedXpDist() {
        ensureConfigLoaded();
        return cachedXpDist;
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

    public static List<EntityDiagSnapshot> getTopEntityStats(int limit) {
        ensureConfigLoaded();
        return CLASS_STATS.entrySet().stream()
                .map(e -> {
                    Class<?> cls = e.getKey();
                    long rendered = e.getValue().rendered.sum();
                    long culled = e.getValue().culled.sum();
                    return new EntityDiagSnapshot(cls.getName(), rendered, culled, isWhitelisted(cls));
                })
                .sorted(Comparator.comparingLong((EntityDiagSnapshot s) -> s.rendered() + s.culled()).reversed())
                .limit(limit)
                .toList();
    }

    private static final class EntityStats {
        final LongAdder rendered = new LongAdder();
        final LongAdder culled = new LongAdder();
    }

    public record EntityDiagSnapshot(String className, long rendered, long culled, boolean whitelisted) {
    }
}
