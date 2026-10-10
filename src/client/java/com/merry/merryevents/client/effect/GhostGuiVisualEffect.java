package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;

public final class GhostGuiVisualEffect implements ClientChaosEffect {
    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
    }

    @Override
    public void onStop(MinecraftClient client) {
    }

    @Override
    public boolean shouldHideHud() {
        return true;
    }
}