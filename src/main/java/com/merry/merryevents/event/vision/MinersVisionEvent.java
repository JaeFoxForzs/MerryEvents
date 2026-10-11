package com.merry.merryevents.event.vision;

import com.merry.merryevents.MerryEvents;
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
 * Персональное событие: подсветка руд сквозь стены.
 */
public final class MinersVisionEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("miners_vision");

    @Override
    public Identifier getId() {
        return ID;
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
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.2F);
            MerryEvents.LOGGER.info("[MinersVisionEvent] Miner's vision granted to {}", targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context, int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[MinersVisionEvent] Miner's vision revoked from {}", targetPlayer.getName().getString());
        }
    }
}