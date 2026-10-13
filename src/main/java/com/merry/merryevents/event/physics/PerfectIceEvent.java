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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Global physics override event belonging to EventCategory.PHYSICS.
 * Replaces surface block friction across all dimensions with Blue Ice
 * slipperiness.
 */
public final class PerfectIceEvent implements ChaosEvent {
    public static final float BLUE_ICE_SLIPPERINESS = 0.989F;

    /**
     * Dedicated-server safe hook initialized exclusively on the client side.
     */
    public static BooleanSupplier clientActiveSupplier = () -> false;

    @Override
    public Identifier getId() {
        return EventIds.PERFECT_ICE;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.PHYSICS;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    /**
     * Queries whether perfect ice slipperiness is actively running in the given
     * world context.
     *
     * @param world Target world instance to evaluate
     * @return True if block friction should be overridden to blue ice
     */
    public static boolean isActive(@Nullable World world) {
        if (world != null && world.isClient()) {
            return PerfectIceEvent.clientActiveSupplier.getAsBoolean();
        }
        return com.merry.merryevents.engine.EventManager.getInstance().isGlobalEventActive(EventIds.PERFECT_ICE);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.AMBIENT, 1.0F, 1.6F);
        }
        MerryEvents.LOGGER.info(
                "[PerfectIceEvent] Extreme surface friction anomaly active: all blocks set to 0.989 slipperiness.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.AMBIENT, 1.0F, 0.7F);
        }
        MerryEvents.LOGGER.info("[PerfectIceEvent] Global surface friction normalized to standard physical baseline.");
    }
}