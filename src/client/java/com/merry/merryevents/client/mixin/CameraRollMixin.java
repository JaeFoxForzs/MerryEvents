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

@Mixin(Camera.class)
public abstract class CameraRollMixin {
    @Shadow
    @Final
    private Quaternionf rotation;

    @Inject(method = "update", at = @At("TAIL"))
    private void merryevents$flipCameraRoll(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (UpsideDownClientEffect.isActive()) {
            this.rotation.rotateZ((float) Math.PI); // Переворачиваем обзор ровно на 180 градусов
        }
    }
}