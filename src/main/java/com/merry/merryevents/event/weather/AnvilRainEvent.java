package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Global aerial hazard event belonging to EventCategory.WEATHER.
 * Precipitates falling anvils above sky-exposed players at rapid intervals.
 */
public final class AnvilRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("anvil_rain");

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
        MerryEvents.LOGGER.info("[AnvilRainEvent] Atmospheric density destabilized: anvil shower initiated.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context, int remainingTicks) {
        if (remainingTicks % 15 != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }

            ServerWorld world = player.getServerWorld();
            BlockPos playerPos = player.getBlockPos();

            if (!world.isSkyVisible(playerPos)) {
                continue;
            }

            Random random = player.getRandom();
            double offsetX = (random.nextDouble() - 0.5) * 16.0;
            double offsetZ = (random.nextDouble() - 0.5) * 16.0;

            int targetX = (int) (player.getX() + offsetX);
            int targetZ = (int) (player.getZ() + offsetZ);
            int spawnY = Math.min(world.getTopY() - 2, player.getBlockY() + 24 + random.nextInt(8));

            BlockPos spawnPos = new BlockPos(targetX, spawnY, targetZ);
            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(spawnPos.getX()), ChunkSectionPos.getSectionCoord(spawnPos.getZ()))) {
                continue;
            }

            FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(world, spawnPos, Blocks.ANVIL.getDefaultState());
            anvil.setHurtEntities(3.0F, 40);
            context.trackEntity(anvil.getUuid());

            world.playSound(null, spawnPos, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.WEATHER, 1.2F, 0.4F);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[AnvilRainEvent] Anvil shower normalized.");
    }
}