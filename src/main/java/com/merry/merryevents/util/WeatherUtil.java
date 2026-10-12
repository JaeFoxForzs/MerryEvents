package com.merry.merryevents.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.biome.Biome;

/**
 * Deterministic utility verifying sky exposure and active precipitation state.
 */
public final class WeatherUtil {
    private WeatherUtil() {
    }

    /**
     * Determines whether target coordinate is directly exposed to the sky without
     * solid obstructions.
     * Verifies chunk loading boundaries prior to querying heightmaps to eliminate
     * synchronous thread stalls.
     *
     * @param world Active server world
     * @param pos   Target block position to test
     * @return True if no solid block obstructs the vertical column to the sky
     */
    public static boolean isExposedToSky(ServerWorld world, BlockPos pos) {
        if (!world.getDimension().hasSkyLight()) {
            return false;
        }

        int chunkX = ChunkSectionPos.getSectionCoord(pos.getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ());
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }

        return world.isSkyVisible(pos);
    }

    /**
     * Determines whether an entity is directly exposed to active rainfall in a
     * rainy biome.
     * Verifies overhead clearance at both feet and eye elevation to guarantee
     * shelter under ceilings.
     *
     * @param world  Active server world
     * @param pos    Target position
     * @param entity Target living entity
     * @return True if active rain precipitates onto the entity
     */
    public static boolean isExposedToRain(ServerWorld world, BlockPos pos, LivingEntity entity) {
        if (!world.isRaining()) {
            return false;
        }

        BlockPos checkPos = entity != null ? BlockPos.ofFloored(entity.getX(), entity.getEyeY(), entity.getZ()) : pos;
        if (!WeatherUtil.isExposedToSky(world, checkPos)) {
            return false;
        }

        int chunkX = ChunkSectionPos.getSectionCoord(checkPos.getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(checkPos.getZ());
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }

        return world.getBiome(checkPos).value().getPrecipitation(checkPos) == Biome.Precipitation.RAIN;
    }

    /**
     * Forces rainfall weather and synchronizes client render gradients.
     *
     * @param server        Active Minecraft server instance
     * @param durationTicks Duration of forced rainfall in ticks
     */
    public static void startRain(MinecraftServer server, int durationTicks) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getDimension().hasSkyLight()) {
                world.setWeather(0, durationTicks, true, false);
                server.getPlayerManager().sendToDimension(
                        new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STARTED, 0.0F),
                        world.getRegistryKey());
                server.getPlayerManager().sendToDimension(
                        new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, 1.0F),
                        world.getRegistryKey());
            }
        }
    }

    /**
     * Terminates storm weather and zeroes client rain opacity across surface
     * dimensions.
     *
     * @param server Active Minecraft server instance
     */
    public static void stopRain(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getDimension().hasSkyLight()) {
                world.setWeather(6000, 0, false, false);
                server.getPlayerManager().sendToDimension(
                        new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STOPPED, 0.0F),
                        world.getRegistryKey());
                server.getPlayerManager().sendToDimension(
                        new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, 0.0F),
                        world.getRegistryKey());
            }
        }
    }
}