package com.merry.merryevents.event.vision;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific visual disorientation event belonging to
 * EventCategory.VISION.
 * Inverts world camera orientation by 180 degrees while preserving standard
 * movement vectors.
 */
public final class UpsideDownEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.UPSIDE_DOWN;
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
            targetPlayer.playSoundToPlayer(SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 1.0F, 0.7F);
            MerryEvents.LOGGER.info("[UpsideDownEvent] World flipped for {}", targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[UpsideDownEvent] World orientation restored for {}",
                    targetPlayer.getName().getString());
        }
    }
}