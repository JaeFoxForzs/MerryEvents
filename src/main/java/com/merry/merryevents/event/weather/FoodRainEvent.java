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
 * Precipitates consumable rations above sky-exposed players at steady
 * intervals.
 */
public final class FoodRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("food_rain");

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
        MerryEvents.LOGGER.info("[FoodRainEvent] Stratospheric provision cascade initiated.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

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
            double offsetX = (random.nextDouble() - 0.5) * 12.0;
            double offsetZ = (random.nextDouble() - 0.5) * 12.0;

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