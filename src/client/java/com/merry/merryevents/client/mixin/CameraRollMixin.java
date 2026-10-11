package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.UpsideDownClientEffect;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting camera orientation updates to apply roll inversion during
 * active UpsideDown events.
 */
@Mixin(Camera.class)
public abstract class CameraRollMixin {
    @Shadow
    @Final
    private Quaternionf rotation;

    /**
     * Intercepts camera rotation updates to apply a 180-degree Z-axis roll
     * transformation.
     *
     * @param area          World block view
     * @param focusedEntity Camera focus entity
     * @param thirdPerson   Third-person perspective flag
     * @param inverseView   Inverted perspective flag
     * @param tickDelta     Partial frame delta
     * @param ci            Callback info
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void merryevents$flipCameraRoll(BlockView area, Entity focusedEntity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (UpsideDownClientEffect.isActive()) {
            this.rotation.rotateZ((float) Math.PI);
        }
    }
}