package com.merry.merryevents.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor interface exposing internal shader pipeline compilation in GameRenderer.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    /**
     * Dispatches internal pipeline rebuild for a post-effect processor JSON.
     *
     * @param id Identifier of the shader pipeline asset
     */
    @Invoker("loadPostProcessor")
    void merryevents$loadPostProcessor(Identifier id);
}