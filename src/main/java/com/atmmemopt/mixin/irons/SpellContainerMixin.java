package com.atmmemopt.mixin.irons;

import com.atmmemopt.opt.irons.SpellContainerOptHelper;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.SpellContainer;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = SpellContainer.class, remap = false)
public class SpellContainerMixin {
    /**
     * Codec decode lambda (SpellContainer.CODEC).
     */
    @Inject(method = "lambda$static$4", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internCodecDecode(
            Integer maxSpells,
            Boolean spellWheel,
            Boolean mustEquip,
            Boolean improved,
            List<SpellSlot> slots,
            CallbackInfoReturnable<ISpellContainer> cir
    ) {
        cir.setReturnValue(SpellContainerOptHelper.internContainer(cir.getReturnValue()));
    }

    /**
     * StreamCodec decode lambda (SpellContainer.STREAM_CODEC).
     */
    @Inject(method = "lambda$static$7", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internStreamDecode(
            FriendlyByteBuf buf,
            CallbackInfoReturnable<ISpellContainer> cir
    ) {
        cir.setReturnValue(SpellContainerOptHelper.internContainer(cir.getReturnValue()));
    }
}
