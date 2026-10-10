package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
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
 * Global meteorological corruption event belonging to EventCategory.WEATHER.
 * Induces an acidic downpour that erodes all exposed living entities across
 * active dimensions.
 */
public final class AcidRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("acid_rain");
    private static final String PREVIOUS_WEATHER_KEY = "was_raining";

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
        ServerWorld overworld = server.getOverworld();
        context.set(PREVIOUS_WEATHER_KEY, overworld.isRaining());

        for (ServerWorld world : server.getWorlds()) {
            if (world.getDimension().hasSkyLight()) {
                world.setWeather(0, 6000, true, false);
            }
        }

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

                if (living instanceof PlayerEntity player && (player.isCreative() || player.isSpectator())) {
                    continue;
                }

                BlockPos pos = living.getBlockPos();
                if (world.hasRain(pos)) {
                    living.damage(world.getDamageSources().magic(), 1.0F);
                }
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        boolean wasRaining = context.getOrDefault(PREVIOUS_WEATHER_KEY, false);

        for (ServerWorld world : server.getWorlds()) {
            if (world.getDimension().hasSkyLight()) {
                if (wasRaining) {
                    world.setWeather(0, 6000, true, false);
                } else {
                    world.setWeather(6000, 0, false, false);
                }
            }
        }

        MerryEvents.LOGGER.info("[AcidRainEvent] Atmospheric conditions normalized: weather state restored.");
    }
}