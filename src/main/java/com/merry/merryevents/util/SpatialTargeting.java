package com.merry.merryevents.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Deterministic spatial targeting utility computing safe surface and sky
 * coordinates within loaded chunks.
 */
public final class SpatialTargeting {
    private SpatialTargeting() {
    }

    /**
     * Determines whether the given player is strictly immune to environmental
     * targeting due to game modes.
     *
     * @param player Target player instance
     * @return True if player is in spectator or creative mode
     */
    public static boolean isHazardImmune(PlayerEntity player) {
        return player.isSpectator() || player.isCreative();
    }

    /**
     * Computes a safe ground-level strike position directly on the world surface
     * within loaded chunks.
     *
     * @param world     Active server world
     * @param player    Center player
     * @param radius    Maximum radial offset
     * @param heightmap Type of heightmap to query
     * @return Target surface BlockPos, or null if target chunk is not currently
     *         loaded
     */
    @Nullable
    public static BlockPos findRandomSurfacePosition(ServerWorld world, PlayerEntity player, double radius,
            Heightmap.Type heightmap) {
        Random random = player.getRandom();
        double offsetX = (random.nextDouble() - 0.5) * radius * 2.0;
        double offsetZ = (random.nextDouble() - 0.5) * radius * 2.0;

        int targetX = MathHelper.floor(player.getX() + offsetX);
        int targetZ = MathHelper.floor(player.getZ() + offsetZ);

        int chunkX = ChunkSectionPos.getSectionCoord(targetX);
        int chunkZ = ChunkSectionPos.getSectionCoord(targetZ);

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }

        int surfaceY = world.getTopY(heightmap, targetX, targetZ);
        return new BlockPos(targetX, surfaceY, targetZ);
    }

    /**
     * Computes a safe precipitation strike position above the target player within
     * a loaded chunk.
     *
     * @param world                Active server world
     * @param player               Center player
     * @param radius               Maximum radial offset
     * @param heightmap            Type of heightmap to query
     * @param minHeightAbovePlayer Minimum height clearance above the player
     * @param extraHeightVariation Randomized extra vertical clearance
     * @return Target BlockPos, or null if target chunk is not currently loaded
     */
    @Nullable
    public static BlockPos findRandomSkyPosition(ServerWorld world, PlayerEntity player, double radius,
            Heightmap.Type heightmap, int minHeightAbovePlayer,
            int extraHeightVariation) {
        Random random = player.getRandom();
        double offsetX = (random.nextDouble() - 0.5) * radius * 2.0;
        double offsetZ = (random.nextDouble() - 0.5) * radius * 2.0;

        int targetX = MathHelper.floor(player.getX() + offsetX);
        int targetZ = MathHelper.floor(player.getZ() + offsetZ);

        int chunkX = ChunkSectionPos.getSectionCoord(targetX);
        int chunkZ = ChunkSectionPos.getSectionCoord(targetZ);

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }

        int surfaceY = world.getTopY(heightmap, targetX, targetZ);
        int spawnY = Math.min(world.getTopY() - 2,
                Math.max(player.getBlockY() + minHeightAbovePlayer, surfaceY + minHeightAbovePlayer - 4)
                        + (extraHeightVariation > 0 ? random.nextInt(extraHeightVariation) : 0));

        return new BlockPos(targetX, spawnY, targetZ);
    }
}