package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.ClientEventManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class CameraEntityMixin {
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double merryevents$lockYaw(double cursorDeltaX) {
        if ((Object) this == MinecraftClient.getInstance().player && ClientEventManager.isYawLocked()) {
            return 0.0;
        }
        return cursorDeltaX;
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double merryevents$lockPitch(double cursorDeltaY) {
        if ((Object) this == MinecraftClient.getInstance().player && ClientEventManager.isPitchLocked()) {
            return 0.0;
        }
        return cursorDeltaY;
    }
}