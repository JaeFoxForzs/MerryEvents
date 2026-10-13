package com.merry.merryevents.event.vision;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific visual occlusion event belonging to EventCategory.VISION.
 * Compresses effective sight range using combined Darkness and Blindness status
 * effects.
 */
public final class DenseDarknessEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.DENSE_DARKNESS;
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
            applyDarkness(targetPlayer);
            MerryEvents.LOGGER.info("[DenseDarknessEvent] Visual field collapsed for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks % 20 != 0) {
            return;
        }

        applyDarkness(targetPlayer);
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.removeStatusEffect(StatusEffects.DARKNESS);
            targetPlayer.removeStatusEffect(StatusEffects.BLINDNESS);
            MerryEvents.LOGGER.info("[DenseDarknessEvent] Visual field restored for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer,
            EventContext context) {
        applyDarkness(newPlayer);
    }

    private void applyDarkness(ServerPlayerEntity player) {
        StatusEffectInstance dark = player.getStatusEffect(StatusEffects.DARKNESS);
        if (dark == null || dark.getDuration() <= 20) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, false, false, true));
        }

        StatusEffectInstance blind = player.getStatusEffect(StatusEffects.BLINDNESS);
        if (blind == null || blind.getDuration() <= 20) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false, true));
        }
    }
}