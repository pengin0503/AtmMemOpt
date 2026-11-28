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
    public static final ModConfigSpec.IntValue SPELL_CONTAINER_STRONG_CACHE_MAX_ENTRIES;
    public static final ModConfigSpec.IntValue SPELL_CONTAINER_CACHE_MAX_ENTRIES;
    public static final ModConfigSpec.IntValue SPELL_CONTAINER_EMPTY_SLOT_MAX_LENGTH;
    public static final ModConfigSpec.BooleanValue ENABLE_IRIS_SHADER_CACHE_PURGE;
    public static final ModConfigSpec.IntValue IRIS_AUTO_PURGE_DELAY_SECONDS;
    public static final ModConfigSpec.BooleanValue ENABLE_NATIVE_IMAGE_DIAGNOSTICS;
    public static final ModConfigSpec.IntValue NATIVE_IMAGE_DIAG_MAX_SITES;
    public static final ModConfigSpec.BooleanValue NATIVE_IMAGE_DIAG_SHALLOW;
    public static final ModConfigSpec.BooleanValue ENABLE_ITEM_ENCHANTMENTS_OPT;
    public static final ModConfigSpec.IntValue ITEM_ENCHANTMENTS_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_VOXEL_SHAPE_OPT;
    public static final ModConfigSpec.IntValue VOXEL_SHAPE_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.IntValue VOXEL_SHAPE_COORD_LIST_MAX_LENGTH;
    public static final ModConfigSpec.IntValue VOXEL_SHAPE_DOUBLE_LIST_MAX_ENTRIES;
    public static final ModConfigSpec.IntValue DISCRETE_SHAPE_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue CUBE_SHAPE_INTERN_ENABLED;
    public static final ModConfigSpec.BooleanValue ENABLE_JEI_PARALLEL_FILTER;
    public static final ModConfigSpec.BooleanValue JEI_LAZY_TOOLTIP_INDEX;
    public static final ModConfigSpec.BooleanValue JEI_SKIP_TOOLTIP_INDEX;
    public static final ModConfigSpec.IntValue JEI_DEFERRED_SNAPSHOT_TTL_SECONDS;
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
    public static final ModConfigSpec.IntValue DATA_COMPONENT_PATCH_MAX_KEY_PAYLOAD_BYTES;
    public static final ModConfigSpec.BooleanValue ENABLE_PALETTED_CONTAINER_OPT;
    public static final ModConfigSpec.BooleanValue ENABLE_RESOURCE_LOCATION_OPT;
    public static final ModConfigSpec.IntValue RESOURCE_LOCATION_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.IntValue RESOURCE_LOCATION_NAMESPACE_MAX_ENTRIES;
    public static final ModConfigSpec.BooleanValue RESOURCE_LOCATION_CONSERVATIVE_CACHE;
    public static final ModConfigSpec.BooleanValue ENABLE_CLIMATE_PARAMETER_OPT;
    public static final ModConfigSpec.IntValue CLIMATE_PARAMETER_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.IntValue CLIMATE_PARAMETER_ARRAY_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_SPRITE_MIPMAP_RELEASE;
    public static final ModConfigSpec.BooleanValue ENABLE_BAKED_QUAD_VERTEX_DEDUP;
    public static final ModConfigSpec.IntValue BAKED_QUAD_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.IntValue BAKED_QUAD_MAX_QUAD_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_TAG_ENTRY_OPT;
    public static final ModConfigSpec.IntValue TAG_ENTRY_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_MOB_AI_THROTTLING;
    public static final ModConfigSpec.IntValue MOB_AI_THROTTLE_DISTANCE;
    public static final ModConfigSpec.IntValue MOB_AI_THROTTLE_FAR_DISTANCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOB_AI_THROTTLE_WHITELIST;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_MERGE_OPT;
    public static final ModConfigSpec.DoubleValue ITEM_MERGE_RADIUS;
    public static final ModConfigSpec.DoubleValue XP_MERGE_RADIUS;
    public static final ModConfigSpec.BooleanValue ENABLE_PLAIN_STYLE_INTERNING;
    public static final ModConfigSpec.IntValue PLAIN_STYLE_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_TAG_AND_RESOURCE_KEY_INTERNING;
    public static final ModConfigSpec.IntValue TAG_KEY_MAX_CACHE_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_FONT_NATIVE_IMAGE_RELEASE;
    public static final ModConfigSpec.BooleanValue ENABLE_RELOAD_ARTIFACT_AUDIT;
    public static final ModConfigSpec.BooleanValue ENABLE_MODEL_BAKERY_CLEANUP;

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

        SPELL_CONTAINER_STRONG_CACHE_MAX_ENTRIES = builder
                .comment("Module 3: Maximum entries in each strong SpellData / SpellSlot / empty-container cache (formerly a hard-coded 8192). Applied on config reload.")
                .defineInRange("spellContainerStrongCacheMaxEntries", 8192, 0, 262144);

        SPELL_CONTAINER_CACHE_MAX_ENTRIES = builder
                .comment("Module 3: Maximum entries in the weak SpellContainer instance cache (formerly a hard-coded 32768). Applied on config reload.")
                .defineInRange("spellContainerCacheMaxEntries", 32768, 0, 262144);

        SPELL_CONTAINER_EMPTY_SLOT_MAX_LENGTH = builder
                .comment("Module 3: Longest SpellSlot[] for which an empty array is cached (formerly a hard-coded 64). Longer arrays are always allocated uncached at full length.")
                .defineInRange("spellContainerEmptySlotMaxLength", 64, 1, 1024);

        ENABLE_IRIS_SHADER_CACHE_PURGE = builder
                .comment("Module 4: Purge Iris / glsl_transformer AST and token caches after shader pack load and expose purge command. Requires restart (mixin toggle read at startup).")
                .define("enableIrisShaderCachePurge", true);

        IRIS_AUTO_PURGE_DELAY_SECONDS = builder
                .comment("Delay in seconds after Iris shader pipeline preparation before automatically purging glsl_transformer caches (0 disables auto-purge, manual command still works).")
                .defineInRange("irisAutoPurgeDelaySeconds", 30, 0, 600);

        ENABLE_NATIVE_IMAGE_DIAGNOSTICS = builder
                .comment("Module 5: Track NativeImage allocation stack traces and close() calls for /atmmemopt diag nativeimage. Default OFF due to stack-capture overhead. Requires restart (mixin toggle read at startup).")
                .define("enableNativeImageDiagnostics", false);

        NATIVE_IMAGE_DIAG_MAX_SITES = builder
                .comment("Module 5: Maximum number of distinct allocation sites kept in the NativeImage diagnostic map. Additional sites are dropped and counted (0 disables site tracking). Applied on config reload.")
                .defineInRange("nativeImageDiagMaxSites", 512, 0, 65536);

        NATIVE_IMAGE_DIAG_SHALLOW = builder
                .comment("Module 5: When true, skip the StackWalker walk and record all NativeImage allocations under a single '<shallow>' site. Applied on config reload.")
                .define("nativeImageDiagShallow", false);

        ENABLE_ITEM_ENCHANTMENTS_OPT = builder
                .comment("Module 6: Intern immutable ItemEnchantments instances and unify empty instances to ItemEnchantments.EMPTY. Requires restart (mixin toggle read at startup).")
                .define("enableItemEnchantmentsOpt", true);

        ITEM_ENCHANTMENTS_MAX_CACHE_ENTRIES = builder
                .comment("Module 6: Maximum entries in the ItemEnchantments weak intern cache (0 disables non-empty interning while keeping empty unification).")
                .defineInRange("itemEnchantmentsMaxCacheEntries", 32768, 0, 262144);

        ENABLE_VOXEL_SHAPE_OPT = builder
                .comment("Module 7: Deduplicate immutable ArrayVoxelShape DoubleArrayList coordinates and identical VoxelShape instances. Requires restart (mixin toggle read at startup).")
                .define("enableVoxelShapeOpt", true);

        VOXEL_SHAPE_MAX_CACHE_ENTRIES = builder
                .comment("Module 7: Maximum entries in VoxelShape intern cache to avoid CHM/key bloat from rare shapes (0 disables VoxelShape body caching while keeping DoubleArrayList deduplication).")
                .defineInRange("voxelShapeMaxCacheEntries", 32768, 0, 262144);

        VOXEL_SHAPE_COORD_LIST_MAX_LENGTH = builder
                .comment("Module 7: Coordinate DoubleList arrays longer than this are returned uncached (bounds the size of each cached double[] value).")
                .defineInRange("voxelShapeCoordListMaxLength", 4096, 2, 262144);

        VOXEL_SHAPE_DOUBLE_LIST_MAX_ENTRIES = builder
                .comment("Module 7: Maximum entries in the strong DoubleArrayList coordinate cache (formerly a hard-coded 16384). This cache is also cleared on purge/reload.")
                .defineInRange("voxelShapeDoubleListMaxEntries", 16384, 0, 262144);

        DISCRETE_SHAPE_MAX_CACHE_ENTRIES = builder
                .comment("Module 7: Maximum entries in the BitSetDiscreteVoxelShape weak cache (formerly a hard-coded 16384).")
                .defineInRange("discreteShapeMaxCacheEntries", 16384, 0, 262144);

        CUBE_SHAPE_INTERN_ENABLED = builder
                .comment("Module 7: Also intern immutable CubeVoxelShape instances by their six bounds in a bounded weak cache (majority vanilla box()/join path).")
                .define("cubeShapeInternEnabled", true);

        ENABLE_JEI_PARALLEL_FILTER = builder
                .comment("Module 8a: Parallelize JEI ElementSearch index building (keeps TOOLTIPS/COLORS/CREATIVE_TABS on main thread). Requires restart (mixin toggle read at startup).")
                .define("enableJeiParallelFilter", true);

        JEI_LAZY_TOOLTIP_INDEX = builder
                .comment("Module 8a: Defer building JEI's heavy TOOLTIPS index (15.9s) until the first '$' tooltip search is performed. Only effective when enableJeiParallelFilter=true (the JEI mixins are not applied otherwise); no restart needed for this sub-flag itself.")
                .define("jeiLazyTooltipIndex", true);

        JEI_SKIP_TOOLTIP_INDEX = builder
                .comment("Module 8a (optional): Permanently skip building JEI's $tooltip search index. Only effective when enableJeiParallelFilter=true (the JEI mixins are not applied otherwise); no restart needed for this sub-flag itself.")
                .define("jeiSkipTooltipIndex", false);

        JEI_DEFERRED_SNAPSHOT_TTL_SECONDS = builder
                .comment("Module 8a: If a deferred TOOLTIPS element snapshot has not been consumed by a '$' search within this many seconds, discard it lazily on the next JEI search/add call (0 disables the TTL). A later '$' search rebuilds the index from JEI's live element view. Requires enableJeiParallelFilter=true and enableJeiLazyTooltipIndex=true to have an effect.")
                .defineInRange("jeiDeferredSnapshotTtlSeconds", 0, 0, 3600);

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

        DATA_COMPONENT_PATCH_MAX_KEY_PAYLOAD_BYTES = builder
                .comment("Module 10: Maximum shallow estimated bytes of component payload retained by a cache key. Patches with larger (NBT/collection/map/string/unknown-heavy) payloads are not cached; the key payload is counted in status.")
                .defineInRange("dataComponentPatchMaxKeyPayloadBytes", 4096, 0, 1048576);

        ENABLE_PALETTED_CONTAINER_OPT = builder
                .comment("Module 11: Replace per-PalettedContainer ThreadingDetector (Semaphore + ReentrantLock) with a shared no-op detector to save ~25-30MB and lock overhead. Note: disables vanilla crash-on-concurrent-chunk-access detection; disable this if debugging chunk corruption with async chunk mods.")
                .define("enablePalettedContainerOpt", true);

        ENABLE_RESOURCE_LOCATION_OPT = builder
                .comment("Module 12: Intern ResourceLocation namespaces and deduplicate identical ResourceLocation instances within a bounded weak cache.")
                .define("enableResourceLocationOpt", true);

        RESOURCE_LOCATION_MAX_CACHE_ENTRIES = builder
                .comment("Module 12: Maximum entries in the ResourceLocation weak intern cache (0 disables instance caching while keeping namespace string interning).")
                .defineInRange("resourceLocationMaxCacheEntries", 131072, 0, 524288);

        RESOURCE_LOCATION_NAMESPACE_MAX_ENTRIES = builder
                .comment("Module 12: Maximum entries in the strong namespace String cache (formerly a hard-coded 4096). The cache is cleared on purge/reload.")
                .defineInRange("resourceLocationNamespaceMaxEntries", 4096, 0, 262144);

        RESOURCE_LOCATION_CONSERVATIVE_CACHE = builder
                .comment("Module 12: When true, the effective ResourceLocation instance cache cap is HALVED at config refresh time (e.g. 131072 -> 65536); namespace interning is unaffected. Lower memory, fewer deduplications.")
                .define("resourceLocationConservativeCache", false);

        ENABLE_CLIMATE_PARAMETER_OPT = builder
                .comment("Module 13: Intern immutable Biome Climate.Parameter instances.")
                .define("enableClimateParameterOpt", true);

        CLIMATE_PARAMETER_MAX_CACHE_ENTRIES = builder
                .comment("Module 13/18: Maximum entries in the Climate.Parameter weak cache (formerly a hard-coded 32768). Applied on config reload.")
                .defineInRange("climateParameterMaxCacheEntries", 32768, 0, 262144);

        CLIMATE_PARAMETER_ARRAY_MAX_CACHE_ENTRIES = builder
                .comment("Module 13/18: Maximum entries in the Climate.Parameter[] weak cache (formerly a hard-coded 32768). Applied on config reload.")
                .defineInRange("climateParameterArrayMaxCacheEntries", 32768, 0, 262144);

        ENABLE_SPRITE_MIPMAP_RELEASE = builder
                .comment("Module 14: Release mipmap NativeImage levels (1..N) of static SpriteContents after GPU atlas upload (keeps originalImage level 0 intact).")
                .define("enableSpriteMipmapRelease", true);

        ENABLE_BAKED_QUAD_VERTEX_DEDUP = builder
                .comment("Module 15: Deduplicate identical BakedQuad vertex int[] arrays within a bounded weak cache.")
                .define("enableBakedQuadVertexDedup", true);

        BAKED_QUAD_MAX_CACHE_ENTRIES = builder
                .comment("Module 15: Maximum entries in the BakedQuad vertex int[] weak intern cache. Kept separate from the quad-body cap.")
                .defineInRange("bakedQuadMaxCacheEntries", 65536, 0, 262144);

        BAKED_QUAD_MAX_QUAD_ENTRIES = builder
                .comment("Module 17: Maximum entries in the BakedQuad instance weak intern cache (independent of the vertex cache cap).")
                .defineInRange("bakedQuadMaxQuadEntries", 65536, 0, 262144);

        ENABLE_TAG_ENTRY_OPT = builder
                .comment("Module 16: Intern immutable TagEntry instances across datapack tags.")
                .define("enableTagEntryOpt", true);

        TAG_ENTRY_MAX_CACHE_ENTRIES = builder
                .comment("Module 16: Maximum entries in the TagEntry weak cache (formerly a hard-coded 65536). Applied on config reload.")
                .defineInRange("tagEntryMaxCacheEntries", 65536, 0, 262144);

        ENABLE_MOB_AI_THROTTLING = builder
                .comment("Module 20: Throttle Mob#serverAiStep() for distant non-critical mobs to reduce server/integrated TPS load.")
                .define("enableMobAiThrottling", true);

        MOB_AI_THROTTLE_DISTANCE = builder
                .comment("Module 20: Distance (in blocks) beyond which non-whitelisted mob AI runs every 2 ticks.")
                .defineInRange("mobAiThrottleDistance", 48, 16, 256);

        MOB_AI_THROTTLE_FAR_DISTANCE = builder
                .comment("Module 20: Distance (in blocks) beyond which non-whitelisted mob AI runs every 4 ticks.")
                .defineInRange("mobAiThrottleFarDistance", 64, 24, 256);

        MOB_AI_THROTTLE_WHITELIST = builder
                .comment("Module 20: Class name substrings excluded from distant mob AI throttling (bosses, villagers, companions, etc.).")
                .defineListAllowEmpty(
                        "mobAiThrottleWhitelist",
                        List.of(
                                "Boss",
                                "Dragon",
                                "Wither",
                                "cataclysm",
                                "irons_spellbooks",
                                "twilightforest.entity.boss",
                                "minecolonies"
                        ),
                        o -> o instanceof String
                );

        ENABLE_ENTITY_MERGE_OPT = builder
                .comment("Module 21: Expand ItemEntity and ExperienceOrb merge search radii to merge dense drops faster.")
                .define("enableEntityMergeOpt", true);

        ITEM_MERGE_RADIUS = builder
                .comment("Module 21: Horizontal merge search radius (in blocks) for dropped ItemEntity (vanilla default is 0.5).")
                .defineInRange("itemMergeRadius", 1.5D, 0.5D, 6.0D);

        XP_MERGE_RADIUS = builder
                .comment("Module 21: Merge search radius (in blocks) for ExperienceOrb (vanilla default is 0.5).")
                .defineInRange("xpMergeRadius", 1.5D, 0.5D, 6.0D);

        ENABLE_PLAIN_STYLE_INTERNING = builder
                .comment("Module 22: Intern plain Style instances (no click/hover event, font or insertion) from Style codec/creation paths. Exact net.minecraft.network.chat.Style class only. Requires restart (mixin toggle read at startup).")
                .define("enablePlainStyleInterning", true);

        PLAIN_STYLE_MAX_CACHE_ENTRIES = builder
                .comment("Module 22: Maximum entries in the plain Style weak intern cache (0 disables non-empty interning while keeping the Style.EMPTY fast path). Applied on config reload.")
                .defineInRange("plainStyleMaxCacheEntries", 65536, 0, 262144);

        ENABLE_TAG_AND_RESOURCE_KEY_INTERNING = builder
                .comment("Module 23: Intern TagKey / ResourceKey instances from their static factories and codecs using canonical (registry, location) keys. Exact classes only; no dynamic-registry Holder objects are used as keys. Requires restart (mixin toggle read at startup).")
                .define("enableTagAndResourceKeyInterning", true);

        TAG_KEY_MAX_CACHE_ENTRIES = builder
                .comment("Module 23: Maximum entries in EACH of the two weak intern caches (TagKey and ResourceKey); one cap is deliberately shared by both caches. Applied on config reload.")
                .defineInRange("tagKeyMaxCacheEntries", 32768, 0, 262144);

        ENABLE_FONT_NATIVE_IMAGE_RELEASE = builder
                .comment("Module 24: DIAGNOSTICS ONLY in this stage. Vanilla BitmapProvider retains and reuses each glyph NativeImage after upload (Glyph#upload and isColored), so no post-upload close() is performed even when this is true; counters only. Requires restart (mixin toggle read at startup).")
                .define("enableFontNativeImageRelease", false);

        ENABLE_RELOAD_ARTIFACT_AUDIT = builder
                .comment("Module 25: DIAGNOSTICS ONLY. Record per-listener heap-used deltas (Runtime before/after each listener's apply task) in addition to M8c timings. Does not clear or modify recipe/reload state; requires enableReloadProfiler=true and a restart.")
                .define("enableReloadArtifactAudit", false);

        ENABLE_MODEL_BAKERY_CLEANUP = builder
                .comment("Module 26: AUDIT ONLY - cleanup is NOT implemented in this stage. Counts ModelBakery staging maps still reachable after a client model reload; never clears or modifies ModelBakery state. Requires restart (mixin toggle read at startup).")
                .define("enableModelBakeryCleanup", false);

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
