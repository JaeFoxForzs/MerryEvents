package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialEntityScanner;
import com.merry.merryevents.util.SpatialTargeting;
import com.merry.merryevents.util.WeatherUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Global meteorological corrosion event belonging to EventCategory.WEATHER.
 * Forces an acidic storm that damages sky-exposed living entities within
 * localized player zones.
 */
public final class AcidRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("acid_rain");
    private static final String PLAYER_INDEX_KEY = "acid_rain_player_idx";
    private static final double SEARCH_RADIUS = 64.0;
    private static final int MAX_TARGETS_PER_TICK = 128;
    private static final float ACID_DAMAGE = 1.0F;

    @Override
    public Identifier getId() {
        return AcidRainEvent.ID;
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

        SpatialEntityScanner.scanAndProcess(
                server,
                context,
                AcidRainEvent.PLAYER_INDEX_KEY,
                LivingEntity.class,
                AcidRainEvent.SEARCH_RADIUS,
                AcidRainEvent.MAX_TARGETS_PER_TICK,
                world -> world.getDimension().hasSkyLight() && world.isRaining(),
                living -> living.isAlive() && !living.isInvulnerable()
                        && (!(living instanceof PlayerEntity player) || !SpatialTargeting.isHazardImmune(player)),
                (world, player, living) -> {
                    BlockPos pos = living.getBlockPos();
                    if (WeatherUtil.isExposedToRain(world, pos, living)) {
                        living.damage(world.getDamageSources().magic(), AcidRainEvent.ACID_DAMAGE);
                    }
                });
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        WeatherUtil.stopRain(server);
        MerryEvents.LOGGER.info("[AcidRainEvent] Atmospheric conditions normalized: weather cleared.");
    }
}