package com.merry.merryevents.event.sounds;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific auditory replacement event belonging to EventCategory.SOUNDS.
 * Instructs the client driver to replace movement footsteps with squeaking
 * rubber ducks
 * and jumps with cartoon springs, audible exclusively to the affected player.
 */
public final class RubberWorldEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("rubber_world");

    @Override
    public Identifier getId() {
        return RubberWorldEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.SOUNDS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[RubberWorldEvent] Rubber audio stream activated for {}",
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
            MerryEvents.LOGGER.info("[RubberWorldEvent] Natural acoustics restored for {}",
                    targetPlayer.getName().getString());
        }
    }
}