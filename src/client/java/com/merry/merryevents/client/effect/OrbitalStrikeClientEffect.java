package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

/**
 * Client visual and auditory effect driver for celestial strike events.
 * Dispatches beacon deactivation acoustics upon event initiation.
 */
public final class OrbitalStrikeClientEffect implements ClientChaosEffect {
    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        if (client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.5F, 0.6F);
        }
    }

    @Override
    public void onStop(MinecraftClient client) {
    }
}