package com.atmmemopt.mixin.enchant;

import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEnchantments.Mutable.class)
public class ItemEnchantmentsMutableMixin {
    @Inject(method = "toImmutable", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internToImmutable(CallbackInfoReturnable<ItemEnchantments> cir) {
        cir.setReturnValue(ItemEnchantmentsOptHelper.intern(cir.getReturnValue()));
    }
}
