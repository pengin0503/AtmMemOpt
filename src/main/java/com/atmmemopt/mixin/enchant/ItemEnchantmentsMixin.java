package com.atmmemopt.mixin.enchant;

import com.atmmemopt.opt.enchant.ItemEnchantmentsOptHelper;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ItemEnchantments.class)
public class ItemEnchantmentsMixin {
    @Shadow
    @Final
    @Mutable
    public static Codec<ItemEnchantments> CODEC;

    @Shadow
    @Final
    @Mutable
    public static StreamCodec<RegistryFriendlyByteBuf, ItemEnchantments> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void atmmemopt$wrapCodecs(CallbackInfo ci) {
        CODEC = CODEC.xmap(ItemEnchantmentsOptHelper::intern, Function.identity());
        STREAM_CODEC = STREAM_CODEC.map(ItemEnchantmentsOptHelper::intern, Function.identity());
    }

    @Inject(method = "withTooltip", at = @At("RETURN"), cancellable = true)
    private void atmmemopt$internWithTooltip(boolean showInTooltip, CallbackInfoReturnable<ItemEnchantments> cir) {
        cir.setReturnValue(ItemEnchantmentsOptHelper.intern(cir.getReturnValue()));
    }
}
