package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.BouncingHamsterClientEffect;
import com.merry.merryevents.client.effect.UncontrolledRunClientEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting keyboard input evaluation to enforce continuous jumping
 * and autonomous sprinting vectors.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    /**
     * Intercepts input tick completion to override movement direction vectors and
     * jumping status.
     *
     * @param slowDown       Whether movement speed is attenuated
     * @param slowDownFactor Sneaking speed multiplier factor
     * @param ci             Callback info
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void merryevents$applyInvoluntaryInputOverrides(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (BouncingHamsterClientEffect.isActive()) {
            this.jumping = true;
        }

        if (UncontrolledRunClientEffect.isActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                float targetYaw = UncontrolledRunClientEffect.getTargetWorldYaw();
                float deltaAngle = (targetYaw - client.player.getYaw()) * MathHelper.RADIANS_PER_DEGREE;

                this.movementForward = MathHelper.cos(deltaAngle);
                this.movementSideways = -MathHelper.sin(deltaAngle);
                this.pressingForward = true;
                this.pressingBack = false;
                this.pressingLeft = false;
                this.pressingRight = false;
            }
        }
    }
}