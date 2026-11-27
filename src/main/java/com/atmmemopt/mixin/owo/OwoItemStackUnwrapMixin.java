package com.atmmemopt.mixin.owo;

import com.atmmemopt.opt.owo.OwoDerivedOptHelper;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module 1: Runs after owo-lib's ItemStackMixin (priority 1100 > default 1000)
 * at the tail of ItemStack's primary constructor to unwrap DerivedComponentMap
 * for items that do not override deriveStackComponents.
 */
@Mixin(value = ItemStack.class, priority = 1100)
public class OwoItemStackUnwrapMixin {
    @Shadow
    @Final
    PatchedDataComponentMap components;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",
            at = @At("TAIL")
    )
    private void atmmemopt$unwrapUnusedDerivedMap(ItemLike item, int count, PatchedDataComponentMap components, CallbackInfo ci) {
        OwoDerivedOptHelper.unwrapIfUnused((ItemStack) (Object) this, this.components);
    }
}
