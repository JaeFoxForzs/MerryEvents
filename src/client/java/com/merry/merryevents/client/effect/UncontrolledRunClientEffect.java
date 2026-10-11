package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client physics coordinator governing autonomous sprinting in randomized
 * directions.
 * Continuously recalculates movement vectors relative to player camera without
 * altering look direction.
 */
public final class UncontrolledRunClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;
    private static boolean registered = false;

    private static float targetWorldYaw = 0.0F;
    private static int directionChangeCooldown = 0;

    /**
     * Queries whether autonomous sprinting is actively running.
     *
     * @return True if uncontrolled run effect is active
     */
    public static boolean isActive() {
        return UncontrolledRunClientEffect.active;
    }

    /**
     * Resolves the current authoritative global yaw direction vector target.
     *
     * @return Current target direction angle in degrees
     */
    public static float getTargetWorldYaw() {
        return UncontrolledRunClientEffect.targetWorldYaw;
    }

    /**
     * Initializes client tick listeners updating direction targets and handling
     * obstacle collisions.
     */
    public static void init() {
        if (UncontrolledRunClientEffect.registered) {
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!UncontrolledRunClientEffect.active || client.player == null) {
                return;
            }

            if (--UncontrolledRunClientEffect.directionChangeCooldown <= 0 || client.player.horizontalCollision) {
                UncontrolledRunClientEffect.pickNewDirection();
            }
        });

        UncontrolledRunClientEffect.registered = true;
    }

    /**
     * Randomly selects a new heading direction and assigns randomized cooldown
     * ticks.
     */
    private static void pickNewDirection() {
        UncontrolledRunClientEffect.targetWorldYaw = ThreadLocalRandom.current().nextFloat() * 360.0F;
        UncontrolledRunClientEffect.directionChangeCooldown = ThreadLocalRandom.current().nextInt(50, 100);
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        UncontrolledRunClientEffect.active = true;
        UncontrolledRunClientEffect.pickNewDirection();
    }

    @Override
    public void onStop(MinecraftClient client) {
        UncontrolledRunClientEffect.active = false;
        UncontrolledRunClientEffect.directionChangeCooldown = 0;
    }
}