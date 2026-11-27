package com.atmmemopt.opt.iris;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.config.AtmMemOptConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Module 4: Safely purges Iris TransformPatcher and glsl_transformer AST/token caches
 * after shader pack loading completes or on manual command (/atmmemopt purge-shader-cache).
 *
 * Synchronizes on the underlying cache/parser objects and only nulls active tokenStream/input
 * fields on explicit manual purge to avoid racing with lazy shader compilation.
 */
public final class IrisCachePurger {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AtmMemOpt-IrisPurge");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicReference<ScheduledFuture<?>> PENDING_TASK = new AtomicReference<>();
    private static final AtomicLong PURGE_COUNT = new AtomicLong();
    private static final AtomicLong TOTAL_ENTRIES_CLEARED = new AtomicLong();

    private IrisCachePurger() {
    }

    public static void scheduleDelayedPurge() {
        if (!ModList.get().isLoaded("iris") || !AtmMemOptConfig.ENABLE_IRIS_SHADER_CACHE_PURGE.get()) {
            return;
        }
        int delaySec = AtmMemOptConfig.IRIS_AUTO_PURGE_DELAY_SECONDS.get();
        if (delaySec <= 0) {
            return;
        }

        ScheduledFuture<?> next = SCHEDULER.schedule(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    // Auto-purge clears only caches (not active tokenStream/input) for safety
                    PurgeResult res = purgeInternal(false);
                    if (res.totalEntries() > 0) {
                        AtmMemOpt.LOGGER.info(
                                "[AtmMemOpt] Auto-purged Iris/glsl_transformer caches: transformCache={}, buildCache={}, parseCache={}, secondaryCache={}",
                                res.transformCache(), res.buildCache(), res.parseCache(), res.secondaryCache()
                        );
                    }
                });
            }
        }, delaySec, TimeUnit.SECONDS);

        ScheduledFuture<?> prev = PENDING_TASK.getAndSet(next);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    /**
     * Manual purge invoked via /atmmemopt purge-shader-cache.
     */
    public static PurgeResult purgeNow() {
        return purgeInternal(true);
    }

    private static PurgeResult purgeInternal(boolean clearActiveStreams) {
        if (!ModList.get().isLoaded("iris")) {
            return PurgeResult.EMPTY;
        }

        int transformCache = 0;
        int buildCache = 0;
        int parseCache = 0;
        int secondaryCache = 0;
        int clearedStreams = 0;

        try {
            Class<?> tpClass = Class.forName("net.irisshaders.iris.pipeline.transform.TransformPatcher");

            // 1. TransformPatcher.cache (Map<CacheKey, Map<PatchShaderType, String>>)
            Object cacheObj = getStaticField(tpClass, "cache");
            if (cacheObj instanceof Map<?, ?> map) {
                synchronized (map) {
                    transformCache = map.size();
                    map.clear();
                }
            }

            // 2. TransformPatcher.transformer (EnumASTTransformer extends ASTParser)
            Object transformerObj = getStaticField(tpClass, "transformer");
            if (transformerObj != null) {
                int[] counts = purgeAstParser(transformerObj, clearActiveStreams);
                buildCache += counts[0];
                parseCache += counts[1];
                secondaryCache += counts[2];
                clearedStreams += counts[3];
            }

            // 3. ASTParser.INSTANCE (shared internal instance, if distinct)
            try {
                Class<?> astParserClass = Class.forName("io.github.douira.glsl_transformer.ast.transform.ASTParser");
                Object internalInstance = getStaticField(astParserClass, "INSTANCE");
                if (internalInstance != null && internalInstance != transformerObj) {
                    int[] counts = purgeAstParser(internalInstance, clearActiveStreams);
                    buildCache += counts[0];
                    parseCache += counts[1];
                    secondaryCache += counts[2];
                    clearedStreams += counts[3];
                }
            } catch (ClassNotFoundException ignored) {
            }
        } catch (Throwable t) {
            AtmMemOpt.LOGGER.warn("[AtmMemOpt] Failed to purge Iris/glsl_transformer caches", t);
        }

        PurgeResult result = new PurgeResult(transformCache, buildCache, parseCache, secondaryCache, clearedStreams);
        PURGE_COUNT.incrementAndGet();
        TOTAL_ENTRIES_CLEARED.addAndGet(result.totalEntries());
        return result;
    }

    private static int[] purgeAstParser(Object astParser, boolean clearActiveStreams) {
        int buildCacheCleared = 0;
        int parseCacheCleared = 0;
        int secondaryCacheCleared = 0;
        int streamsCleared = 0;

        synchronized (astParser) {
            Object buildCacheObj = getFieldInHierarchy(astParser, "buildCache");
            if (buildCacheObj instanceof Map<?, ?> map) {
                synchronized (map) {
                    buildCacheCleared = map.size();
                    map.clear();
                }
            }

            Object parserObj = getFieldInHierarchy(astParser, "parser");
            if (parserObj != null) {
                synchronized (parserObj) {
                    Object parseCacheObj = getFieldInHierarchy(parserObj, "parseCache");
                    if (parseCacheObj instanceof Map<?, ?> map) {
                        synchronized (map) {
                            parseCacheCleared = map.size();
                            map.clear();
                        }
                    }

                    Object secondaryCacheObj = getFieldInHierarchy(parserObj, "secondaryCache");
                    if (secondaryCacheObj instanceof Map<?, ?> map) {
                        synchronized (map) {
                            secondaryCacheCleared = map.size();
                            map.clear();
                        }
                    }

                    if (clearActiveStreams) {
                        if (clearFieldInHierarchy(parserObj, "tokenStream")) {
                            streamsCleared++;
                        }
                        if (clearFieldInHierarchy(parserObj, "input")) {
                            streamsCleared++;
                        }
                    }
                }
            }
        }

        return new int[]{buildCacheCleared, parseCacheCleared, secondaryCacheCleared, streamsCleared};
    }

    private static Object getStaticField(Class<?> cls, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getFieldInHierarchy(Object target, String name) {
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean clearFieldInHierarchy(Object target, String name) {
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                if (f.get(target) != null) {
                    f.set(target, null);
                    return true;
                }
                return false;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    public static long getPurgeCount() {
        return PURGE_COUNT.get();
    }

    public static long getTotalEntriesCleared() {
        return TOTAL_ENTRIES_CLEARED.get();
    }

    public record PurgeResult(int transformCache, int buildCache, int parseCache, int secondaryCache, int clearedStreams) {
        public static final PurgeResult EMPTY = new PurgeResult(0, 0, 0, 0, 0);

        public int totalEntries() {
            return transformCache + buildCache + parseCache + secondaryCache;
        }
    }
}
