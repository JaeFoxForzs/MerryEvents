package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import com.merry.merryevents.util.WeatherUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Global aerial hazard event belonging to EventCategory.WEATHER.
 * Precipitates falling anvils above sky-exposed players with randomized cadence
 * and dispersion.
 */
public final class AnvilRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("anvil_rain");
    private static final String INTERVAL_KEY = "cadence_ticks";
    private static final String RADIUS_KEY = "dispersion_radius";

    private static final int MIN_CADENCE_TICKS = 10;
    private static final int MAX_CADENCE_TICKS = 24;
    private static final double MIN_DISPERSION_RADIUS = 8.0;
    private static final double MAX_DISPERSION_RADIUS = 24.0;
    private static final Heightmap.Type SPAWN_HEIGHTMAP = Heightmap.Type.MOTION_BLOCKING;
    private static final int MIN_HEIGHT_ABOVE_PLAYER = 20;
    private static final int EXTRA_HEIGHT_VARIATION = 8;
    private static final float ANVIL_DAMAGE = 3.0F;
    private static final int ANVIL_DAMAGE_CAP = 40;
    private static final float SPAWN_SOUND_VOLUME = 1.2F;
    private static final float SPAWN_SOUND_PITCH = 0.4F;

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
        Random random = server.getOverworld().getRandom();
        int cadenceTicks = MIN_CADENCE_TICKS + random.nextInt(MAX_CADENCE_TICKS - MIN_CADENCE_TICKS + 1);
        double dispersionRadius = MIN_DISPERSION_RADIUS
                + random.nextDouble() * (MAX_DISPERSION_RADIUS - MIN_DISPERSION_RADIUS);

        context.set(INTERVAL_KEY, cadenceTicks);
        context.set(RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info("[AnvilRainEvent] Anvil shower initiated: interval={}t, radius={}",
                cadenceTicks, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        int cadence = context.getInt(INTERVAL_KEY, MAX_CADENCE_TICKS);
        if (remainingTicks % cadence != 0) {
            return;
        }

        double radius = context.getDouble(RADIUS_KEY, MIN_DISPERSION_RADIUS);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (SpatialTargeting.isHazardImmune(player)) {
                continue;
            }

            ServerWorld world = player.getServerWorld();
            BlockPos playerPos = player.getBlockPos();

            if (!WeatherUtil.isExposedToSky(world, playerPos)) {
                continue;
            }

            BlockPos spawnPos = SpatialTargeting.findRandomSkyPosition(
                    world, player, radius, SPAWN_HEIGHTMAP, MIN_HEIGHT_ABOVE_PLAYER, EXTRA_HEIGHT_VARIATION);

            if (spawnPos == null) {
                continue;
            }

            FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(world, spawnPos,
                    Blocks.ANVIL.getDefaultState());
            anvil.setHurtEntities(ANVIL_DAMAGE, ANVIL_DAMAGE_CAP);
            anvil.dropItem = false;
            anvil.setDestroyedOnLanding();
            context.trackEntity(anvil.getUuid());

            world.playSound(null, spawnPos, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.WEATHER, SPAWN_SOUND_VOLUME,
                    SPAWN_SOUND_PITCH);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[AnvilRainEvent] Anvil shower normalized.");
    }
}