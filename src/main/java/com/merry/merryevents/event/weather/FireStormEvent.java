package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.SmallFireballEntity;
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
 * Global meteorological incendiary event belonging to EventCategory.WEATHER.
 * Releases a randomized event-wide quota of non-explosive small fireballs over
 * the event duration.
 */
public final class FireStormEvent implements ChaosEvent {
    private static final String REMAINING_FIREBALLS_KEY = "remaining_fireballs";
    private static final String RADIUS_KEY = "dispersion_radius";

    private static final int MIN_FIREBALLS = 128;
    private static final int MAX_FIREBALLS = 1024;
    private static final int MAX_FIREBALLS_PER_TICK = 16;
    private static final double MIN_DISPERSION_RADIUS = 10.0;
    private static final double MAX_DISPERSION_RADIUS = 24.0;
    private static final Heightmap.Type SPAWN_HEIGHTMAP = Heightmap.Type.MOTION_BLOCKING;
    private static final int MIN_HEIGHT_ABOVE_PLAYER = 64;
    private static final int EXTRA_HEIGHT_VARIATION = 64;
    private static final double MIN_HORIZONTAL_SPEED = -0.12;
    private static final double MAX_HORIZONTAL_SPEED = 0.12;
    private static final double MIN_DOWNWARD_SPEED = 0.8;
    private static final double MAX_DOWNWARD_SPEED = 1.6;
    private static final float SPAWN_SOUND_VOLUME = 0.7F;
    private static final float MIN_SOUND_PITCH = 0.65F;
    private static final float MAX_SOUND_PITCH = 1.15F;

    @Override
    public Identifier getId() {
        return EventIds.FIRE_STORM;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        Random random = server.getOverworld().getRandom();
        int fireballQuota = FireStormEvent.MIN_FIREBALLS + random.nextInt(FireStormEvent.MAX_FIREBALLS - FireStormEvent.MIN_FIREBALLS + 1);
        double dispersionRadius = FireStormEvent.MIN_DISPERSION_RADIUS
                + random.nextDouble() * (FireStormEvent.MAX_DISPERSION_RADIUS - FireStormEvent.MIN_DISPERSION_RADIUS);

        context.set(FireStormEvent.REMAINING_FIREBALLS_KEY, fireballQuota);
        context.set(FireStormEvent.RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info("[FireStormEvent] Incendiary storm ignited: quota={} fireballs, radius={}",
                fireballQuota, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        int remainingFireballs = context.getInt(FireStormEvent.REMAINING_FIREBALLS_KEY, 0);
        if (remainingFireballs <= 0 || remainingTicks <= 0) {
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
        double expectedFireballsThisTick = (double) remainingFireballs / remainingTicks;
        int fireballsThisTick = (int) expectedFireballsThisTick;
        if (random.nextDouble() < expectedFireballsThisTick - fireballsThisTick) {
            fireballsThisTick++;
        }
        fireballsThisTick = Math.min(fireballsThisTick, FireStormEvent.MAX_FIREBALLS_PER_TICK);

        double radius = context.getDouble(FireStormEvent.RADIUS_KEY, FireStormEvent.MIN_DISPERSION_RADIUS);
        int spawnedFireballs = 0;
        for (int i = 0; i < fireballsThisTick; i++) {
            ServerPlayerEntity player = eligiblePlayers.get(random.nextInt(eligiblePlayers.size()));
            ServerWorld world = player.getServerWorld();
            BlockPos spawnPos = SpatialTargeting.findRandomSkyPosition(
                    world, player, radius, FireStormEvent.SPAWN_HEIGHTMAP, FireStormEvent.MIN_HEIGHT_ABOVE_PLAYER, FireStormEvent.EXTRA_HEIGHT_VARIATION);
            if (spawnPos == null) {
                continue;
            }

            SmallFireballEntity fireball = EntityType.SMALL_FIREBALL.create(world);
            if (fireball == null) {
                continue;
            }

            fireball.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F,
                    90.0F);
            fireball.setVelocity(
                    FireStormEvent.MIN_HORIZONTAL_SPEED + random.nextDouble() * (FireStormEvent.MAX_HORIZONTAL_SPEED - FireStormEvent.MIN_HORIZONTAL_SPEED),
                    -(FireStormEvent.MIN_DOWNWARD_SPEED + random.nextDouble() * (FireStormEvent.MAX_DOWNWARD_SPEED - FireStormEvent.MIN_DOWNWARD_SPEED)),
                    FireStormEvent.MIN_HORIZONTAL_SPEED + random.nextDouble() * (FireStormEvent.MAX_HORIZONTAL_SPEED - FireStormEvent.MIN_HORIZONTAL_SPEED));
            if (!world.spawnEntity(fireball)) {
                continue;
            }

            context.trackEntity(fireball.getUuid());
            spawnedFireballs++;
            float soundPitch = (float) (FireStormEvent.MIN_SOUND_PITCH
                    + random.nextDouble() * (FireStormEvent.MAX_SOUND_PITCH - FireStormEvent.MIN_SOUND_PITCH));
            world.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.WEATHER, FireStormEvent.SPAWN_SOUND_VOLUME, soundPitch);
        }

        if (spawnedFireballs > 0) {
            context.set(FireStormEvent.REMAINING_FIREBALLS_KEY, remainingFireballs - spawnedFireballs);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[FireStormEvent] Incendiary storm neutralized.");
    }
}