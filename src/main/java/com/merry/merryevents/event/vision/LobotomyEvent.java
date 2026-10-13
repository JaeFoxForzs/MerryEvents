package com.merry.merryevents.event.vision;

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
 * Player-specific sensory disruption event belonging to EventCategory.VISION.
 * Periodically triggers visual frame freezes accompanied by auditory cues and downscaled pixelation.
 */
public final class LobotomyEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.LOBOTOMY;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.VISION;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[LobotomyEvent] Lobotomy syndrome initiated for {}", targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context, int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[LobotomyEvent] Lobotomy ended for {}", targetPlayer.getName().getString());
        }
    }
}