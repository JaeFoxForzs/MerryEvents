package com.merry.merryevents.event.visual;

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
 * Global visual anomaly event belonging to EventCategory.VISUAL.
 * Compresses living entity models into flat cardboard billboard sprites facing the client camera.
 */
public final class FlatMobsEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.FLAT_MOBS;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.VISUAL;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_SLIME_BLOCK_PLACE, SoundCategory.AMBIENT, 1.0F, 1.5F);
        }
        MerryEvents.LOGGER.info("[FlatMobsEvent] Cardboard cutout visual transposition active globally.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.AMBIENT, 1.0F, 1.2F);
        }
        MerryEvents.LOGGER.info("[FlatMobsEvent] Living entity 3D volumetric rendering restored.");
    }
}