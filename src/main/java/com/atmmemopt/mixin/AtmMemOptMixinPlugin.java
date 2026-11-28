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
            return true;
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
        if (relative.equals("startup.JeiElementSearchMixin") || relative.equals("startup.JeiElementPrefixParserInvoker")) {
            return recordAndReturn("M8a", isModPresent("jei") && AtmMemOptConfig.getEarlyBoolean("enableJeiParallelFilter", true));
        }
        if (relative.equals("startup.JeiStuffLootRecipesMixin")) {
            return recordAndReturn("M8b", isModPresent("jeistuff") && AtmMemOptConfig.getEarlyBoolean("enableJeiStuffSkipLootScan", true));
        }
        if (relative.equals("startup.SimpleReloadInstanceMixin")) {
            return recordAndReturn("M8c", AtmMemOptConfig.getEarlyBoolean("enableReloadProfiler", true));
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

        return true;
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
    }
}
