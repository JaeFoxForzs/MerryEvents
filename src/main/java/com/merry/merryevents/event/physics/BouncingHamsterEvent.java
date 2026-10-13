package com.merry.merryevents.event.physics;

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
 * Player-specific forced jumping event belonging to EventCategory.PHYSICS.
 * Authoritatively coordinates perpetual jumping mechanics dispatched to the
 * client driver.
 */
public final class BouncingHamsterEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.BOUNCING_HAMSTER;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.PHYSICS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            targetPlayer.playSoundToPlayer(SoundEvents.ENTITY_RABBIT_JUMP, SoundCategory.PLAYERS, 1.0F, 1.2F);
            MerryEvents.LOGGER.info("[BouncingHamsterEvent] Continuous jumping effect started for {}",
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
            MerryEvents.LOGGER.info("[BouncingHamsterEvent] Continuous jumping effect ended for {}",
                    targetPlayer.getName().getString());
        }
    }
}