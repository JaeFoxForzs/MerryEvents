package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Global fluid paradigm inversion event belonging to EventCategory.WORLD.
 * Inverts functional behaviors between water and lava while preserving native
 * rendering shaders.
 */
public final class LavaWaterSwapEvent implements ChaosEvent {
    @Override
    public com.merry.merryevents.api.EventDefinition getDefinition() {
        return com.merry.merryevents.api.EventDefinition.withConflicts(
                this.getId(), this.getCategory(), com.merry.merryevents.api.EventScope.GLOBAL,
                java.util.Set.of(EventIds.FALSE_WATER));
    }

    /**
     * Dedicated-server safe hook initialized exclusively on the client side.
     */
    public static BooleanSupplier clientActiveSupplier = () -> false;

    @Override
    public Identifier getId() {
        return EventIds.LAVA_WATER_SWAP;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WORLD;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    /**
     * Queries whether the fluid transposition effect is active on the given world
     * side.
     *
     * @param world Target world context to evaluate
     * @return True if water and lava mechanics should be transposed
     */
    public static boolean isActive(World world) {
        if (world.isClient()) {
            return LavaWaterSwapEvent.clientActiveSupplier.getAsBoolean();
        }
        return com.merry.merryevents.engine.EventManager.getInstance().isGlobalEventActive(EventIds.LAVA_WATER_SWAP);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        MerryEvents.LOGGER.info("[LavaWaterSwapEvent] Fluid transposition active: water and lava mechanics swapped.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[LavaWaterSwapEvent] Fluid transposition normalized: baseline physics restored.");
    }
}