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
 * Global visual environment event belonging to EventCategory.VISION.
 * Cycles rhythmic full-screen color overlays using smooth cosine transitions.
 */
public final class DiscoEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.DISCO;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.VISION;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.AMBIENT, 1.0F, 1.5F);
        }
        MerryEvents.LOGGER.info("[DiscoEvent] Disco party started globally.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[DiscoEvent] Disco party ended.");
    }
}