package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Global fluid corruption event belonging to EventCategory.WORLD.
 * Causes water contact to ignite and burn submerged players.
 */
public final class FalseWaterEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.FALSE_WATER;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WORLD;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[FalseWaterEvent] Fluid properties corrupted: water now acts as lava.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 10 != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (SpatialTargeting.isHazardImmune(player)) {
                continue;
            }

            if (player.isTouchingWater()) {
                player.setOnFireFor(3.0F);
                player.damage(player.getServerWorld().getDamageSources().inFire(), 2.0F);
                MerryEvents.LOGGER.debug("[FalseWaterEvent] Boiling scald applied to player {}",
                        player.getName().getString());
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[FalseWaterEvent] Water chemistry normalized.");
    }
}