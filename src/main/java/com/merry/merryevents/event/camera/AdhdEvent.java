package com.merry.merryevents.event.camera;

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
 * Player-specific involuntary attention deviation event belonging to EventCategory.CAMERA.
 * Authoritative lifecycle driver synchronizing event state to the client engine,
 * which executes smooth per-frame gaze interpolation and continuous entity tracking.
 */
public final class AdhdEvent implements ChaosEvent {
    @Override
    public com.merry.merryevents.api.EventDefinition getDefinition() {
        return com.merry.merryevents.api.EventDefinition.withConflicts(
                this.getId(), this.getCategory(), com.merry.merryevents.api.EventScope.PLAYER,
                java.util.Set.of(EventIds.STIFF_NECK, EventIds.EPILEPSY), 600, 1);
    }

    @Override
    public Identifier getId() {
        return EventIds.ADHD;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.CAMERA;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            targetPlayer.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6F, 1.5F);
            MerryEvents.LOGGER.info("[AdhdEvent] Attention deficit camera deviation active for {}",
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
            MerryEvents.LOGGER.info("[AdhdEvent] Camera control normalized for {}",
                    targetPlayer.getName().getString());
        }
    }
}