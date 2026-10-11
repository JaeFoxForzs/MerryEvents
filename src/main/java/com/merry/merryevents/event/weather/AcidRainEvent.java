package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import com.merry.merryevents.util.WeatherUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Global meteorological corrosion event belonging to EventCategory.WEATHER.
 * Forces an acidic storm that damages all sky-exposed living entities.
 */
public final class AcidRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("acid_rain");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WEATHER;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        WeatherUtil.startRain(server, 6000);
        MerryEvents.LOGGER.info(
                "[AcidRainEvent] Atmospheric corruption initiated: acid rain active across all surface dimensions.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            if (!world.getDimension().hasSkyLight() || !world.isRaining()) {
                continue;
            }

            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvulnerable()) {
                    continue;
                }

                if (living instanceof PlayerEntity player && SpatialTargeting.isHazardImmune(player)) {
                    continue;
                }

                BlockPos pos = living.getBlockPos();
                if (WeatherUtil.isExposedToRain(world, pos, living)) {
                    living.damage(world.getDamageSources().magic(), 1.0F);
                }
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        WeatherUtil.stopRain(server);
        MerryEvents.LOGGER.info("[AcidRainEvent] Atmospheric conditions normalized: weather cleared.");
    }
}