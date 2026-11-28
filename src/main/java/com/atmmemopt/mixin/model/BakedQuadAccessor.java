package com.atmmemopt.mixin.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Module 15: allows the FaceBakery RETURN hook to replace the vertex array only after
 * NeoForge's in-place QuadTransformers have finished mutating it. The old constructor-time
 * hook was removed because it handed out a canonical array that was subsequently mutated.
 */
@Mixin(BakedQuad.class)
public interface BakedQuadAccessor {
    @Accessor("vertices")
    int[] atmmemopt$getVertices();

    @Mutable
    @Accessor("vertices")
    void atmmemopt$setVertices(int[] vertices);
}
