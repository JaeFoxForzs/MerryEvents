package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.EpilepsyClientEffect;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin intercepting camera field of view evaluations to project optical
 * epilepsy shockwave offsets.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /**
     * Intercepts FOV projection calculations to inject elastic sensory shock
     * offsets during epilepsy snaps.
     *
     * @param camera      Active rendering camera
     * @param tickDelta   Partial frame delta
     * @param changingFov Dynamic FOV modifier flag
     * @param cir         Callback returnable providing calculated FOV
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void merryevents$applyEpilepsyFovShock(Camera camera, float tickDelta, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        if (EpilepsyClientEffect.isActive() && changingFov) {
            double originalFov = cir.getReturnValueD();
            cir.setReturnValue(originalFov + EpilepsyClientEffect.getFovOffset(tickDelta));
        }
    }
}