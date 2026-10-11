package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;

/**
 * Client physics coordinator enforcing perpetual jump input.
 * Keeps an atomic state flag queried by input mixins to hold the jumping key
 * down.
 */
public final class BouncingHamsterClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;

    /**
     * Queries whether continuous jumping input is currently enforced.
     *
     * @return True if bouncing hamster effect is active
     */
    public static boolean isActive() {
        return BouncingHamsterClientEffect.active;
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        BouncingHamsterClientEffect.active = true;
    }

    @Override
    public void onStop(MinecraftClient client) {
        BouncingHamsterClientEffect.active = false;
    }
}