package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Global meteorological incendiary event belonging to EventCategory.WEATHER.
 * Rains non-explosive small fireballs with randomized interval and dispersion
 * radius.
 */
public final class FireStormEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("fire_storm");
    private static final String INTERVAL_KEY = "cadence_ticks";
    private static final String RADIUS_KEY = "dispersion_radius";

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
        Random random = server.getOverworld().getRandom();
        int cadenceTicks = 12 + random.nextInt(18);
        double dispersionRadius = 10.0 + (random.nextDouble() * 14.0);

        context.set(INTERVAL_KEY, cadenceTicks);
        context.set(RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info("[FireStormEvent] Incendiary storm ignited: interval={}t, radius={}",
                cadenceTicks, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        int cadence = context.getInt(INTERVAL_KEY, 20);
        if (remainingTicks % cadence != 0) {
            return;
        }

        double radius = context.getDouble(RADIUS_KEY, 16.0);

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
            double offsetX = (random.nextDouble() - 0.5) * radius * 2.0;
            double offsetZ = (random.nextDouble() - 0.5) * radius * 2.0;

            double targetX = player.getX() + offsetX;
            double targetZ = player.getZ() + offsetZ;
            double targetY = Math.min(world.getTopY() - 2, player.getY() + 20.0 + random.nextInt(6));

            int chunkX = ChunkSectionPos.getSectionCoord((int) targetX);
            int chunkZ = ChunkSectionPos.getSectionCoord((int) targetZ);
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            SmallFireballEntity fireball = EntityType.SMALL_FIREBALL.create(world);
            if (fireball != null) {
                fireball.refreshPositionAndAngles(targetX, targetY, targetZ, 0.0F, 90.0F);
                fireball.setVelocity(new Vec3d(0.0, -1.1, 0.0));
                world.spawnEntity(fireball);
                context.trackEntity(fireball.getUuid());

                world.playSound(null, targetX, targetY, targetZ,
                        SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.WEATHER, 0.7F, 0.8F);
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[FireStormEvent] Incendiary storm neutralized.");
    }
}