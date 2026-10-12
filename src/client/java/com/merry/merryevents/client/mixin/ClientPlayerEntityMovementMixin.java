package com.merry.merryevents.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merry.merryevents.client.effect.UncontrolledRunClientEffect;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting client player movement ticking to maintain continuous,
 * smooth sprinting
 * during UncontrolledRun events without 20Hz FOV oscillation.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMovementMixin {

    /**
     * Prevents vanilla tickMovement logic from cancelling sprint when running
     * perpendicular to look direction.
     *
     * @param instance Target player input instance
     * @param original Original hasForwardMovement invocation
     * @return True if uncontrolled run is active, otherwise nominal input check
     */
    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;hasForwardMovement()Z"))
    private boolean merryevents$bypassSprintAngleCheck(Input instance, Operation<Boolean> original) {
        if (UncontrolledRunClientEffect.isActive()) {
            return true;
        }
        return original.call(instance);
    }

    /**
     * Enforces continuous sprinting state at the conclusion of client movement
     * ticking.
     *
     * @param ci Callback info
     */
    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void merryevents$enforceAutonomousSprint(CallbackInfo ci) {
        if (UncontrolledRunClientEffect.isActive()) {
            ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
            if (!player.isSneaking() && !player.isTouchingWater()) {
                player.setSprinting(true);
            }
        }
    }
}