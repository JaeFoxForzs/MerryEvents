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
 * Player-specific visual alteration event belonging to EventCategory.VISION.
 * Simulates one of six distinct color vision deficiency matrix profiles via
 * post-shaders.
 */
public final class ColorblindnessEvent implements ChaosEvent {
    @Override
    public com.merry.merryevents.api.EventDefinition getDefinition() {
        return com.merry.merryevents.api.EventDefinition.withConflicts(
                this.getId(), this.getCategory(), com.merry.merryevents.api.EventScope.PLAYER,
                java.util.Set.of(EventIds.MYOPIA, EventIds.UPSIDE_DOWN));
    }

    @Override
    public Identifier getId() {
        return EventIds.COLORBLINDNESS;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 0.8F, 1.2F);
            MerryEvents.LOGGER.info("[ColorblindnessEvent] Colorblindness applied to {}",
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
            MerryEvents.LOGGER.info("[ColorblindnessEvent] Color vision normalized for {}",
                    targetPlayer.getName().getString());
        }
    }
}