package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Global astronomical corruption event belonging to EventCategory.WEATHER.
 * Transforms unobstructed daytime sunlight into a lethal heat source damaging
 * exposed entities.
 */
public final class SolarVerdictEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("solar_verdict");
    private static final String DAY_ACTIVE_KEY = "day_active";
    private static final String NEXT_PLAYER_INDEX_KEY = "next_player_index";
    private static final long TICKS_PER_DAY = 24000L;
    private static final long DUSK_TIME = 12500L;
    private static final int DAMAGE_INTERVAL_TICKS = 20;
    private static final int MAX_TARGETS_PER_TICK = 128;
    private static final double PLAYER_SEARCH_RADIUS = 96.0;
    private static final double PLAYER_SEARCH_RADIUS_SQUARED = SolarVerdictEvent.PLAYER_SEARCH_RADIUS
            * SolarVerdictEvent.PLAYER_SEARCH_RADIUS;
    private static final int HELMET_DAMAGE = 1;
    private static final float HELMET_FIRE_DAMAGE = 1.0F;
    private static final float EXPOSED_FIRE_DAMAGE = 4.0F;
    private static final float EXPOSED_FIRE_DURATION_SECONDS = 8.0F;
    private static final int HELMET_SMOKE_PARTICLE_COUNT = 2;
    private static final double HELMET_SMOKE_SPREAD = 0.1;
    private static final double HELMET_SMOKE_SPEED = 0.02;
    private static final int EXPOSED_FLAME_PARTICLE_COUNT = 4;
    private static final double EXPOSED_FLAME_SPREAD = 0.2;
    private static final double EXPOSED_FLAME_SPEED = 0.05;

    @Override
    public Identifier getId() {
        return SolarVerdictEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WEATHER;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    /**
     * Calculates the exact remaining ticks until dusk, or spans the remaining night
     * plus full next day.
     *
     * @param server Active MinecraftServer instance
     * @return Total ticks until the next solar dusk
     */
    @Override
    public int getCustomDurationTicks(MinecraftServer server) {
        long timeOfDay = server.getOverworld().getTimeOfDay() % SolarVerdictEvent.TICKS_PER_DAY;
        if (timeOfDay < SolarVerdictEvent.DUSK_TIME) {
            return Math.max(SolarVerdictEvent.DAMAGE_INTERVAL_TICKS, (int) (SolarVerdictEvent.DUSK_TIME - timeOfDay));
        } else {
            return Math.max(SolarVerdictEvent.DAMAGE_INTERVAL_TICKS,
                    (int) ((SolarVerdictEvent.TICKS_PER_DAY - timeOfDay) + SolarVerdictEvent.DUSK_TIME));
        }
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        long timeOfDay = server.getOverworld().getTimeOfDay() % SolarVerdictEvent.TICKS_PER_DAY;
        boolean isCurrentlyDay = SolarVerdictEvent.isDaytime(timeOfDay);

        context.set(SolarVerdictEvent.DAY_ACTIVE_KEY, isCurrentlyDay);

        if (isCurrentlyDay) {
            MerryEvents.LOGGER
                    .info("[SolarVerdictEvent] Started during DAY. All exposed creatures will scorch until dusk.");
        } else {
            MerryEvents.LOGGER.info("[SolarVerdictEvent] Started at NIGHT. Waiting for sunrise to scorch creatures.");
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        ServerWorld overworld = server.getOverworld();
        long timeOfDay = overworld.getTimeOfDay() % SolarVerdictEvent.TICKS_PER_DAY;
        boolean isDay = SolarVerdictEvent.isDaytime(timeOfDay);
        boolean wasDayActive = context.getOrDefault(SolarVerdictEvent.DAY_ACTIVE_KEY, false);

        if (!wasDayActive && isDay) {
            context.set(SolarVerdictEvent.DAY_ACTIVE_KEY, true);
            MerryEvents.LOGGER
                    .info("[SolarVerdictEvent] Dawn has broken. Sunlight is now lethal to exposed creatures.");
            overworld.playSound(null, BlockPos.ofFloored(overworld.getSpawnPos().toCenterPos()),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.WEATHER, 1.0F, 0.8F);
        } else if (wasDayActive && !isDay) {
            context.set(SolarVerdictEvent.DAY_ACTIVE_KEY, false);
            return;
        }

        int currentTick = server.getTicks();
        if (!isDay || currentTick % SolarVerdictEvent.DAMAGE_INTERVAL_TICKS != 0) {
            return;
        }

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            return;
        }

        int startPlayerIndex = Math.floorMod(context.getInt(SolarVerdictEvent.NEXT_PLAYER_INDEX_KEY, 0),
                players.size());
        context.set(SolarVerdictEvent.NEXT_PLAYER_INDEX_KEY, (startPlayerIndex + 1) % players.size());

        int processedTargets = 0;
        Set<UUID> processedEntityIds = new HashSet<>();
        for (int playerOffset = 0; playerOffset < players.size()
                && processedTargets < SolarVerdictEvent.MAX_TARGETS_PER_TICK; playerOffset++) {
            ServerPlayerEntity player = players.get((startPlayerIndex + playerOffset) % players.size());
            if (SpatialTargeting.isHazardImmune(player)) {
                continue;
            }

            ServerWorld world = player.getServerWorld();
            if (!world.getDimension().hasSkyLight() || !world.isDay() || world.isRaining()) {
                continue;
            }

            Box searchArea = player.getBoundingBox().expand(SolarVerdictEvent.PLAYER_SEARCH_RADIUS);
            List<LivingEntity> nearbyTargets = world.getEntitiesByClass(LivingEntity.class, searchArea,
                    living -> living.isAlive()
                            && !living.isInvulnerable()
                            && !living.isFireImmune()
                            && (!(living instanceof ServerPlayerEntity candidatePlayer)
                                    || !SpatialTargeting.isHazardImmune(candidatePlayer))
                            && living.squaredDistanceTo(player.getX(), living.getY(),
                                    player.getZ()) <= SolarVerdictEvent.PLAYER_SEARCH_RADIUS_SQUARED);

            for (LivingEntity living : nearbyTargets) {
                if (processedTargets >= SolarVerdictEvent.MAX_TARGETS_PER_TICK) {
                    break;
                }

                if (!processedEntityIds.add(living.getUuid())) {
                    continue;
                }

                processedTargets++;
                BlockPos eyePos = BlockPos.ofFloored(living.getX(), living.getEyeY(), living.getZ());
                if (!world.isSkyVisible(eyePos) || living.isTouchingWater() || living.isWet() || living.inPowderSnow) {
                    continue;
                }

                ItemStack helmet = living.getEquippedStack(EquipmentSlot.HEAD);
                if (!helmet.isEmpty()) {
                    if (helmet.isDamageable()) {
                        helmet.damage(SolarVerdictEvent.HELMET_DAMAGE, living, EquipmentSlot.HEAD);
                    }

                    living.damage(world.getDamageSources().onFire(), SolarVerdictEvent.HELMET_FIRE_DAMAGE);
                    world.spawnParticles(ParticleTypes.SMOKE, living.getX(), living.getY() + living.getHeight(),
                            living.getZ(), SolarVerdictEvent.HELMET_SMOKE_PARTICLE_COUNT,
                            SolarVerdictEvent.HELMET_SMOKE_SPREAD, SolarVerdictEvent.HELMET_SMOKE_SPREAD,
                            SolarVerdictEvent.HELMET_SMOKE_SPREAD, SolarVerdictEvent.HELMET_SMOKE_SPEED);

                    if (living instanceof ServerPlayerEntity scorchedPlayer
                            && currentTick % (SolarVerdictEvent.DAMAGE_INTERVAL_TICKS * 2) == 0) {
                        scorchedPlayer.playSoundToPlayer(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8F,
                                1.6F);
                    }
                } else {
                    living.setOnFireFor(SolarVerdictEvent.EXPOSED_FIRE_DURATION_SECONDS);
                    living.damage(world.getDamageSources().onFire(), SolarVerdictEvent.EXPOSED_FIRE_DAMAGE);

                    world.spawnParticles(ParticleTypes.FLAME,
                            living.getX(), living.getY() + living.getHeight() * 0.5, living.getZ(),
                            SolarVerdictEvent.EXPOSED_FLAME_PARTICLE_COUNT, SolarVerdictEvent.EXPOSED_FLAME_SPREAD,
                            SolarVerdictEvent.EXPOSED_FLAME_SPREAD,
                            SolarVerdictEvent.EXPOSED_FLAME_SPREAD, SolarVerdictEvent.EXPOSED_FLAME_SPEED);

                    if (living instanceof ServerPlayerEntity scorchedPlayer) {
                        scorchedPlayer.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.PLAYERS, 0.9F,
                                1.0F);
                    }
                }
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[SolarVerdictEvent] Solar Verdict has concluded. Sunlight normalized.");
    }

    private static boolean isDaytime(long timeOfDay) {
        return timeOfDay < SolarVerdictEvent.DUSK_TIME;
    }
}