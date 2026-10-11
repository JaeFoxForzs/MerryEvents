package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.UncontrolledRunClientEffect;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting client player movement ticking to maintain continuous
 * sprinting during UncontrolledRun events.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMovementMixin {

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