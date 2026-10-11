package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Global aerial hazard event belonging to EventCategory.WEATHER.
 * Precipitates a randomized quota of falling anvils with randomized dispersion.
 */
public final class AnvilRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("anvil_rain");
    private static final String REMAINING_ANVILS_KEY = "remaining_anvils";
    private static final String RADIUS_KEY = "dispersion_radius";

    private static final int MIN_ANVILS = 256;
    private static final int MAX_ANVILS = 1024;
    private static final int MAX_ANVILS_PER_TICK = 16;
    private static final int MAX_FALL_TICKS = 100;
    private static final double MIN_DISPERSION_RADIUS = 32.0;
    private static final double MAX_DISPERSION_RADIUS = 64.0;
    private static final Heightmap.Type SPAWN_HEIGHTMAP = Heightmap.Type.MOTION_BLOCKING;
    private static final int MIN_HEIGHT_ABOVE_PLAYER = 64;
    private static final int EXTRA_HEIGHT_VARIATION = 64;
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
        int anvilQuota = MIN_ANVILS + random.nextInt(MAX_ANVILS - MIN_ANVILS + 1);
        double dispersionRadius = MIN_DISPERSION_RADIUS
                + random.nextDouble() * (MAX_DISPERSION_RADIUS - MIN_DISPERSION_RADIUS);

        context.set(REMAINING_ANVILS_KEY, anvilQuota);
        context.set(RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info("[AnvilRainEvent] Anvil storm initiated: quota={} anvils, radius={}",
                anvilQuota, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        int remainingAnvils = context.getInt(REMAINING_ANVILS_KEY, 0);
        int firingWindowTicks = remainingTicks - MAX_FALL_TICKS;
        if (remainingAnvils <= 0 || firingWindowTicks <= 0) {
            return;
        }

        List<ServerPlayerEntity> eligiblePlayers = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!SpatialTargeting.isHazardImmune(player)) {
                eligiblePlayers.add(player);
            }
        }
        if (eligiblePlayers.isEmpty()) {
            return;
        }

        Random random = server.getOverworld().getRandom();
        double expectedAnvilsThisTick = (double) remainingAnvils / firingWindowTicks;
        int anvilsThisTick = (int) expectedAnvilsThisTick;
        if (random.nextDouble() < expectedAnvilsThisTick - anvilsThisTick) {
            anvilsThisTick++;
        }
        anvilsThisTick = Math.min(anvilsThisTick, MAX_ANVILS_PER_TICK);

        double radius = context.getDouble(RADIUS_KEY, MIN_DISPERSION_RADIUS);
        int spawnedAnvils = 0;

        for (int i = 0; i < anvilsThisTick; i++) {
            ServerPlayerEntity player = eligiblePlayers.get(random.nextInt(eligiblePlayers.size()));
            ServerWorld world = player.getServerWorld();
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
            spawnedAnvils++;

            world.playSound(null, spawnPos, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.WEATHER, SPAWN_SOUND_VOLUME,
                    SPAWN_SOUND_PITCH);
        }

        if (spawnedAnvils > 0) {
            context.set(REMAINING_ANVILS_KEY, remainingAnvils - spawnedAnvils);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[AnvilRainEvent] Anvil shower normalized.");
    }
}