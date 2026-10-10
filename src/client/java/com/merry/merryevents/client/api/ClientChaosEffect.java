package com.merry.merryevents.client.api;

import net.minecraft.client.MinecraftClient;

public interface ClientChaosEffect {
    void onStart(MinecraftClient client, int initialRemainingTicks);

    void onStop(MinecraftClient client);

    default void onTick(MinecraftClient client, int remainingTicks) {
    }

    default boolean shouldHideHud() {
        return false;
    }
}