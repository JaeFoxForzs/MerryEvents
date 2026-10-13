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
 * Player-specific camera disorientation event belonging to
 * EventCategory.CAMERA.
 * Authoritatively coordinates the stochastic camera snap effect lifecycle on
 * the target player.
 */
public final class EpilepsyEvent implements ChaosEvent {
    @Override
    public com.merry.merryevents.api.EventDefinition getDefinition() {
        return com.merry.merryevents.api.EventDefinition.withConflicts(
                this.getId(), this.getCategory(), com.merry.merryevents.api.EventScope.PLAYER,
                java.util.Set.of(EventIds.ADHD));
    }

    @Override
    public Identifier getId() {
        return EventIds.EPILEPSY;
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
            targetPlayer.getServerWorld().playSound(
                    null,
                    targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    SoundEvents.ENTITY_SKELETON_STEP, SoundCategory.PLAYERS, 0.8F, 1.4F);
            MerryEvents.LOGGER.info("[EpilepsyEvent] Stochastic camera snap effect started for {}",
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
            MerryEvents.LOGGER.info("[EpilepsyEvent] Stochastic camera snap effect ended for {}",
                    targetPlayer.getName().getString());
        }
    }
}