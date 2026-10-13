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
 * Player-specific involuntary movement event belonging to
 * EventCategory.PHYSICS.
 * Dispatches autonomous sprint overrides in pseudorandom world directions
 * without locking camera pitch/yaw.
 */
public final class UncontrolledRunEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.UNCONTROLLED_RUN;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 0.7F,
                    1.5F);
            MerryEvents.LOGGER.info("[UncontrolledRunEvent] Autonomous sprint started for {}",
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
            MerryEvents.LOGGER.info("[UncontrolledRunEvent] Autonomous sprint ended for {}",
                    targetPlayer.getName().getString());
        }
    }
}