package com.merry.merryevents.client.mixin;

import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("loadPostProcessor")
    void merryevents$loadPostProcessor(Identifier id);

    @Accessor("postProcessor")
    PostEffectProcessor merryevents$getPostProcessor();
}