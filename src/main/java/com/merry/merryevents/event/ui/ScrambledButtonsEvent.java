package com.merry.merryevents.event.ui;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific interface scramble event belonging to EventCategory.UI.
 * Randomly permutes button text components within client GUI menus while
 * preserving layout and callbacks.
 */
public final class ScrambledButtonsEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.SCRAMBLED_BUTTONS;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.UI;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[ScrambledButtonsEvent] Dispatched button scrambling to {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[ScrambledButtonsEvent] Normalized menu labels for {}",
                    targetPlayer.getName().getString());
        }
    }
}