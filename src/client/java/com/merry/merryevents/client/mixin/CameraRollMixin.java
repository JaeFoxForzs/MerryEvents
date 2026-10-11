package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.EpilepsyClientEffect;
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
 * Mixin intercepting camera orientation updates to apply roll inversion and
 * elastic epilepsy spasms.
 */
@Mixin(Camera.class)
public abstract class CameraRollMixin {
    @Shadow
    @Final
    private Quaternionf rotation;

    /**
     * Intercepts camera rotation updates to apply 180-degree roll inversion and
     * elastic epilepsy spasms along the Z-axis.
     *
     * @param area          World block view
     * @param focusedEntity Camera focus entity
     * @param thirdPerson   Third-person perspective flag
     * @param inverseView   Inverted perspective flag
     * @param tickDelta     Partial frame delta
     * @param ci            Callback info
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void merryevents$applyCameraRollTransformations(BlockView area, Entity focusedEntity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (UpsideDownClientEffect.isActive()) {
            this.rotation.rotateZ((float) Math.PI);
        }

        if (EpilepsyClientEffect.isActive()) {
            float roll = EpilepsyClientEffect.getCameraRoll(tickDelta);
            if (roll != 0.0F) {
                this.rotation.rotateZ(roll * (float) (Math.PI / 180.0));
            }
        }
    }
}