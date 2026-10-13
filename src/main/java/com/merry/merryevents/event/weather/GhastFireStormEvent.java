package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Global aerial bombardment event that launches ghast fireballs from high above
 * exposed players.
 */
public final class GhastFireStormEvent implements ChaosEvent {
    private static final String REMAINING_FIREBALLS_KEY = "remaining_fireballs";
    private static final String RADIUS_KEY = "dispersion_radius";

    private static final int MIN_FIREBALLS = 32;
    private static final int MAX_FIREBALLS = 128;
    private static final int MAX_FIREBALLS_PER_TICK = 4;
    private static final int MAX_FLIGHT_TICKS = 140;
    private static final double MIN_DISPERSION_RADIUS = 24.0;
    private static final double MAX_DISPERSION_RADIUS = 48.0;
    private static final Heightmap.Type SPAWN_HEIGHTMAP = Heightmap.Type.MOTION_BLOCKING;
    private static final int MIN_HEIGHT_ABOVE_PLAYER = 64;
    private static final int EXTRA_HEIGHT_VARIATION = 64;
    private static final double TARGET_SPREAD = 3.0;
    private static final double INITIAL_SPEED = 0.65;
    private static final double ACCELERATION_POWER = 0.08;
    private static final float MIN_SOUND_PITCH = 0.7F;
    private static final float MAX_SOUND_PITCH = 1.1F;
    private static final float SPAWN_SOUND_VOLUME = 0.8F;

    @Override
    public Identifier getId() {
        return EventIds.GHAST_FIRE_STORM;
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
        int fireballQuota = GhastFireStormEvent.MIN_FIREBALLS + random.nextInt(GhastFireStormEvent.MAX_FIREBALLS - GhastFireStormEvent.MIN_FIREBALLS + 1);
        double dispersionRadius = GhastFireStormEvent.MIN_DISPERSION_RADIUS
                + random.nextDouble() * (GhastFireStormEvent.MAX_DISPERSION_RADIUS - GhastFireStormEvent.MIN_DISPERSION_RADIUS);

        context.set(GhastFireStormEvent.REMAINING_FIREBALLS_KEY, fireballQuota);
        context.set(GhastFireStormEvent.RADIUS_KEY, dispersionRadius);
        MerryEvents.LOGGER.info(
                "[GhastFireStormEvent] High-altitude ghast bombardment ignited: quota={} fireballs, radius={}",
                fireballQuota, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        int remainingFireballs = context.getInt(GhastFireStormEvent.REMAINING_FIREBALLS_KEY, 0);
        int firingWindowTicks = remainingTicks - GhastFireStormEvent.MAX_FLIGHT_TICKS;
        if (remainingFireballs <= 0 || firingWindowTicks <= 0) {
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
        double expectedFireballsThisTick = (double) remainingFireballs / firingWindowTicks;
        int fireballsThisTick = (int) expectedFireballsThisTick;
        if (random.nextDouble() < expectedFireballsThisTick - fireballsThisTick) {
            fireballsThisTick++;
        }
        fireballsThisTick = Math.min(fireballsThisTick, GhastFireStormEvent.MAX_FIREBALLS_PER_TICK);

        double radius = context.getDouble(GhastFireStormEvent.RADIUS_KEY, GhastFireStormEvent.MIN_DISPERSION_RADIUS);
        int spawnedFireballs = 0;
        for (int i = 0; i < fireballsThisTick; i++) {
            ServerPlayerEntity player = eligiblePlayers.get(random.nextInt(eligiblePlayers.size()));
            ServerWorld world = player.getServerWorld();
            BlockPos spawnPos = SpatialTargeting.findRandomSkyPosition(
                    world, player, radius, GhastFireStormEvent.SPAWN_HEIGHTMAP, GhastFireStormEvent.MIN_HEIGHT_ABOVE_PLAYER, GhastFireStormEvent.EXTRA_HEIGHT_VARIATION);
            if (spawnPos == null) {
                continue;
            }

            FireballEntity fireball = EntityType.FIREBALL.create(world);
            if (fireball == null) {
                continue;
            }

            double spawnX = spawnPos.getX() + 0.5;
            double spawnY = spawnPos.getY();
            double spawnZ = spawnPos.getZ() + 0.5;
            Vec3d targetDirection = new Vec3d(
                    player.getX() + (random.nextDouble() - 0.5) * GhastFireStormEvent.TARGET_SPREAD - spawnX,
                    player.getY() + player.getHeight() * 0.5 - spawnY,
                    player.getZ() + (random.nextDouble() - 0.5) * GhastFireStormEvent.TARGET_SPREAD - spawnZ).normalize();

            fireball.refreshPositionAndAngles(spawnX, spawnY, spawnZ, 0.0F, 0.0F);
            fireball.accelerationPower = GhastFireStormEvent.ACCELERATION_POWER;
            fireball.setVelocity(targetDirection.multiply(GhastFireStormEvent.INITIAL_SPEED));
            if (!world.spawnEntity(fireball)) {
                continue;
            }

            context.trackEntity(fireball.getUuid());
            spawnedFireballs++;
            float soundPitch = (float) (GhastFireStormEvent.MIN_SOUND_PITCH
                    + random.nextDouble() * (GhastFireStormEvent.MAX_SOUND_PITCH - GhastFireStormEvent.MIN_SOUND_PITCH));
            world.playSound(null, spawnPos, SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.WEATHER,
                    GhastFireStormEvent.SPAWN_SOUND_VOLUME, soundPitch);
        }

        if (spawnedFireballs > 0) {
            context.set(GhastFireStormEvent.REMAINING_FIREBALLS_KEY, remainingFireballs - spawnedFireballs);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[GhastFireStormEvent] High-altitude ghast bombardment ceased.");
    }
}
