package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.network.OrbitalStrikeSpawnPayload;
import com.merry.merryevents.util.SpatialTargeting;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Global meteorological energy bombardment event belonging to
 * EventCategory.WEATHER.
 * Precipitates randomized high-yield orbital death rays dispersed broadly
 * around players.
 */
public final class OrbitalStrikeEvent implements ChaosEvent {
    private static final String STRIKES_KEY = "pending_strikes";
    private static final String REMAINING_STRIKES_KEY = "remaining_strikes";
    private static final String RADIUS_KEY = "dispersion_radius";
    private static final int MIN_STRIKES = 16;
    private static final int MAX_STRIKES = 64;
    private static final int MAX_STRIKES_PER_TICK = 4;
    private static final int WARMUP_TICKS = 35;
    private static final float LETHAL_EXPLOSION_POWER = 5.5F;
    private static final double DAMAGE_RADIUS = 7.5;
    private static final double MIN_DISPERSION_RADIUS = 28.0;
    private static final double MAX_DISPERSION_RADIUS = 48.0;

    @Override
    public Identifier getId() {
        return EventIds.ORBITAL_STRIKE;
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
        int strikeQuota = OrbitalStrikeEvent.MIN_STRIKES + random.nextInt(OrbitalStrikeEvent.MAX_STRIKES - OrbitalStrikeEvent.MIN_STRIKES + 1);
        double dispersionRadius = OrbitalStrikeEvent.MIN_DISPERSION_RADIUS
                + random.nextDouble() * (OrbitalStrikeEvent.MAX_DISPERSION_RADIUS - OrbitalStrikeEvent.MIN_DISPERSION_RADIUS);

        context.set(OrbitalStrikeEvent.STRIKES_KEY, new ArrayList<StrikeTarget>());
        context.set(OrbitalStrikeEvent.REMAINING_STRIKES_KEY, strikeQuota);
        context.set(OrbitalStrikeEvent.RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info(
                "[OrbitalStrikeEvent] Orbital targeting satellite calibrated (quota={} strikes, dispersion radius={}). Bombardment initiated.",
                strikeQuota, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        List<StrikeTarget> strikes = context.getOrDefault(OrbitalStrikeEvent.STRIKES_KEY, null);
        if (strikes == null) {
            strikes = new ArrayList<>();
            context.set(OrbitalStrikeEvent.STRIKES_KEY, strikes);
        }

        double radius = context.getDouble(OrbitalStrikeEvent.RADIUS_KEY, OrbitalStrikeEvent.MIN_DISPERSION_RADIUS);
        int remainingStrikes = context.getInt(OrbitalStrikeEvent.REMAINING_STRIKES_KEY, 0);
        int strikeWindowTicks = remainingTicks - OrbitalStrikeEvent.WARMUP_TICKS;

        if (remainingStrikes > 0 && strikeWindowTicks > 0) {
            List<ServerPlayerEntity> eligiblePlayers = new ArrayList<>();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!SpatialTargeting.isHazardImmune(player)) {
                    eligiblePlayers.add(player);
                }
            }

            if (!eligiblePlayers.isEmpty()) {
                double expectedStrikesThisTick = (double) remainingStrikes / strikeWindowTicks;
                int strikesThisTick = (int) expectedStrikesThisTick;
                if (server.getOverworld().getRandom().nextDouble() < expectedStrikesThisTick - strikesThisTick) {
                    strikesThisTick++;
                }
                strikesThisTick = Math.min(strikesThisTick, OrbitalStrikeEvent.MAX_STRIKES_PER_TICK);

                int spawnedStrikes = 0;
                for (int i = 0; i < strikesThisTick; i++) {
                    ServerPlayerEntity player = eligiblePlayers.get(
                            server.getOverworld().getRandom().nextInt(eligiblePlayers.size()));
                    ServerWorld world = player.getServerWorld();
                    BlockPos randomStrikePos = SpatialTargeting.findRandomSurfacePosition(
                            world, player, radius, Heightmap.Type.MOTION_BLOCKING);

                    if (randomStrikePos != null) {
                        strikes.add(new StrikeTarget(world, randomStrikePos, OrbitalStrikeEvent.WARMUP_TICKS));

                        OrbitalStrikeSpawnPayload payload = new OrbitalStrikeSpawnPayload(randomStrikePos,
                                OrbitalStrikeEvent.WARMUP_TICKS);
                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(p, payload);
                        }
                        spawnedStrikes++;
                    }
                }

                if (spawnedStrikes > 0) {
                    context.set(OrbitalStrikeEvent.REMAINING_STRIKES_KEY, remainingStrikes - spawnedStrikes);
                }
            }
        }

        strikes.removeIf(strike -> {
            strike.remainingWarmup--;
            ServerWorld world = strike.world;
            BlockPos pos = strike.pos;

            if (strike.remainingWarmup > 0) {
                if (strike.remainingWarmup % 10 == 0) {
                    world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.WEATHER, 1.2F,
                            1.8F);
                }
                return false;
            }

            double centerX = pos.getX() + 0.5;
            double centerY = pos.getY();
            double centerZ = pos.getZ() + 0.5;

            Box lethalZone = new Box(
                    centerX - OrbitalStrikeEvent.DAMAGE_RADIUS, centerY - 4.0, centerZ - OrbitalStrikeEvent.DAMAGE_RADIUS,
                    centerX + OrbitalStrikeEvent.DAMAGE_RADIUS, centerY + 16.0, centerZ + OrbitalStrikeEvent.DAMAGE_RADIUS);

            List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, lethalZone,
                    LivingEntity::isAlive);
            for (int i = 0; i < victims.size(); i++) {
                LivingEntity victim = victims.get(i);
                if (victim instanceof PlayerEntity player && SpatialTargeting.isHazardImmune(player)) {
                    continue;
                }

                double distSq = victim.squaredDistanceTo(centerX, victim.getY(), centerZ);
                if (distSq <= OrbitalStrikeEvent.DAMAGE_RADIUS * OrbitalStrikeEvent.DAMAGE_RADIUS) {
                    double dist = Math.sqrt(distSq);
                    float damageMultiplier = (float) Math.max(0.15, 1.0 - (dist / OrbitalStrikeEvent.DAMAGE_RADIUS));
                    float finalDamage = Math.max(16.0F, 50.0F * damageMultiplier);

                    victim.timeUntilRegen = 0;
                    victim.damage(world.getDamageSources().generic(), finalDamage);
                    victim.setOnFireFor(8.0F);

                    double pushX = (victim.getX() - centerX) * 0.5;
                    double pushZ = (victim.getZ() - centerZ) * 0.5;
                    victim.addVelocity(pushX, 0.65, pushZ);
                    victim.velocityModified = true;
                }
            }

            world.spawnParticles(ParticleTypes.FLASH, centerX, centerY + 1.0, centerZ, 5, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, centerX, centerY + 1.0, centerZ, 4, 0.3, 0.3, 0.3,
                    0.0);
            world.spawnParticles(ParticleTypes.LAVA, centerX, centerY + 1.0, centerZ, 40, 0.8, 0.8, 0.8, 0.3);

            world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.WEATHER, 5.0F, 0.6F);
            world.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 3.0F, 0.9F);

            world.createExplosion(null, centerX, centerY + 0.5, centerZ, OrbitalStrikeEvent.LETHAL_EXPLOSION_POWER,
                    World.ExplosionSourceType.MOB);

            return true;
        });
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        List<StrikeTarget> strikes = context.getOrDefault(OrbitalStrikeEvent.STRIKES_KEY, null);
        if (strikes != null) {
            strikes.clear();
        }
        MerryEvents.LOGGER.info("[OrbitalStrikeEvent] Orbital bombardment ceased.");
    }

    private static class StrikeTarget {
        final ServerWorld world;
        final BlockPos pos;
        int remainingWarmup;

        StrikeTarget(ServerWorld world, BlockPos pos, int remainingWarmup) {
            this.world = world;
            this.pos = pos;
            this.remainingWarmup = remainingWarmup;
        }
    }
}