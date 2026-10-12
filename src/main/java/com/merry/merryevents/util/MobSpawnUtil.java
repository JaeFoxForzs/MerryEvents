package com.merry.merryevents.util;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Finds safe ground positions for mobs relative to player elevation and
 * geometry.
 */
public final class MobSpawnUtil {
    private MobSpawnUtil() {
    }

    /**
     * Finds a loaded, collision-free ground position within the given distance
     * range,
     * probing floor blocks around the player's vertical elevation.
     *
     * @param world       Target server world
     * @param mob         Mob entity used for collision and bounding box checks
     * @param center      Player entity establishing search origin
     * @param minDistance Minimum horizontal distance from the player
     * @param maxDistance Maximum horizontal distance from the player
     * @param attempts    Number of candidate coordinates to evaluate
     * @return Safe ground coordinate vector, or null if no valid position was found
     */
    @Nullable
    public static Vec3d findRandomGroundPosition(ServerWorld world, MobEntity mob, ServerPlayerEntity center,
            double minDistance, double maxDistance, int attempts) {
        if (!Double.isFinite(minDistance) || !Double.isFinite(maxDistance) || minDistance < 0.0
                || maxDistance < minDistance || attempts < 1) {
            throw new IllegalArgumentException("Invalid mob spawn search parameters");
        }

        Random random = center.getRandom();
        int playerY = center.getBlockY();

        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            int blockX = MathHelper.floor(x);
            int blockZ = MathHelper.floor(z);

            if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                continue;
            }

            // Probe floor blocks vertically around player elevation to support caves and
            // interiors
            for (int dy = 4; dy >= -5; dy--) {
                BlockPos spawnBlock = new BlockPos(blockX, playerY + dy, blockZ);
                if (world.isOutOfHeightLimit(spawnBlock)) {
                    continue;
                }

                BlockPos groundBlock = spawnBlock.down();
                BlockState groundState = world.getBlockState(groundBlock);
                if (groundState.getCollisionShape(world, groundBlock).isEmpty()
                        || !groundState.getFluidState().isEmpty()) {
                    continue;
                }

                mob.refreshPositionAndAngles(x, playerY + dy, z, random.nextFloat() * 360.0F, 0.0F);
                if (world.isSpaceEmpty(mob, mob.getBoundingBox()) && !world.containsFluid(mob.getBoundingBox())) {
                    return new Vec3d(x, playerY + dy, z);
                }
            }
        }

        return null;
    }
}