package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Client effect handler suppressing all container and inventory screen
 * instantiations.
 */
public final class ClosedInventoryClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;

    /**
     * Evaluates whether client inventory and container screens are forbidden from
     * opening.
     *
     * @return True if inventory lockout is active
     */
    public static boolean isActive() {
        return ClosedInventoryClientEffect.active;
    }

    /**
     * Rejects an open attempt by playing a latch sound and presenting an action bar
     * notice.
     *
     * @param client Active Minecraft client instance
     */
    public static void notifyLockout(MinecraftClient client) {
        if (client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_CHEST_LOCKED, 1.0F, 1.2F);
            client.player.sendMessage(
                    Text.translatable("event.merryevents.closed_inventory.blocked").formatted(Formatting.RED),
                    true);
        }
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        ClosedInventoryClientEffect.active = true;
        if (client.currentScreen instanceof HandledScreen<?>) {
            client.setScreen(null);
            ClosedInventoryClientEffect.notifyLockout(client);
        }
    }

    @Override
    public void onStop(MinecraftClient client) {
        ClosedInventoryClientEffect.active = false;
    }
}