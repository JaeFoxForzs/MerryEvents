package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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

import java.util.ArrayList;
import java.util.List;

public final class SolarVerdictEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("solar_verdict");
    private static final String DAY_ACTIVE_KEY = "day_active";
    private static final long DUSK_TIME = 12500L; // Момент заката / наступления сумерек

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
    public int getCustomDurationTicks(MinecraftServer server) {
        long timeOfDay = server.getOverworld().getTimeOfDay() % 24000L;
        if (timeOfDay < DUSK_TIME) {
            // Сейчас день: длительность ровно до заката
            return (int) (DUSK_TIME - timeOfDay);
        } else {
            // Сейчас ночь: остаток ночи + весь следующий день
            return (int) ((24000L - timeOfDay) + DUSK_TIME);
        }
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        long timeOfDay = server.getOverworld().getTimeOfDay() % 24000L;
        boolean isCurrentlyDay = isDaytime(timeOfDay);

        context.set(DAY_ACTIVE_KEY, isCurrentlyDay);

        if (isCurrentlyDay) {
            MerryEvents.LOGGER.info("[SolarVerdictEvent] Started during DAY. All exposed creatures will scorch until dusk.");
        } else {
            MerryEvents.LOGGER.info("[SolarVerdictEvent] Started at NIGHT. Waiting for sunrise to scorch creatures.");
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context, int remainingTicks) {
        ServerWorld overworld = server.getOverworld();
        long timeOfDay = overworld.getTimeOfDay() % 24000L;
        boolean isDay = isDaytime(timeOfDay);
        boolean wasDayActive = context.getOrDefault(DAY_ACTIVE_KEY, false);

        // 1. Обработка фаз дня и ночи
        if (!wasDayActive && isDay) {
            context.set(DAY_ACTIVE_KEY, true);
            MerryEvents.LOGGER.info("[SolarVerdictEvent] Dawn has broken! Sunlight is now lethal to all creatures.");
            overworld.playSound(null, BlockPos.ofFloored(overworld.getSpawnPos().toCenterPos()),
                    SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.WEATHER, 1.0F, 0.8F);
        } else if (wasDayActive && !isDay) {
            MerryEvents.LOGGER.info("[SolarVerdictEvent] Day has ended. Terminating event.");
            EventManager.getInstance().stopGlobalEvent(server, ID);
            return;
        }

        // Если сейчас ночь или до рассвета — никто не горит
        if (!isDay) {
            return;
        }

        // Наносим урон раз в 1 секунду (20 тиков)
        if (remainingTicks % 20 != 0) {
            return;
        }

        // 2. Обрабатываем все миры, где есть дневной солнечный свет
        for (ServerWorld world : server.getWorlds()) {
            if (!world.getDimension().hasSkyLight() || !world.isDay() || world.isRaining()) {
                continue;
            }

            // Создаем снимок живых сущностей для исключения ConcurrentModificationException при их смерти
            List<LivingEntity> livingEntities = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    livingEntities.add(living);
                }
            }

            // 3. Применяем солнечный жар к существам
            for (LivingEntity living : livingEntities) {
                if (!living.isAlive()) {
                    continue;
                }

                // Игнорируем игроков в творческом режиме или спектаторе
                if (living instanceof PlayerEntity player && (player.isCreative() || player.isSpectator() || player.isInvulnerable())) {
                    continue;
                }

                // Игнорируем существ с естественным иммунитетом к огню (страйдеры, ифриты и т.д.)
                if (living.isFireImmune() || living.isInvulnerable()) {
                    continue;
                }

                // Проверяем позицию глаз на прямое воздействие солнца
                BlockPos eyePos = BlockPos.ofFloored(living.getX(), living.getEyeY(), living.getZ());
                if (!world.isSkyVisible(eyePos)) {
                    continue;
                }

                // Вода, дождь или рыхлый снег спасают от горения
                if (living.isTouchingWater() || living.isWet() || living.inPowderSnow) {
                    continue;
                }

                // Проверка экипированного шлема
                ItemStack helmet = living.getEquippedStack(EquipmentSlot.HEAD);
                if (!helmet.isEmpty()) {
                    // Шлем принимает удар на себя и плавится
                    if (helmet.isDamageable()) {
                        helmet.damage(1, living, EquipmentSlot.HEAD);
                    }

                    living.damage(world.getDamageSources().onFire(), 1.0F);
                    world.spawnParticles(ParticleTypes.SMOKE, living.getX(), living.getY() + living.getHeight(), living.getZ(), 2, 0.1, 0.1, 0.1, 0.02);

                    if (living instanceof ServerPlayerEntity player && remainingTicks % 40 == 0) {
                        player.playSoundToPlayer(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8F, 1.6F);
                    }
                } else {
                    // Смертельное прямое горение
                    living.setOnFireFor(8.0F);
                    living.damage(world.getDamageSources().onFire(), 4.0F); // 2 сердца урона в секунду

                    world.spawnParticles(ParticleTypes.FLAME, living.getX(), living.getY() + living.getHeight() * 0.5, living.getZ(), 4, 0.2, 0.2, 0.2, 0.05);

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