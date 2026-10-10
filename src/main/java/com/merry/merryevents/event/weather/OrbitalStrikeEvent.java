package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.network.OrbitalStrikeSpawnPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class OrbitalStrikeEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("orbital_strike");
    private static final String STRIKES_KEY = "pending_strikes";
    private static final int WARMUP_TICKS = 35; // 1.75 сек наведения луча до удара

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
        context.set(STRIKES_KEY, new ArrayList<StrikeTarget>());
        MerryEvents.LOGGER.info("[OrbitalStrikeEvent] Orbital targeting satellite calibrated. Bombardment initiated.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context, int remainingTicks) {
        List<StrikeTarget> strikes = context.getOrDefault(STRIKES_KEY, null);
        if (strikes == null) {
            strikes = new ArrayList<>();
            context.set(STRIKES_KEY, strikes);
        }

        // 1. Каждые 25 тиков генерируем новые цели вокруг игроков
        if (remainingTicks % 25 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSpectator()) {
                    continue;
                }

                ServerWorld world = player.getServerWorld();
                Random rand = player.getRandom();

                // 2 точки удара на каждого игрока в радиусе 6-28 блоков
                for (int i = 0; i < 2; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2.0;
                    double dist = 6.0 + rand.nextDouble() * 22.0;
                    int targetX = (int) (player.getX() + Math.cos(angle) * dist);
                    int targetZ = (int) (player.getZ() + Math.sin(angle) * dist);
                    int targetY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, targetX, targetZ);

                    BlockPos strikePos = new BlockPos(targetX, targetY, targetZ);
                    strikes.add(new StrikeTarget(world, strikePos, WARMUP_TICKS));

                    // Отправляем пакет клиентам для рендеринга шейдерного луча
                    OrbitalStrikeSpawnPayload payload = new OrbitalStrikeSpawnPayload(strikePos, WARMUP_TICKS);
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(p, payload);
                    }
                }
            }
        }

        // 2. Обрабатываем активные метки на земле и детонацию
        List<StrikeTarget> toRemove = new ArrayList<>();
        for (StrikeTarget strike : strikes) {
            strike.remainingWarmup--;
            ServerWorld world = strike.world;
            BlockPos pos = strike.pos;

            if (strike.remainingWarmup > 0) {
                // Звуковой сигнал заряда лазера
                if (strike.remainingWarmup % 10 == 0) {
                    world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.WEATHER, 1.2F, 1.8F);
                }
            } else {
                // 3. Нанесение взрыва
                world.spawnParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0);
                world.spawnParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.2);

                // Звуки взрыва (с .value() только для ENTITY_GENERIC_EXPLODE)
                world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.WEATHER, 4.0F, 0.6F);
                world.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 2.5F, 0.9F);

                // Взрыв блока/мобов
                world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5.0F, World.ExplosionSourceType.MOB);
                toRemove.add(strike);
            }
        }

        strikes.removeAll(toRemove);
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