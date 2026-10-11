package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;

/**
 * Client visual coordinator tracking the active state of camera roll inversion.
 */
public final class UpsideDownClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;

    /**
     * Queries whether world camera orientation should currently be flipped.
     *
     * @return True if upside down camera effect is active
     */
    public static boolean isActive() {
        return UpsideDownClientEffect.active;
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        UpsideDownClientEffect.active = true;
    }

    @Override
    public void onStop(MinecraftClient client) {
        UpsideDownClientEffect.active = false;
    }
}