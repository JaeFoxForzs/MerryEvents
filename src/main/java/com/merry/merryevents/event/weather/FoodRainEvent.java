package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * Global aerial provision event belonging to EventCategory.WEATHER.
 * Precipitates rations with randomized frequency and area spread.
 */
public final class FoodRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("food_rain");
    private static final String INTERVAL_KEY = "cadence_ticks";
    private static final String RADIUS_KEY = "dispersion_radius";

    private static final Item[] EDIBLE_DROPS = new Item[] {
            Items.APPLE,
            Items.BREAD,
            Items.COOKED_BEEF,
            Items.CARROT
    };

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
        int cadenceTicks = 15 + random.nextInt(20);
        double dispersionRadius = 6.0 + (random.nextDouble() * 10.0);

        context.set(INTERVAL_KEY, cadenceTicks);
        context.set(RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info("[FoodRainEvent] Stratospheric provision cascade initiated: interval={}t, radius={}",
                cadenceTicks, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        int cadence = context.getInt(INTERVAL_KEY, 20);
        if (remainingTicks % cadence != 0) {
            return;
        }

        double radius = context.getDouble(RADIUS_KEY, 12.0);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) {
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
            double targetY = Math.min(world.getTopY() - 2, player.getY() + 18.0 + random.nextInt(6));

            int chunkX = ChunkSectionPos.getSectionCoord((int) targetX);
            int chunkZ = ChunkSectionPos.getSectionCoord((int) targetZ);
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            Item chosenItem = EDIBLE_DROPS[random.nextInt(EDIBLE_DROPS.length)];
            ItemEntity drop = new ItemEntity(world, targetX, targetY, targetZ, new ItemStack(chosenItem));
            drop.setVelocity(0.0, -0.25, 0.0);
            world.spawnEntity(drop);
            context.trackEntity(drop.getUuid());

            world.playSound(null, targetX, targetY, targetZ,
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.WEATHER, 0.4F, 0.6F);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[FoodRainEvent] Provision precipitation normalized.");
    }
}