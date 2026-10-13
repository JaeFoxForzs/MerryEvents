package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialTargeting;
import com.merry.merryevents.util.WeatherUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Global aerial provision event belonging to EventCategory.WEATHER.
 * Precipitates any valid food item registered in the game registry above
 * sky-exposed players.
 */
public final class FoodRainEvent implements ChaosEvent {
    private static final String INTERVAL_KEY = "cadence_ticks";
    private static final String RADIUS_KEY = "dispersion_radius";
    private static final String REMAINING_FOOD_KEY = "remaining_food";

    private static final int MIN_FOOD_QUOTA = 128;
    private static final int MAX_FOOD_QUOTA = 512;
    private static final int MIN_CADENCE_TICKS = 15;
    private static final int MAX_CADENCE_TICKS = 34;
    private static final double MIN_DISPERSION_RADIUS = 6.0;
    private static final double MAX_DISPERSION_RADIUS = 16.0;
    private static final Heightmap.Type SPAWN_HEIGHTMAP = Heightmap.Type.MOTION_BLOCKING;
    private static final int MIN_HEIGHT_ABOVE_PLAYER = 18;
    private static final int EXTRA_HEIGHT_VARIATION = 6;
    private static final double DOWNWARD_SPEED = 0.25;

    private static final Item[] ALL_FOOD_ITEMS;

    static {
        List<Item> foods = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item.getComponents().contains(DataComponentTypes.FOOD)) {
                foods.add(item);
            }
        }

        if (foods.isEmpty()) {
            foods.add(Items.APPLE);
            foods.add(Items.BREAD);
        }

        ALL_FOOD_ITEMS = foods.toArray(new Item[0]);
    }

    @Override
    public Identifier getId() {
        return EventIds.FOOD_RAIN;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        Random random = server.getOverworld().getRandom();
        int foodQuota = FoodRainEvent.MIN_FOOD_QUOTA + random.nextInt(FoodRainEvent.MAX_FOOD_QUOTA - FoodRainEvent.MIN_FOOD_QUOTA + 1);
        int cadenceTicks = FoodRainEvent.MIN_CADENCE_TICKS + random.nextInt(FoodRainEvent.MAX_CADENCE_TICKS - FoodRainEvent.MIN_CADENCE_TICKS + 1);
        double dispersionRadius = FoodRainEvent.MIN_DISPERSION_RADIUS
                + random.nextDouble() * (FoodRainEvent.MAX_DISPERSION_RADIUS - FoodRainEvent.MIN_DISPERSION_RADIUS);

        context.set(FoodRainEvent.REMAINING_FOOD_KEY, foodQuota);
        context.set(FoodRainEvent.INTERVAL_KEY, cadenceTicks);
        context.set(FoodRainEvent.RADIUS_KEY, dispersionRadius);

        MerryEvents.LOGGER.info(
                "[FoodRainEvent] Stratospheric provision cascade initiated (Pool: {} foods, quota: {} items): interval={}t, radius={}",
                FoodRainEvent.ALL_FOOD_ITEMS.length, foodQuota, cadenceTicks, String.format("%.1f", dispersionRadius));
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        int remainingFood = context.getInt(FoodRainEvent.REMAINING_FOOD_KEY, 0);
        if (remainingFood <= 0) {
            return;
        }

        int cadence = context.getInt(FoodRainEvent.INTERVAL_KEY, FoodRainEvent.MAX_CADENCE_TICKS);
        if (remainingTicks % cadence != 0) {
            return;
        }

        double radius = context.getDouble(FoodRainEvent.RADIUS_KEY, FoodRainEvent.MIN_DISPERSION_RADIUS);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (remainingFood <= 0) {
                break;
            }

            if (SpatialTargeting.isHazardImmune(player)) {
                continue;
            }

            ServerWorld world = player.getServerWorld();
            BlockPos playerPos = player.getBlockPos();

            if (!WeatherUtil.isExposedToSky(world, playerPos)) {
                continue;
            }

            BlockPos spawnPos = SpatialTargeting.findRandomSkyPosition(
                    world, player, radius, FoodRainEvent.SPAWN_HEIGHTMAP, FoodRainEvent.MIN_HEIGHT_ABOVE_PLAYER, FoodRainEvent.EXTRA_HEIGHT_VARIATION);

            if (spawnPos == null) {
                continue;
            }

            Random random = player.getRandom();
            Item chosenItem = FoodRainEvent.ALL_FOOD_ITEMS[random.nextInt(FoodRainEvent.ALL_FOOD_ITEMS.length)];
            ItemEntity drop = new ItemEntity(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    new ItemStack(chosenItem));
            drop.setVelocity(0.0, -FoodRainEvent.DOWNWARD_SPEED, 0.0);
            if (!world.spawnEntity(drop)) {
                continue;
            }

            context.trackEntity(drop.getUuid());
            remainingFood--;

            world.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.WEATHER, 0.4F, 0.6F);
        }

        context.set(FoodRainEvent.REMAINING_FOOD_KEY, remainingFood);
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        MerryEvents.LOGGER.info("[FoodRainEvent] Provision precipitation normalized.");
    }
}