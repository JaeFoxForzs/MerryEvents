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
 * Player-specific interface blackout event belonging to EventCategory.UI.
 * Disables client-side HUD rendering.
 */
public final class GhostGuiEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.GHOST_GUI;
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
            MerryEvents.LOGGER.info("[GhostGuiEvent] Heads-up display blackout transmitted to {}",
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
            MerryEvents.LOGGER.info("[GhostGuiEvent] Heads-up display restored for {}",
                    targetPlayer.getName().getString());
        }
    }
}