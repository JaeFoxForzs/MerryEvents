package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

public final class StiffNeckClientEffect implements ClientChaosEffect {
    private final Random random = new Random();
    private boolean lockYaw = false;
    private boolean lockPitch = false;

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        if (random.nextBoolean()) {
            this.lockYaw = true;
            this.lockPitch = false;
        } else {
            this.lockYaw = false;
            this.lockPitch = true;
        }
    }

    @Override
    public void onStop(MinecraftClient client) {
        this.lockYaw = false;
        this.lockPitch = false;
    }

    @Override
    public boolean isYawLocked() {
        return this.lockYaw;
    }

    @Override
    public boolean isPitchLocked() {
        return this.lockPitch;
    }
}