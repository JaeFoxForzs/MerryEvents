package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.ClientEventManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin intercepting player look angle deltas to enforce pitch and yaw locks.
 */
@Mixin(Entity.class)
public abstract class CameraEntityMixin {
    /**
     * Intercepts yaw delta calculations to neutralize horizontal camera panning
     * when locked.
     *
     * @param cursorDeltaX Horizontal mouse movement delta
     * @return Zero if yaw is locked on the client player, otherwise original
     *         cursorDeltaX
     */
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double merryevents$lockYaw(double cursorDeltaX) {
        if ((Object) this == MinecraftClient.getInstance().player && ClientEventManager.isYawLocked()) {
            return 0.0;
        }
        return cursorDeltaX;
    }

    /**
     * Intercepts pitch delta calculations to neutralize vertical camera panning
     * when locked.
     *
     * @param cursorDeltaY Vertical mouse movement delta
     * @return Zero if pitch is locked on the client player, otherwise original
     *         cursorDeltaY
     */
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double merryevents$lockPitch(double cursorDeltaY) {
        if ((Object) this == MinecraftClient.getInstance().player && ClientEventManager.isPitchLocked()) {
            return 0.0;
        }
        return cursorDeltaY;
    }
}