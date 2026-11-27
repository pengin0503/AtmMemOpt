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

/**
 * Controls conditional application of AtmMemOpt mixins based on both
 * target mod presence (LoadingModList) and user configuration flags.
 */
public class AtmMemOptMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AtmMemOptMixinPlugin");
    private static final String MIXIN_PACKAGE_PREFIX = "com.atmmemopt.mixin.";

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
            return isModPresent("owo") && AtmMemOptConfig.getEarlyBoolean("enableOwoDerivedMapOpt", true);
        }

        // Module 2: PatchedDataComponentMap optimization
        if (relative.startsWith("components.")) {
            return AtmMemOptConfig.getEarlyBoolean("enablePatchedComponentMapOpt", true);
        }

        // Module 3: Iron's Spellbooks SpellContainer sharing
        if (relative.startsWith("irons.")) {
            return isModPresent("irons_spellbooks") && AtmMemOptConfig.getEarlyBoolean("enableIronsSpellContainerOpt", true);
        }

        // Module 4: Iris / glsl_transformer cache purge
        if (relative.startsWith("iris.")) {
            return isModPresent("iris") && AtmMemOptConfig.getEarlyBoolean("enableIrisShaderCachePurge", true);
        }

        // Module 5: NativeImage diagnostics (default OFF)
        if (relative.startsWith("diag.")) {
            boolean enabled = AtmMemOptConfig.getEarlyBoolean("enableNativeImageDiagnostics", false);
            if (enabled && relative.equals("diag.NativeImageDiagMixin")) {
                com.atmmemopt.diag.NativeImageTracker.markMixinApplied();
            }
            return enabled;
        }

        // Module 6: ItemEnchantments interning
        if (relative.startsWith("enchant.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableItemEnchantmentsOpt", true);
        }

        // Module 7: VoxelShape / DoubleArrayList deduplication
        if (relative.startsWith("shapes.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableVoxelShapeOpt", true);
        }

        // Module 8: Startup & world-load optimizations
        if (relative.equals("startup.JeiElementSearchMixin") || relative.equals("startup.JeiElementPrefixParserInvoker")) {
            return isModPresent("jei") && AtmMemOptConfig.getEarlyBoolean("enableJeiParallelFilter", true);
        }
        if (relative.equals("startup.JeiStuffLootRecipesMixin")) {
            return isModPresent("jeistuff") && AtmMemOptConfig.getEarlyBoolean("enableJeiStuffSkipLootScan", true);
        }
        if (relative.equals("startup.SimpleReloadInstanceMixin")) {
            return AtmMemOptConfig.getEarlyBoolean("enableReloadProfiler", true);
        }

        // Module 9: Render CPU/GPU optimizations
        if (relative.equals("render.BlockEntityRenderDispatcherMixin")) {
            return AtmMemOptConfig.getEarlyBoolean("enableBerDistanceCulling", true);
        }
        if (relative.equals("render.MinecraftFpsLimitMixin")) {
            return AtmMemOptConfig.getEarlyBoolean("enableUnfocusedFpsLimit", true);
        }
        if (relative.equals("render.ParticleEngineMixin")) {
            return AtmMemOptConfig.getEarlyBoolean("enableParticleDistanceCulling", true);
        }
        if (relative.equals("render.EntityRenderDispatcherMixin")) {
            return AtmMemOptConfig.getEarlyBoolean("enableEntityDistanceCulling", true);
        }

        // Module 10: DataComponentPatch interning & CoW map sharing
        if (relative.startsWith("patch.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableDataComponentPatchOpt", true);
        }

        // Module 11: PalettedContainer ThreadingDetector optimization
        if (relative.startsWith("chunk.")) {
            return AtmMemOptConfig.getEarlyBoolean("enablePalettedContainerOpt", true);
        }

        // Module 12: ResourceLocation interning
        if (relative.startsWith("resloc.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableResourceLocationOpt", true);
        }

        // Module 13: Biome Climate.Parameter interning
        if (relative.startsWith("climate.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableClimateParameterOpt", true);
        }

        // Module 14: Static SpriteContents mipmap NativeImage release
        if (relative.startsWith("texture.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableSpriteMipmapRelease", true);
        }

        // Module 15: BakedQuad vertex int[] deduplication
        if (relative.startsWith("model.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableBakedQuadVertexDedup", true);
        }

        // Module 16: TagEntry interning
        if (relative.startsWith("tags.")) {
            return AtmMemOptConfig.getEarlyBoolean("enableTagEntryOpt", true);
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
