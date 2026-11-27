package com.atmmemopt.mixin.irons;

import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ComponentRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ISpellContainer.class, remap = false)
public interface ISpellContainerMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void atmmemopt$sharedCreate(
            int maxSpells,
            boolean spellWheel,
            boolean mustEquip,
            CallbackInfoReturnable<ISpellContainer> cir
    ) {
        cir.setReturnValue(SpellContainerOptHelper.getEmptyContainer(maxSpells, spellWheel, mustEquip, false));
    }

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true)
    private static void atmmemopt$avoidAllocGetOrCreate(ItemStack stack, CallbackInfoReturnable<ISpellContainer> cir) {
        ISpellContainer existing = stack.get(ComponentRegistry.SPELL_CONTAINER);
        SpellContainerOptHelper.recordGetOrCreateAvoided();
        if (existing != null) {
            cir.setReturnValue(existing);
        } else {
            cir.setReturnValue(SpellContainerOptHelper.getEmptyContainer(1, false, false, false));
        }
    }
}
