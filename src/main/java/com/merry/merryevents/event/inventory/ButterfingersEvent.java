package com.merry.merryevents.event.inventory;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific cognitive inventory disruption event belonging to EventCategory.INVENTORY.
 * Periodically swaps the active main-hand hotbar item with an arbitrary slot from the
 * player's main storage inventory grid (slots 9 through 35).
 */
public final class ButterfingersEvent implements ChaosEvent {

    private static final String TIMER_KEY = "butterfingers";
    private static final int SWAP_INTERVAL_TICKS = 20 * 20; // Exact 20 seconds cadence
    private static final int INVENTORY_STORAGE_START = 9;
    private static final int INVENTORY_STORAGE_END = 35;

    @Override
    public Identifier getId() {
        return EventIds.BUTTERFINGERS;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.INVENTORY;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        context.set(TIMER_KEY, SWAP_INTERVAL_TICKS);
        targetPlayer.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.8F, 1.4F);
        MerryEvents.LOGGER.info("[ButterfingersEvent] Cognitive inventory swap active for {}",
                targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        int timer = context.getInt(TIMER_KEY, SWAP_INTERVAL_TICKS) - 1;
        if (timer <= 0) {
            executeInventorySwap(targetPlayer);
            context.set(TIMER_KEY, SWAP_INTERVAL_TICKS);
        } else {
            context.set(TIMER_KEY, timer);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[ButterfingersEvent] Inventory orientation normalized for {}",
                    targetPlayer.getName().getString());
        }
    }

    private static void executeInventorySwap(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        Random random = player.getRandom();

        int handSlot = inventory.selectedSlot;
        int targetSlot = INVENTORY_STORAGE_START + random.nextInt(INVENTORY_STORAGE_END - INVENTORY_STORAGE_START + 1);

        ItemStack inHand = inventory.getStack(handSlot);
        ItemStack inBag = inventory.getStack(targetSlot);

        // Atomic slot exchange
        inventory.setStack(handSlot, inBag);
        inventory.setStack(targetSlot, inHand);
        inventory.markDirty();

        player.playerScreenHandler.sendContentUpdates();

        player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7F, 1.3F);
        MerryEvents.LOGGER.debug("[ButterfingersEvent] Swapped hand slot {} with bag slot {} for {}",
                handSlot, targetSlot, player.getName().getString());
    }
}