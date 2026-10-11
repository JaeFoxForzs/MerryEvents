package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
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
    public static final Identifier ID = MerryEvents.id("orbital_strike");
    private static final String STRIKES_KEY = "pending_strikes";
    private static final String RADIUS_KEY = "dispersion_radius";
    private static final int WARMUP_TICKS = 35;
    private static final float LETHAL_EXPLOSION_POWER = 5.5F;
    private static final double DAMAGE_RADIUS = 7.5;

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
        double dispersionRadius = 28.0 + (random.nextDouble() * 20.0);

        context.set(STRIKES_KEY, new ArrayList<StrikeTarget>());
        context.set(RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info(
                "[OrbitalStrikeEvent] Orbital targeting satellite calibrated (dispersion radius={}). Bombardment initiated.",
                String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        List<StrikeTarget> strikes = context.getOrDefault(STRIKES_KEY, null);
        if (strikes == null) {
            strikes = new ArrayList<>();
            context.set(STRIKES_KEY, strikes);
        }

        double radius = context.getDouble(RADIUS_KEY, 36.0);

        if (remainingTicks % 20 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (SpatialTargeting.isHazardImmune(player)) {
                    continue;
                }

                ServerWorld world = player.getServerWorld();

                for (int i = 0; i < 2; i++) {
                    BlockPos randomStrikePos = SpatialTargeting.findRandomSurfacePosition(
                            world, player, radius, Heightmap.Type.MOTION_BLOCKING);

                    if (randomStrikePos != null) {
                        strikes.add(new StrikeTarget(world, randomStrikePos, WARMUP_TICKS));

                        OrbitalStrikeSpawnPayload payload = new OrbitalStrikeSpawnPayload(randomStrikePos,
                                WARMUP_TICKS);
                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            ServerPlayNetworking.send(p, payload);
                        }
                    }
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
                    centerX - DAMAGE_RADIUS, centerY - 4.0, centerZ - DAMAGE_RADIUS,
                    centerX + DAMAGE_RADIUS, centerY + 16.0, centerZ + DAMAGE_RADIUS);

            List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, lethalZone,
                    LivingEntity::isAlive);
            for (int i = 0; i < victims.size(); i++) {
                LivingEntity victim = victims.get(i);
                if (victim instanceof PlayerEntity player && SpatialTargeting.isHazardImmune(player)) {
                    continue;
                }

                double distSq = victim.squaredDistanceTo(centerX, victim.getY(), centerZ);
                if (distSq <= DAMAGE_RADIUS * DAMAGE_RADIUS) {
                    double dist = Math.sqrt(distSq);
                    float damageMultiplier = (float) Math.max(0.15, 1.0 - (dist / DAMAGE_RADIUS));
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

            world.createExplosion(null, centerX, centerY + 0.5, centerZ, LETHAL_EXPLOSION_POWER,
                    World.ExplosionSourceType.MOB);

            return true;
        });
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        List<StrikeTarget> strikes = context.getOrDefault(STRIKES_KEY, null);
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