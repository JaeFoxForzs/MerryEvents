package com.merry.merryevents.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merry.merryevents.client.weather.RainColorManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin intercepting weather precipitation rendering to dynamically tint rain
 * and snow geometry across vanilla, Sodium, and shader pipelines.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    /**
     * Intercepts vertex color emission during weather rendering to apply active
     * event color tints.
     * Modifies RGB color channels while strictly preserving vanilla alpha depth
     * attenuation.
     *
     * @param instance Target vertex consumer receiving quad data
     * @param red      Nominal red vertex channel
     * @param green    Nominal green vertex channel
     * @param blue     Nominal blue vertex channel
     * @param alpha    Interpolated precipitation opacity
     * @param original Original color method invocation wrapper
     * @return Mutated vertex consumer for chaining
     */
    @WrapOperation(method = "renderWeather", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"))
    private VertexConsumer merryevents$tintPrecipitationVertices(
            VertexConsumer instance,
            float red,
            float green,
            float blue,
            float alpha,
            Operation<VertexConsumer> original) {

        if (RainColorManager.hasActiveRainTint()) {
            float tintedRed = red * RainColorManager.getTintRed();
            float tintedGreen = green * RainColorManager.getTintGreen();
            float tintedBlue = blue * RainColorManager.getTintBlue();
            return original.call(instance, tintedRed, tintedGreen, tintedBlue, alpha);
        }

        return original.call(instance, red, green, blue, alpha);
    }
}