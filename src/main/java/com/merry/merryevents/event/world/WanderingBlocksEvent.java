package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific spatial anomaly event belonging to EventCategory.WORLD.
 * Randomly extracts terrain blocks within a randomized source radius around the
 * player
 * and relocates them to a distant destination radius accompanied by Enderman
 * teleportation cues.
 */
public final class WanderingBlocksEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("wandering_blocks");
    private static final String REMAINING_BLOCKS_KEY = "remaining_blocks";
    private static final String SRC_MIN_RAD_KEY = "src_min_radius";
    private static final String SRC_MAX_RAD_KEY = "src_max_radius";
    private static final String DST_MIN_RAD_KEY = "dst_min_radius";
    private static final String DST_MAX_RAD_KEY = "dst_max_radius";

    private static final int MIN_BLOCKS = 32;
    private static final int MAX_BLOCKS = 256;
    private static final int MIN_SOURCE_RADIUS = 3;
    private static final int MAX_SOURCE_RADIUS = 12;
    private static final int MIN_DEST_RADIUS = 8;
    private static final int MAX_DEST_RADIUS = 24;
    private static final int MAX_SEARCH_ATTEMPTS = 8;
    private static final int MAX_VERTICAL_SOURCE_OFFSET = 3;
    private static final int CHUNK_SHIFT = 4;
    private static final double DEFAULT_SOURCE_MIN_RADIUS = 4.0;
    private static final double DEFAULT_SOURCE_MAX_RADIUS = 16.0;
    private static final double DEFAULT_DEST_MIN_RADIUS = 8.0;
    private static final double DEFAULT_DEST_MAX_RADIUS = 32.0;
    private static final float MIN_BLOCK_HARDNESS = 0.0F;
    private static final float ENDERMAN_SOUND_VOLUME = 0.6F;
    private static final float ENDERMAN_SOURCE_SOUND_PITCH = 1.2F;
    private static final float ENDERMAN_TARGET_SOUND_PITCH = 1.0F;

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WORLD;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer == null) {
            return;
        }

        Random random = targetPlayer.getRandom();
        int totalBlocks = MIN_BLOCKS + random.nextInt(MAX_BLOCKS - MIN_BLOCKS + 1);

        double srcMin = MIN_SOURCE_RADIUS + random.nextInt(3);
        double srcMax = MAX_SOURCE_RADIUS + random.nextInt(5);
        double dstMin = MIN_DEST_RADIUS + random.nextInt(5);
        double dstMax = MAX_DEST_RADIUS + random.nextInt(9);

        context.set(REMAINING_BLOCKS_KEY, totalBlocks);
        context.set(SRC_MIN_RAD_KEY, srcMin);
        context.set(SRC_MAX_RAD_KEY, srcMax);
        context.set(DST_MIN_RAD_KEY, dstMin);
        context.set(DST_MAX_RAD_KEY, dstMax);

        MerryEvents.LOGGER.info(
                "[WanderingBlocksEvent] Dislocation anomaly active for {} (Quota: {} blocks, Source radius: {}-{}m, Target radius: {}-{}m)",
                targetPlayer.getName().getString(), totalBlocks, (int) srcMin, (int) srcMax, (int) dstMin,
                (int) dstMax);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks <= 0) {
            return;
        }

        int blocksRemaining = context.getInt(REMAINING_BLOCKS_KEY, 0);
        if (blocksRemaining <= 0) {
            return;
        }

        Random random = targetPlayer.getRandom();
        double expectedRate = (double) blocksRemaining / (double) remainingTicks;
        int toMoveCount = (int) expectedRate;
        double fractionalRate = expectedRate - (double) toMoveCount;

        if (random.nextDouble() < fractionalRate) {
            toMoveCount++;
        }

        if (toMoveCount <= 0) {
            return;
        }

        double srcMin = context.getDouble(SRC_MIN_RAD_KEY, DEFAULT_SOURCE_MIN_RADIUS);
        double srcMax = context.getDouble(SRC_MAX_RAD_KEY, DEFAULT_SOURCE_MAX_RADIUS);
        double dstMin = context.getDouble(DST_MIN_RAD_KEY, DEFAULT_DEST_MIN_RADIUS);
        double dstMax = context.getDouble(DST_MAX_RAD_KEY, DEFAULT_DEST_MAX_RADIUS);

        ServerWorld world = targetPlayer.getServerWorld();
        BlockPos playerPos = targetPlayer.getBlockPos();
        BlockPos.Mutable sourcePos = new BlockPos.Mutable();
        BlockPos.Mutable targetPos = new BlockPos.Mutable();
        int successfullyMoved = 0;

        for (int i = 0; i < toMoveCount && blocksRemaining > 0; i++) {
            boolean displaced = false;

            for (int attempt = 0; attempt < MAX_SEARCH_ATTEMPTS; attempt++) {
                double srcAngle = random.nextDouble() * Math.PI * 2.0;
                double srcRadius = srcMin + random.nextDouble() * (srcMax - srcMin);
                int srcX = MathHelper.floor(playerPos.getX() + Math.cos(srcAngle) * srcRadius);
                int srcZ = MathHelper.floor(playerPos.getZ() + Math.sin(srcAngle) * srcRadius);
                int srcY = playerPos.getY() - random.nextInt(MAX_VERTICAL_SOURCE_OFFSET);

                sourcePos.set(srcX, srcY, srcZ);

                if (!world.isChunkLoaded(sourcePos.getX() >> CHUNK_SHIFT, sourcePos.getZ() >> CHUNK_SHIFT)) {
                    continue;
                }

                BlockState sourceState = world.getBlockState(sourcePos);
                if (sourceState.isAir() || sourceState.getHardness(world, sourcePos) < MIN_BLOCK_HARDNESS
                        || sourceState.hasBlockEntity() || !sourceState.getFluidState().isEmpty()) {
                    continue;
                }

                double dstAngle = random.nextDouble() * Math.PI * 2.0;
                double dstRadius = dstMin + random.nextDouble() * (dstMax - dstMin);
                int dstX = MathHelper.floor(playerPos.getX() + Math.cos(dstAngle) * dstRadius);
                int dstZ = MathHelper.floor(playerPos.getZ() + Math.sin(dstAngle) * dstRadius);

                if (!world.isChunkLoaded(dstX >> CHUNK_SHIFT, dstZ >> CHUNK_SHIFT)) {
                    continue;
                }

                int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, dstX, dstZ);
                targetPos.set(dstX, surfaceY, dstZ);

                BlockState destState = world.getBlockState(targetPos);
                if (!destState.isAir() && !destState.isReplaceable()) {
                    continue;
                }

                world.setBlockState(sourcePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(targetPos, sourceState, Block.NOTIFY_ALL);

                world.playSound(null, sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5,
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, ENDERMAN_SOUND_VOLUME,
                        ENDERMAN_SOURCE_SOUND_PITCH);
                world.playSound(null, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, ENDERMAN_SOUND_VOLUME,
                        ENDERMAN_TARGET_SOUND_PITCH);

                world.spawnParticles(ParticleTypes.PORTAL, sourcePos.getX() + 0.5, sourcePos.getY() + 0.5,
                        sourcePos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.1);
                world.spawnParticles(ParticleTypes.PORTAL, targetPos.getX() + 0.5, targetPos.getY() + 0.5,
                        targetPos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.1);

                successfullyMoved++;
                blocksRemaining--;
                displaced = true;
                break;
            }

            if (!displaced) {
                break;
            }
        }

        if (successfullyMoved > 0) {
            context.set(REMAINING_BLOCKS_KEY, blocksRemaining);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[WanderingBlocksEvent] Dislocation anomaly normalized for {}",
                    targetPlayer.getName().getString());
        }
    }
}