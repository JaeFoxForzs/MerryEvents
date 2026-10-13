package com.merry.merryevents.event.status;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific neutralization event belonging to EventCategory.STATUS.
 * Enforces total pacifist equilibrium by granting comprehensive damage immunity
 * while neutralizing all outgoing attacks against living entities.
 */
public final class EquilibriumEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.EQUILIBRIUM;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.STATUS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Evaluates whether equilibrium status is currently active for the target player.
     *
     * @param player Target server player entity
     * @return True if equilibrium pacifist protection is active
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), EventIds.EQUILIBRIUM);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 1.8F);
            MerryEvents.LOGGER.info("[EquilibriumEvent] Pacifist equilibrium established for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
        if (targetPlayer != null && targetPlayer.isAlive() && remainingTicks % 8 == 0) {
            ServerWorld world = targetPlayer.getServerWorld();
            world.spawnParticles(ParticleTypes.WAX_OFF,
                    targetPlayer.getX(), targetPlayer.getY() + 0.1, targetPlayer.getZ(),
                    3, 0.3, 0.1, 0.3, 0.02);
            world.spawnParticles(ParticleTypes.END_ROD,
                    targetPlayer.getX(), targetPlayer.getY() + 1.0, targetPlayer.getZ(),
                    1, 0.2, 0.4, 0.2, 0.01);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0F, 1.2F);
            MerryEvents.LOGGER.info("[EquilibriumEvent] Pacifist equilibrium released for {}",
                    targetPlayer.getName().getString());
        }
    }
}