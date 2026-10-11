package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client effect handler substituting step acoustics with high-pitched duck
 * squeaks
 * and jump ascents with cartoon spring sound effects.
 */
public final class RubberWorldClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;

    /**
     * Evaluates whether rubber acoustics are actively enabled on the local client.
     *
     * @return True if rubber sound effect is active
     */
    public static boolean isActive() {
        return RubberWorldClientEffect.active;
    }

    /**
     * Plays a squeaking rubber duck sound effect at the player coordinate with
     * randomized pitch.
     *
     * @param player Local client player instance
     */
    public static void playRubberStepSound(PlayerEntity player) {
        float pitch = 1.5F + ThreadLocalRandom.current().nextFloat() * 0.5F;
        player.getWorld().playSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_CHICKEN_STEP,
                SoundCategory.PLAYERS,
                0.45F,
                pitch,
                false);
    }

    /**
     * Plays a bouncy cartoon spring sound effect upon jumping.
     *
     * @param player Local client player instance
     */
    public static void playRubberJumpSound(PlayerEntity player) {
        float pitch = 1.6F + ThreadLocalRandom.current().nextFloat() * 0.3F;
        player.getWorld().playSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_SLIME_JUMP,
                SoundCategory.PLAYERS,
                0.65F,
                pitch,
                false);
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        RubberWorldClientEffect.active = true;
    }

    @Override
    public void onStop(MinecraftClient client) {
        RubberWorldClientEffect.active = false;
    }
}