package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

public final class OrbitalStrikeClientEffect implements ClientChaosEffect {
    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        // Звук тревоги в начале обстрела
        if (client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.5F, 0.6F);
        }
    }

    @Override
    public void onStop(MinecraftClient client) {
    }
}