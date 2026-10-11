package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

/**
 * Client visual effect driver flattening living entities into 2D billboard
 * sprites.
 * Manages atomic active state evaluated during living entity model rendering.
 */
public final class FlatMobsClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;

    /**
     * Queries whether living entities should currently be rendered as 2D flat
     * billboards.
     *
     * @return True if flat mob rendering is active
     */
    public static boolean isActive() {
        return FlatMobsClientEffect.active;
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        FlatMobsClientEffect.active = true;
        if (client.player != null) {
            client.player.playSound(SoundEvents.UI_TOAST_IN, 1.0F, 1.8F);
        }
    }

    @Override
    public void onStop(MinecraftClient client) {
        FlatMobsClientEffect.active = false;
        if (client.player != null) {
            client.player.playSound(SoundEvents.UI_TOAST_OUT, 1.0F, 1.8F);
        }
    }
}