package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;

public final class UpsideDownClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;

    public static boolean isActive() {
        return active;
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        active = true;
    }

    @Override
    public void onStop(MinecraftClient client) {
        active = false;
    }
}