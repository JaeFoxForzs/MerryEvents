package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import net.minecraft.entity.Entity;
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
import org.jetbrains.annotations.Nullable;

/**
 * Global astronomical corruption event belonging to EventCategory.WEATHER.
 * Transforms unobstructed daytime sunlight into a lethal heat source damaging
 * exposed entities.
 */
public final class SolarVerdictEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("solar_verdict");
    private static final String DAY_ACTIVE_KEY = "day_active";
    private static final long DUSK_TIME = 12500L;

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

    /**
     * Calculates the exact remaining ticks until dusk, or spans the remaining night
     * plus full next day.
     *
     * @param server Active MinecraftServer instance
     * @return Total ticks until the next solar dusk
     */
    @Override
    public int getCustomDurationTicks(MinecraftServer server) {
        long timeOfDay = server.getOverworld().getTimeOfDay() % 24000L;
        if (timeOfDay < DUSK_TIME) {
            return Math.max(20, (int) (DUSK_TIME - timeOfDay));
        } else {
            return Math.max(20, (int) ((24000L - timeOfDay) + DUSK_TIME));
        }
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        long timeOfDay = server.getOverworld().getTimeOfDay() % 24000L;
        boolean isCurrentlyDay = isDaytime(timeOfDay);

        context.set(DAY_ACTIVE_KEY, isCurrentlyDay);

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
        long timeOfDay = overworld.getTimeOfDay() % 24000L;
        boolean isDay = isDaytime(timeOfDay);
        boolean wasDayActive = context.getOrDefault(DAY_ACTIVE_KEY, false);

        if (!wasDayActive && isDay) {
            context.set(DAY_ACTIVE_KEY, true);
            MerryEvents.LOGGER
                    .info("[SolarVerdictEvent] Dawn has broken. Sunlight is now lethal to exposed creatures.");
            overworld.playSound(null, BlockPos.ofFloored(overworld.getSpawnPos().toCenterPos()),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.WEATHER, 1.0F, 0.8F);
        } else if (wasDayActive && !isDay) {
            context.set(DAY_ACTIVE_KEY, false);
            return;
        }

        if (!isDay || remainingTicks % 20 != 0) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            if (!world.getDimension().hasSkyLight() || !world.isDay() || world.isRaining()) {
                continue;
            }

            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvulnerable()
                        || living.isFireImmune()) {
                    continue;
                }

                if (living instanceof ServerPlayerEntity player && SpatialTargeting.isHazardImmune(player)) {
                    continue;
                }

                BlockPos eyePos = BlockPos.ofFloored(living.getX(), living.getEyeY(), living.getZ());
                if (!world.isSkyVisible(eyePos) || living.isTouchingWater() || living.isWet() || living.inPowderSnow) {
                    continue;
                }

                ItemStack helmet = living.getEquippedStack(EquipmentSlot.HEAD);
                if (!helmet.isEmpty()) {
                    if (helmet.isDamageable()) {
                        helmet.damage(1, living, EquipmentSlot.HEAD);
                    }

                    living.damage(world.getDamageSources().onFire(), 1.0F);
                    world.spawnParticles(ParticleTypes.SMOKE, living.getX(), living.getY() + living.getHeight(),
                            living.getZ(), 2, 0.1, 0.1, 0.1, 0.02);

                    if (living instanceof ServerPlayerEntity player && remainingTicks % 40 == 0) {
                        player.playSoundToPlayer(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8F, 1.6F);
                    }
                } else {
                    living.setOnFireFor(8.0F);
                    living.damage(world.getDamageSources().onFire(), 4.0F);

                    world.spawnParticles(ParticleTypes.FLAME, living.getX(), living.getY() + living.getHeight() * 0.5,
                            living.getZ(), 4, 0.2, 0.2, 0.2, 0.05);

                    if (living instanceof ServerPlayerEntity player) {
                        player.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_BURN, SoundCategory.PLAYERS, 0.9F, 1.0F);
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
        return timeOfDay < DUSK_TIME;
    }
}