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
    public static final ModConfigSpec.BooleanValue ENABLE_PARTICLE_DISTANCE_CULLING;
    public static final ModConfigSpec.IntValue PARTICLE_CULLING_DISTANCE;
    public static final ModConfigSpec.BooleanValue UNFOCUSED_SKIP_PARTICLES;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_DISTANCE_CULLING;
    public static final ModConfigSpec.IntValue ENTITY_CULLING_DISTANCE;
    public static final ModConfigSpec.IntValue ITEM_ENTITY_CULLING_DISTANCE;
    public static final ModConfigSpec.IntValue XP_ORB_CULLING_DISTANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_CULLING_WHITELIST;
    public static final ModConfigSpec.BooleanValue ENABLE_DATA_COMPONENT_PATCH_OPT;
    public static final ModConfigSpec.IntValue DATA_COMPONENT_PATCH_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_PALETTED_CONTAINER_OPT;
    public static final ModConfigSpec.BooleanValue ENABLE_RESOURCE_LOCATION_OPT;
    public static final ModConfigSpec.IntValue RESOURCE_LOCATION_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_CLIMATE_PARAMETER_OPT;
    public static final ModConfigSpec.BooleanValue ENABLE_SPRITE_MIPMAP_RELEASE;
    public static final ModConfigSpec.BooleanValue ENABLE_BAKED_QUAD_VERTEX_DEDUP;
    public static final ModConfigSpec.IntValue BAKED_QUAD_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_TAG_ENTRY_OPT;

    private static volatile Map<String, String> earlyValues;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("AtmMemOpt module toggles. Changes to Mixin toggles require a game restart.")
               .push("modules");

        ENABLE_OWO_DERIVED_MAP_OPT = builder
                .comment("Module 1: Lazily allocate/skip owo-lib DerivedComponentMap and its delegate PatchedDataComponentMap when not needed.")
                .define("enableOwoDerivedMapOpt", true);

        ENABLE_PATCHED_COMPONENT_MAP_OPT = builder
                .comment("Module 2: Optimize PatchedDataComponentMap empty/duplicate patch handling safely without breaking CoW.")
                .define("enablePatchedComponentMapOpt", true);

        ENABLE_IRONS_SPELL_CONTAINER_OPT = builder
                .comment("Module 3: Share immutable empty/duplicate SpellContainer, SpellSlot, and SpellData instances in Iron's Spellbooks.")
                .define("enableIronsSpellContainerOpt", true);

        ENABLE_IRIS_SHADER_CACHE_PURGE = builder
                .comment("Module 4: Purge Iris / glsl_transformer AST and token caches after shader pack load and expose purge command.")
                .define("enableIrisShaderCachePurge", true);

        IRIS_AUTO_PURGE_DELAY_SECONDS = builder
                .comment("Delay in seconds after Iris shader pipeline preparation before automatically purging glsl_transformer caches (0 disables auto-purge, manual command still works).")
                .defineInRange("irisAutoPurgeDelaySeconds", 30, 0, 600);

        ENABLE_NATIVE_IMAGE_DIAGNOSTICS = builder
                .comment("Module 5: Track NativeImage allocation stack traces and close() calls for /atmmemopt diag nativeimage. Default OFF due to stack-capture overhead.")
                .define("enableNativeImageDiagnostics", false);

        ENABLE_ITEM_ENCHANTMENTS_OPT = builder
                .comment("Module 6: Intern immutable ItemEnchantments instances and unify empty instances to ItemEnchantments.EMPTY.")
                .define("enableItemEnchantmentsOpt", true);

        ENABLE_VOXEL_SHAPE_OPT = builder
                .comment("Module 7: Deduplicate immutable ArrayVoxelShape DoubleArrayList coordinates and identical VoxelShape instances.")
                .define("enableVoxelShapeOpt", true);

        VOXEL_SHAPE_MAX_CACHE_ENTRIES = builder
                .comment("Module 7: Maximum entries in VoxelShape intern cache to avoid CHM/key bloat from rare shapes (0 disables VoxelShape body caching while keeping DoubleArrayList deduplication).")
                .defineInRange("voxelShapeMaxCacheEntries", 32768, 0, 262144);

        ENABLE_JEI_PARALLEL_FILTER = builder
                .comment("Module 8a: Parallelize JEI ElementSearch index building (keeps TOOLTIPS/COLORS/CREATIVE_TABS on main thread).")
                .define("enableJeiParallelFilter", true);

        JEI_LAZY_TOOLTIP_INDEX = builder
                .comment("Module 8a: Defer building JEI's heavy TOOLTIPS index (15.9s) until the first '#' tooltip search is performed.")
                .define("jeiLazyTooltipIndex", true);

        JEI_SKIP_TOOLTIP_INDEX = builder
                .comment("Module 8a (optional): Permanently skip building JEI's #tooltip search index.")
                .define("jeiSkipTooltipIndex", false);

        ENABLE_JEISTUFF_SKIP_LOOT_SCAN = builder
                .comment("Module 8b: Skip JEIStuff's synchronous loot table scan on world join.")
                .define("enableJeiStuffSkipLootScan", true);

        ENABLE_RELOAD_PROFILER = builder
                .comment("Module 8c: Log Top 10 slowest resource/datapack reload listeners to identify startup & world-load bottlenecks.")
                .define("enableReloadProfiler", true);

        ENABLE_BER_DISTANCE_CULLING = builder
                .comment("Module 9a: Cull BlockEntityRenderer rendering beyond berCullingDistance blocks from camera.")
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
                .comment("Module 9b: Cap framerate when Minecraft window is not focused to save GPU/CPU power.")
                .define("enableUnfocusedFpsLimit", true);

        UNFOCUSED_FPS_LIMIT = builder
                .comment("Module 9b: Framerate limit when Minecraft window is unfocused.")
                .defineInRange("unfocusedFpsLimit", 15, 1, 60);

        ENABLE_PARTICLE_DISTANCE_CULLING = builder
                .comment("Module 9c: Skip creating particles beyond particleCullingDistance blocks (and optionally while unfocused).")
                .define("enableParticleDistanceCulling", true);

        PARTICLE_CULLING_DISTANCE = builder
                .comment("Module 9c: Maximum spawn distance (in blocks) for client particles.")
                .defineInRange("particleCullingDistance", 48, 16, 256);

        UNFOCUSED_SKIP_PARTICLES = builder
                .comment("Module 9c: Skip spawning new particles while the Minecraft window is unfocused.")
                .define("unfocusedSkipParticles", true);

        ENABLE_ENTITY_DISTANCE_CULLING = builder
                .comment("Module 9d: Distance-cull non-whitelisted entities in EntityRenderDispatcher#shouldRender.")
                .define("enableEntityDistanceCulling", true);

        ENTITY_CULLING_DISTANCE = builder
                .comment("Module 9d: Maximum render distance (in blocks) for general non-whitelisted entities.")
                .defineInRange("entityCullingDistance", 64, 16, 256);

        ITEM_ENTITY_CULLING_DISTANCE = builder
                .comment("Module 9d: Maximum render distance (in blocks) for dropped ItemEntity instances.")
                .defineInRange("itemEntityCullingDistance", 32, 8, 128);

        XP_ORB_CULLING_DISTANCE = builder
                .comment("Module 9d: Maximum render distance (in blocks) for ExperienceOrb instances.")
                .defineInRange("xpOrbCullingDistance", 24, 8, 128);

        ENTITY_CULLING_WHITELIST = builder
                .comment("Module 9d: Class name substrings excluded from entity distance culling (bosses, contraptions, vehicles, etc.).")
                .defineListAllowEmpty(
                        "entityCullingWhitelist",
                        List.of(
                                "com.simibubi.create",
                                "Boss",
                                "Dragon",
                                "Wither",
                                "cataclysm",
                                "irons_spellbooks",
                                "twilightforest.entity.boss"
                        ),
                        o -> o instanceof String
                );

        ENABLE_DATA_COMPONENT_PATCH_OPT = builder
                .comment("Module 10: Intern immutable DataComponentPatch instances and share their backing Reference2ObjectMap copy-on-write in PatchedDataComponentMap.")
                .define("enableDataComponentPatchOpt", true);

        DATA_COMPONENT_PATCH_MAX_CACHE_ENTRIES = builder
                .comment("Module 10: Maximum entries in the DataComponentPatch weak intern cache (0 disables patch interning while keeping CoW map sharing).")
                .defineInRange("dataComponentPatchMaxCacheEntries", 65536, 0, 262144);

        ENABLE_PALETTED_CONTAINER_OPT = builder
                .comment("Module 11: Replace per-PalettedContainer ThreadingDetector (Semaphore + ReentrantLock) with a shared no-op detector to save ~25-30MB and lock overhead.")
                .define("enablePalettedContainerOpt", true);

        ENABLE_RESOURCE_LOCATION_OPT = builder
                .comment("Module 12: Intern ResourceLocation namespaces and deduplicate identical ResourceLocation instances within a bounded weak cache.")
                .define("enableResourceLocationOpt", true);

        RESOURCE_LOCATION_MAX_CACHE_ENTRIES = builder
                .comment("Module 12: Maximum entries in the ResourceLocation weak intern cache (0 disables instance caching while keeping namespace string interning).")
                .defineInRange("resourceLocationMaxCacheEntries", 131072, 0, 524288);

        ENABLE_CLIMATE_PARAMETER_OPT = builder
                .comment("Module 13: Intern immutable Biome Climate.Parameter instances.")
                .define("enableClimateParameterOpt", true);

        ENABLE_SPRITE_MIPMAP_RELEASE = builder
                .comment("Module 14: Release mipmap NativeImage levels (1..N) of static SpriteContents after GPU atlas upload (keeps originalImage level 0 intact).")
                .define("enableSpriteMipmapRelease", true);

        ENABLE_BAKED_QUAD_VERTEX_DEDUP = builder
                .comment("Module 15: Deduplicate identical BakedQuad vertex int[] arrays within a bounded weak cache.")
                .define("enableBakedQuadVertexDedup", true);

        BAKED_QUAD_MAX_CACHE_ENTRIES = builder
                .comment("Module 15: Maximum entries in the BakedQuad vertex int[] weak intern cache.")
                .defineInRange("bakedQuadMaxCacheEntries", 65536, 0, 262144);

        ENABLE_TAG_ENTRY_OPT = builder
                .comment("Module 16: Intern immutable TagEntry instances across datapack tags.")
                .define("enableTagEntryOpt", true);

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
        return Boolean.parseBoolean(raw.trim());
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
