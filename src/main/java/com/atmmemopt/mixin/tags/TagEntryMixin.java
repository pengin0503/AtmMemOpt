package com.atmmemopt.mixin.tags;

import com.atmmemopt.opt.tags.TagEntryOptHelper;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * Module 16: Interns immutable TagEntry instances created via CODEC and static factories.
 */
@Mixin(TagEntry.class)
public class TagEntryMixin {
    @Shadow
    @Final
    @Mutable
    public static Codec<TagEntry> CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void atmmemopt$wrapCodec(CallbackInfo ci) {
        CODEC = CODEC.xmap(TagEntryOptHelper::intern, Function.identity());
    }

    @Inject(method = "element", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internElement(ResourceLocation id, CallbackInfoReturnable<TagEntry> cir) {
        cir.setReturnValue(TagEntryOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(method = "optionalElement", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internOptionalElement(ResourceLocation id, CallbackInfoReturnable<TagEntry> cir) {
        cir.setReturnValue(TagEntryOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(method = "tag", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internTag(ResourceLocation id, CallbackInfoReturnable<TagEntry> cir) {
        cir.setReturnValue(TagEntryOptHelper.intern(cir.getReturnValue()));
    }

    @Inject(method = "optionalTag", at = @At("RETURN"), cancellable = true)
    private static void atmmemopt$internOptionalTag(ResourceLocation id, CallbackInfoReturnable<TagEntry> cir) {
        cir.setReturnValue(TagEntryOptHelper.intern(cir.getReturnValue()));
    }
}
