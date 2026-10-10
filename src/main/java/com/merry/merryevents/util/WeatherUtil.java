package com.merry.merryevents.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/**
 * High-performance deterministic utility for spatial sky exposure and
 * precipitation checks.
 * Completely eliminates water and lava immersion roof occlusion exploits using
 * O(1) heightmap evaluation.
 */
public final class WeatherUtil {
    private WeatherUtil() {
    }

    /**
     * Determines whether target coordinate is directly exposed to the sky.
     * Prevents fluid surfaces (both water and lava) from acting as artificial
     * roofs.
     *
     * @param world Active server world
     * @param pos   Target block position to test
     * @return True if no solid obstruction blocks the vertical column to the sky
     */
    public static boolean isExposedToSky(ServerWorld world, BlockPos pos) {
        if (!world.getDimension().hasSkyLight()) {
            return false;
        }

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (pos.getY() >= topY - 1) {
            return true;
        }

        BlockPos topPos = new BlockPos(pos.getX(), topY - 1, pos.getZ());
        FluidState fluidState = world.getFluidState(topPos);
        return !fluidState.isEmpty();
    }

    /**
     * Determines whether an entity is directly exposed to active rainfall.
     * Combines world storm state, biome precipitation type, and exploit-proof roof
     * checks.
     *
     * @param world  Active server world
     * @param pos    Target position
     * @param entity Target living entity
     * @return True if active rain precipitates onto the entity
     */
    public static boolean isExposedToRain(ServerWorld world, BlockPos pos, LivingEntity entity) {
        if (!world.isRaining() || !isExposedToSky(world, pos)) {
            return false;
        }

        return world.getBiome(pos).value().getPrecipitation(pos) == Biome.Precipitation.RAIN;
    }

    /**
     * Instantly forces storm weather and synchronizes client render gradients with
     * zero transition delay.
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
     * Instantly terminates storm weather and zeroes client rain opacity across all
     * surface dimensions.
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