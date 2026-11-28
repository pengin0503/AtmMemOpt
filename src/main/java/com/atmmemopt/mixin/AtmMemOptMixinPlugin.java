package com.atmmemopt.mixin;

import com.atmmemopt.config.AtmMemOptConfig;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controls conditional application of AtmMemOpt mixins based on both
 * target mod presence (LoadingModList) and user configuration flags.
 */
public class AtmMemOptMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AtmMemOptMixinPlugin");
    private static final String MIXIN_PACKAGE_PREFIX = "com.atmmemopt.mixin.";
    private static final Set<String> APPLIED_MODULES = ConcurrentHashMap.newKeySet();

    public static boolean isModuleApplied(String moduleId) {
        return APPLIED_MODULES.contains(moduleId);
    }

    private static boolean recordAndReturn(String moduleId, boolean apply) {
        if (apply) {
            APPLIED_MODULES.add(moduleId);
        }
        return apply;
    }

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("AtmMemOptMixinPlugin loaded for package {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PACKAGE_PREFIX)) {
            // Fail closed: mixins outside our package are not part of this mod's config
            // gates, so applying them blindly would bypass every toggle below.
            LOGGER.warn("[AtmMemOpt] Mixin '{}' is outside the mod's mixin package; skipping it.", mixinClassName);
            return false;
        }
        String relative = mixinClassName.substring(MIXIN_PACKAGE_PREFIX.length());

        // Module 1: owo-lib DerivedComponentMap optimization
        if (relative.startsWith("owo.")) {
            return recordAndReturn("M1", isModPresent("owo") && AtmMemOptConfig.getEarlyBoolean("enableOwoDerivedMapOpt", true));
        }

        // Module 2: PatchedDataComponentMap optimization
        if (relative.startsWith("components.")) {
            return recordAndReturn("M2", AtmMemOptConfig.getEarlyBoolean("enablePatchedComponentMapOpt", true));
        }

        // Module 3: Iron's Spellbooks SpellContainer sharing
        if (relative.startsWith("irons.")) {
            return recordAndReturn("M3", isModPresent("irons_spellbooks") && AtmMemOptConfig.getEarlyBoolean("enableIronsSpellContainerOpt", true));
        }

        // Module 4: Iris / glsl_transformer cache purge
        if (relative.startsWith("iris.")) {
            return recordAndReturn("M4", isModPresent("iris") && AtmMemOptConfig.getEarlyBoolean("enableIrisShaderCachePurge", true));
        }

        // Module 5: NativeImage diagnostics (default OFF)
        if (relative.startsWith("diag.")) {
            boolean enabled = AtmMemOptConfig.getEarlyBoolean("enableNativeImageDiagnostics", false);
            if (enabled && relative.equals("diag.NativeImageDiagMixin")) {
                com.atmmemopt.diag.NativeImageTracker.markMixinApplied();
            }
            return recordAndReturn("M5", enabled);
        }

        // Module 6: ItemEnchantments interning
        if (relative.startsWith("enchant.")) {
            return recordAndReturn("M6", AtmMemOptConfig.getEarlyBoolean("enableItemEnchantmentsOpt", true));
        }

        // Module 7: VoxelShape / DoubleArrayList deduplication
        if (relative.startsWith("shapes.")) {
            return recordAndReturn("M7", AtmMemOptConfig.getEarlyBoolean("enableVoxelShapeOpt", true));
        }

        // Module 8: Startup & world-load optimizations
        if (relative.equals("startup.JeiElementSearchMixin")
                || relative.equals("startup.JeiElementPrefixParserInvoker")
                || relative.equals("startup.JeiIngredientFilterInvoker")
                || relative.equals("startup.JeiIngredientFilterRegistrationMixin")) {
            return recordAndReturn("M8a", isModPresent("jei") && AtmMemOptConfig.getEarlyBoolean("enableJeiParallelFilter", true));
        }
        if (relative.equals("startup.JeiStuffLootRecipesMixin")) {
            return recordAndReturn("M8b", isModPresent("jeistuff") && AtmMemOptConfig.getEarlyBoolean("enableJeiStuffSkipLootScan", true));
        }
        if (relative.equals("startup.SimpleReloadInstanceMixin")) {
            boolean reloadProfiler = AtmMemOptConfig.getEarlyBoolean("enableReloadProfiler", true);
            // M25 reuses this mixin's points; it is only recorded as applied when both its
            // own toggle and the M8c mixin gate are on (fail closed).
            if (reloadProfiler && AtmMemOptConfig.getEarlyBoolean("enableReloadArtifactAudit", false)) {
                recordAndReturn("M25", true);
            }
            return recordAndReturn("M8c", reloadProfiler);
        }

        // Module 9: Render CPU/GPU optimizations
        if (relative.equals("render.BlockEntityRenderDispatcherMixin")) {
            return recordAndReturn("M9a", AtmMemOptConfig.getEarlyBoolean("enableBerDistanceCulling", true));
        }
        if (relative.equals("render.MinecraftFpsLimitMixin")) {
            return recordAndReturn("M9b", AtmMemOptConfig.getEarlyBoolean("enableUnfocusedFpsLimit", true));
        }
        if (relative.equals("render.ParticleEngineMixin")) {
            return recordAndReturn("M9c", AtmMemOptConfig.getEarlyBoolean("enableParticleDistanceCulling", true));
        }
        if (relative.equals("render.EntityRenderDispatcherMixin")) {
            return recordAndReturn("M9d", AtmMemOptConfig.getEarlyBoolean("enableEntityDistanceCulling", true));
        }

        // Module 10: DataComponentPatch interning & CoW map sharing
        if (relative.startsWith("patch.")) {
            return recordAndReturn("M10", AtmMemOptConfig.getEarlyBoolean("enableDataComponentPatchOpt", true));
        }

        // Module 11: PalettedContainer ThreadingDetector optimization
        if (relative.startsWith("chunk.")) {
            return recordAndReturn("M11", AtmMemOptConfig.getEarlyBoolean("enablePalettedContainerOpt", true));
        }

        // Module 12: ResourceLocation interning
        if (relative.startsWith("resloc.")) {
            return recordAndReturn("M12", AtmMemOptConfig.getEarlyBoolean("enableResourceLocationOpt", true));
        }

        // Module 13: Biome Climate.Parameter interning
        if (relative.startsWith("climate.")) {
            return recordAndReturn("M13", AtmMemOptConfig.getEarlyBoolean("enableClimateParameterOpt", true));
        }

        // Module 14: Static SpriteContents mipmap NativeImage release
        if (relative.startsWith("texture.")) {
            return recordAndReturn("M14", AtmMemOptConfig.getEarlyBoolean("enableSpriteMipmapRelease", true));
        }

        // Module 15: BakedQuad vertex int[] deduplication
        if (relative.startsWith("model.")) {
            return recordAndReturn("M15", AtmMemOptConfig.getEarlyBoolean("enableBakedQuadVertexDedup", true));
        }

        // Module 16: TagEntry interning
        if (relative.startsWith("tags.")) {
            return recordAndReturn("M16", AtmMemOptConfig.getEarlyBoolean("enableTagEntryOpt", true));
        }

        // Module 20: Distant mob AI throttling
        if (relative.equals("tick.MobAiThrottleMixin")) {
            return recordAndReturn("M20", AtmMemOptConfig.getEarlyBoolean("enableMobAiThrottling", true));
        }

        // Module 21: ItemEntity / ExperienceOrb merge radius optimization
        if (relative.equals("tick.ItemEntityMergeMixin") || relative.equals("tick.ExperienceOrbMergeMixin")) {
            return recordAndReturn("M21", AtmMemOptConfig.getEarlyBoolean("enableEntityMergeOpt", true));
        }

        // Module 22: plain Style interning (Style#create factory path only, never setStyle)
        if (relative.startsWith("text.")) {
            return recordAndReturn("M22", AtmMemOptConfig.getEarlyBoolean("enablePlainStyleInterning", true));
        }

        // Module 23: TagKey / ResourceKey interning
        if (relative.startsWith("keys.")) {
            return recordAndReturn("M23", AtmMemOptConfig.getEarlyBoolean("enableTagAndResourceKeyInterning", true));
        }

        // Module 24: font glyph NativeImage diagnostics (default OFF; no release hook)
        if (relative.startsWith("font.")) {
            return recordAndReturn("M24", AtmMemOptConfig.getEarlyBoolean("enableFontNativeImageRelease", false));
        }

        // Module 26: ModelBakery staging audit (audit only, default OFF)
        if (relative.startsWith("bakery.")) {
            return recordAndReturn("M26", AtmMemOptConfig.getEarlyBoolean("enableModelBakeryCleanup", false));
        }

        // Fail closed: a mixin added later but not wired into a branch above must not be
        // silently applied without a config gate / mod-presence check.
        LOGGER.warn("[AtmMemOpt] Mixin '{}' has no matching config gate; skipping it.", mixinClassName);
        return false;
    }

    private static boolean isModPresent(String modId) {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // E3: SpellContainerMixin injects into compiler-generated lambda names. With
        // require = 0 a renamed lambda no longer crashes startup, so warn explicitly that
        // interning on that decode path is degraded (skipped).
        if ("com.atmmemopt.mixin.irons.SpellContainerMixin".equals(mixinClassName)) {
            boolean codecLambda = false;
            boolean streamLambda = false;
            for (var method : targetClass.methods) {
                if ("lambda$static$4".equals(method.name)) {
                    codecLambda = true;
                } else if ("lambda$static$7".equals(method.name)) {
                    streamLambda = true;
                }
            }
            if (!codecLambda || !streamLambda) {
                LOGGER.warn("[AtmMemOpt] Iron's SpellContainer codec lambda names changed "
                        + "(codec={}, stream={}); container interning is degraded (skipped) for those decode paths.",
                        codecLambda, streamLambda);
            }
        }
    }
}
