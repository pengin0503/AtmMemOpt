package com.atmmemopt.config;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common configuration for AtmMemOpt.
 * Provides both standard NeoForge ModConfigSpec values and early boot-time reads
 * for IMixinConfigPlugin before NeoForge loads ModConfig instances.
 */
public final class AtmMemOptConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AtmMemOptConfig");

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_OWO_DERIVED_MAP_OPT;
    public static final ModConfigSpec.BooleanValue ENABLE_PATCHED_COMPONENT_MAP_OPT;
    public static final ModConfigSpec.BooleanValue ENABLE_IRONS_SPELL_CONTAINER_OPT;
    public static final ModConfigSpec.BooleanValue ENABLE_IRIS_SHADER_CACHE_PURGE;
    public static final ModConfigSpec.IntValue IRIS_AUTO_PURGE_DELAY_SECONDS;
    public static final ModConfigSpec.BooleanValue ENABLE_NATIVE_IMAGE_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_ITEM_ENCHANTMENTS_OPT;
    public static final ModConfigSpec.BooleanValue ENABLE_VOXEL_SHAPE_OPT;
    public static final ModConfigSpec.IntValue VOXEL_SHAPE_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_JEI_PARALLEL_FILTER;
    public static final ModConfigSpec.BooleanValue JEI_LAZY_TOOLTIP_INDEX;
    public static final ModConfigSpec.BooleanValue JEI_SKIP_TOOLTIP_INDEX;
    public static final ModConfigSpec.BooleanValue ENABLE_JEISTUFF_SKIP_LOOT_SCAN;
    public static final ModConfigSpec.BooleanValue ENABLE_RELOAD_PROFILER;
    public static final ModConfigSpec.BooleanValue ENABLE_BER_DISTANCE_CULLING;
    public static final ModConfigSpec.IntValue BER_CULLING_DISTANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BER_CULLING_WHITELIST;
    public static final ModConfigSpec.BooleanValue ENABLE_UNFOCUSED_FPS_LIMIT;
    public static final ModConfigSpec.IntValue UNFOCUSED_FPS_LIMIT;

    private static volatile Map<String, String> earlyValues;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("AtmMemOpt module toggles. Changes to Mixin toggles require a game restart.")
               .push("modules");

        ENABLE_OWO_DERIVED_MAP_OPT = builder
                .comment("Module 1: Lazily allocate/skip owo-lib DerivedComponentMap and its delegate PatchedDataComponentMap when not needed. Requires restart (mixin toggle read at startup).")
                .define("enableOwoDerivedMapOpt", true);

        ENABLE_PATCHED_COMPONENT_MAP_OPT = builder
                .comment("Module 2: Optimize PatchedDataComponentMap empty/duplicate patch handling safely without breaking CoW. Requires restart (mixin toggle read at startup).")
                .define("enablePatchedComponentMapOpt", true);

        ENABLE_IRONS_SPELL_CONTAINER_OPT = builder
                .comment("Module 3: Share immutable empty/duplicate SpellContainer, SpellSlot, and SpellData instances in Iron's Spellbooks. Requires restart (mixin toggle read at startup).")
                .define("enableIronsSpellContainerOpt", true);

        ENABLE_IRIS_SHADER_CACHE_PURGE = builder
                .comment("Module 4: Purge Iris / glsl_transformer AST and token caches after shader pack load and expose purge command. Requires restart (mixin toggle read at startup).")
                .define("enableIrisShaderCachePurge", true);

        IRIS_AUTO_PURGE_DELAY_SECONDS = builder
                .comment("Delay in seconds after Iris shader pipeline preparation before automatically purging glsl_transformer caches (0 disables auto-purge, manual command still works).")
                .defineInRange("irisAutoPurgeDelaySeconds", 30, 0, 600);

        ENABLE_NATIVE_IMAGE_DIAGNOSTICS = builder
                .comment("Module 5: Track NativeImage allocation stack traces and close() calls for /atmmemopt diag nativeimage. Default OFF due to stack-capture overhead. Requires restart (mixin toggle read at startup).")
                .define("enableNativeImageDiagnostics", false);

        ENABLE_ITEM_ENCHANTMENTS_OPT = builder
                .comment("Module 6: Intern immutable ItemEnchantments instances and unify empty instances to ItemEnchantments.EMPTY. Requires restart (mixin toggle read at startup).")
                .define("enableItemEnchantmentsOpt", true);

        ENABLE_VOXEL_SHAPE_OPT = builder
                .comment("Module 7: Deduplicate immutable ArrayVoxelShape DoubleArrayList coordinates and identical VoxelShape instances. Requires restart (mixin toggle read at startup).")
                .define("enableVoxelShapeOpt", true);

        VOXEL_SHAPE_MAX_CACHE_ENTRIES = builder
                .comment("Module 7: Maximum entries in VoxelShape intern cache to avoid CHM/key bloat from rare shapes (0 disables VoxelShape body caching while keeping DoubleArrayList deduplication).")
                .defineInRange("voxelShapeMaxCacheEntries", 32768, 0, 262144);

        ENABLE_JEI_PARALLEL_FILTER = builder
                .comment("Module 8a: Parallelize JEI ElementSearch index building (keeps TOOLTIPS/COLORS/CREATIVE_TABS on main thread). Requires restart (mixin toggle read at startup).")
                .define("enableJeiParallelFilter", true);

        JEI_LAZY_TOOLTIP_INDEX = builder
                .comment("Module 8a: Defer building JEI's heavy TOOLTIPS index (15.9s) until the first '$' tooltip search is performed. Only effective when enableJeiParallelFilter=true (the JEI mixins are not applied otherwise); no restart needed for this sub-flag itself.")
                .define("jeiLazyTooltipIndex", true);

        JEI_SKIP_TOOLTIP_INDEX = builder
                .comment("Module 8a (optional): Permanently skip building JEI's $tooltip search index. Only effective when enableJeiParallelFilter=true (the JEI mixins are not applied otherwise); no restart needed for this sub-flag itself.")
                .define("jeiSkipTooltipIndex", false);

        ENABLE_JEISTUFF_SKIP_LOOT_SCAN = builder
                .comment("Module 8b: Skip JEIStuff's synchronous loot table scan on world join. Requires restart (mixin toggle read at startup).")
                .define("enableJeiStuffSkipLootScan", true);

        ENABLE_RELOAD_PROFILER = builder
                .comment("Module 8c: Log Top 10 slowest resource/datapack reload listeners to identify startup & world-load bottlenecks. Requires restart (mixin toggle read at startup).")
                .define("enableReloadProfiler", true);

        ENABLE_BER_DISTANCE_CULLING = builder
                .comment("Module 9a: Cull BlockEntityRenderer rendering beyond berCullingDistance blocks from camera. Requires restart (mixin toggle read at startup).")
                .define("enableBerDistanceCulling", true);

        BER_CULLING_DISTANCE = builder
                .comment("Module 9a: Maximum render distance (in blocks) for non-whitelisted BlockEntityRenderers.")
                .defineInRange("berCullingDistance", 48, 16, 256);

        BER_CULLING_WHITELIST = builder
                .comment("Module 9a: Class name substrings excluded from BER distance culling (large multiblocks, contraptions, beacons, etc.).")
                .defineListAllowEmpty(
                        "berCullingWhitelist",
                        List.of(
                                "BeaconBlockEntity",
                                "EndGateway",
                                "StructureBlock",
                                "ConduitBlock",
                                "mekanism.generators",
                                "mekanism.common.tile.multiblock",
                                "zerocore",
                                "extremereactors",
                                "biggerreactors",
                                "immersiveengineering",
                                "com.simibubi.create",
                                "draconicevolution"
                        ),
                        o -> o instanceof String
                );

        ENABLE_UNFOCUSED_FPS_LIMIT = builder
                .comment("Module 9b: Cap framerate when Minecraft window is not focused to save GPU/CPU power. Requires restart (mixin toggle read at startup).")
                .define("enableUnfocusedFpsLimit", true);

        UNFOCUSED_FPS_LIMIT = builder
                .comment("Module 9b: Framerate limit when Minecraft window is unfocused.")
                .defineInRange("unfocusedFpsLimit", 15, 1, 60);

        builder.pop();
        SPEC = builder.build();
    }

    private AtmMemOptConfig() {
    }

    /**
     * Reads a boolean toggle directly from config/atmmemopt-common.toml during early Mixin loading.
     */
    public static boolean getEarlyBoolean(String key, boolean defaultValue) {
        Map<String, String> values = loadEarlyValues();
        String raw = values.get(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.trim();
        // Boolean.parseBoolean() silently maps typos ("ture", "1", "yes") to false,
        // disabling a module before NeoForge's ModConfigSpec can validate the value.
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        LOGGER.warn("[AtmMemOpt] Invalid boolean value '{}' for '{}' in config/atmmemopt-common.toml; using default {}.",
                value, key, defaultValue);
        return defaultValue;
    }

    private static Map<String, String> loadEarlyValues() {
        Map<String, String> cached = earlyValues;
        if (cached != null) {
            return cached;
        }
        Map<String, String> parsed = new HashMap<>();
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("atmmemopt-common.toml");
        if (Files.isRegularFile(configPath)) {
            try {
                for (String line : Files.readAllLines(configPath)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        String k = trimmed.substring(0, eq).trim();
                        String v = trimmed.substring(eq + 1).trim();
                        int commentIdx = v.indexOf('#');
                        if (commentIdx >= 0) {
                            v = v.substring(0, commentIdx).trim();
                        }
                        parsed.put(k, v);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read early config {}, falling back to defaults.", configPath, e);
            }
        }
        earlyValues = parsed;
        return parsed;
    }
}
