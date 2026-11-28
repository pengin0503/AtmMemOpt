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

/**
 * Module 8b: Skips JEIStuff's synchronous 2,595-loot-table scan on world join when enabled in config.
 */
@Mixin(value = LootRecipes.class, remap = false)
public class JeiStuffLootRecipesMixin {
    @Inject(method = "getAllLootRecipes", at = @At("HEAD"), cancellable = true)
    private static void atmmemopt$skipLootScan(
            ServerLevel level,
            CallbackInfoReturnable<List<RecipeHolder<LootRecipe>>> cir
    ) {
        ReloadProfiler.recordJeiStuffLootSkipped();
        AtmMemOpt.LOGGER.info("[AtmMemOpt] Skipped JEIStuff LootRecipes scan (enableJeiStuffSkipLootScan=true).");
        cir.setReturnValue(new ArrayList<>());
    }
}
