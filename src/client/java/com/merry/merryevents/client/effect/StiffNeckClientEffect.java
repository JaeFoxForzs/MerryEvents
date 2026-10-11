package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client effect handler randomly locking either yaw or pitch camera rotation.
 */
public final class StiffNeckClientEffect implements ClientChaosEffect {
    private boolean lockYaw = false;
    private boolean lockPitch = false;

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        if (ThreadLocalRandom.current().nextBoolean()) {
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