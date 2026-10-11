package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.RubberWorldClientEffect;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only mixin intercepting local player movement audio to inject rubber
 * footsteps and spring jumps.
 */
@Mixin(PlayerEntity.class)
public abstract class ClientPlayerSoundMixin {

    /**
     * Intercepts step sound playback to substitute rubber duck squeaks for the
     * client player.
     *
     * @param pos   Stepping block position
     * @param state Stepping block state
     * @param ci    Callback info
     */
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void merryevents$replaceRubberStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && RubberWorldClientEffect.isActive()
                && ci.isCancellable()) {
            ci.cancel();
            RubberWorldClientEffect.playRubberStepSound((PlayerEntity) (Object) this);
        }
    }

    /**
     * Intercepts jump initiation on the client player to append cartoon spring
     * audio effects.
     *
     * @param ci Callback info
     */
    @Inject(method = "jump", at = @At("TAIL"))
    private void merryevents$playRubberJumpSound(CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && RubberWorldClientEffect.isActive()) {
            RubberWorldClientEffect.playRubberJumpSound((PlayerEntity) (Object) this);
        }
    }
}