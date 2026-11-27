package com.atmmemopt.mixin.startup;

import com.atmmemopt.AtmMemOpt;
import com.atmmemopt.opt.startup.ReloadProfiler;
import cy.jdkdigital.jeistuff.recipe.LootRecipe;
import cy.jdkdigital.jeistuff.recipe.LootRecipes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Module 8b: Skips JEIStuff's synchronous 2,595-loot-table scan on world join when enabled in config.
 */
@Mixin(value = LootRecipes.class, remap = false)
public class JeiStuffLootRecipesMixin {
    /** JVM-wide one-shot: the skip itself happens on every call, logging/counter do not. */
    private static final AtomicBoolean SKIP_REPORTED = new AtomicBoolean(false);

    @Inject(method = "getAllLootRecipes", at = @At("HEAD"), cancellable = true)
    private static void atmmemopt$skipLootScan(
            ServerLevel level,
            CallbackInfoReturnable<List<RecipeHolder<LootRecipe>>> cir
    ) {
        if (SKIP_REPORTED.compareAndSet(false, true)) {
            ReloadProfiler.recordJeiStuffLootSkipped();
            AtmMemOpt.LOGGER.info("[AtmMemOpt] Skipped JEIStuff LootRecipes scan (enableJeiStuffSkipLootScan=true).");
        }
        // Mutable empty list: callers that sort/mutate the result must not get UnsupportedOperationException.
        // The skip and its default-ON setting are intentional for this personal-environment mod.
        cir.setReturnValue(new ArrayList<>());
    }
}
