package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Global atmospheric corrosion event belonging to EventCategory.WEATHER.
 * Corrodes item durability for all exposed living entities in active rainfall.
 * Inspects full inventory for players while checking only worn equipment on
 * mobs.
 */
public final class RustRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("rust_rain");
    private static final String PREVIOUS_WEATHER_KEY = "was_raining";

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();
    private static final Consumer<Item> NO_OP_BREAK_CALLBACK = item -> {
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
        ServerWorld overworld = server.getOverworld();
        context.set(PREVIOUS_WEATHER_KEY, overworld.isRaining());

        for (ServerWorld world : server.getWorlds()) {
            if (world.getDimension().hasSkyLight()) {
                world.setWeather(0, 6000, true, false);
            }
        }

        MerryEvents.LOGGER.info(
                "[RustRainEvent] Atmospheric corrosion initiated: corrosive downpour forced across surface dimensions.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            if (!world.getDimension().hasSkyLight() || !world.isRaining()) {
                continue;
            }

            for (Entity entity : world.iterateEntities()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvulnerable()) {
                    continue;
                }

                BlockPos pos = living.getBlockPos();
                if (!world.hasRain(pos)) {
                    continue;
                }

                if (living instanceof ServerPlayerEntity player) {
                    if (player.isCreative() || player.isSpectator()) {
                        continue;
                    }
                    corrodePlayerInventory(world, player);
                } else {
                    corrodeMobEquipment(world, living);
                }
            }
        }
    }

    private void corrodePlayerInventory(ServerWorld world, ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        int totalSlots = inventory.size();
        int damageableCount = 0;

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isDamageable()) {
                damageableCount++;
            }
        }

        if (damageableCount == 0) {
            return;
        }

        Random random = player.getRandom();
        int targetIndex = random.nextInt(damageableCount);
        int currentIndex = 0;

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isDamageable()) {
                if (currentIndex == targetIndex) {
                    applyItemCorrosion(world, player, stack, random);
                    break;
                }
                currentIndex++;
            }
        }
    }

    private void corrodeMobEquipment(ServerWorld world, LivingEntity living) {
        int damageableCount = 0;

        for (int i = 0; i < EQUIPMENT_SLOTS.length; i++) {
            ItemStack stack = living.getEquippedStack(EQUIPMENT_SLOTS[i]);
            if (!stack.isEmpty() && stack.isDamageable()) {
                damageableCount++;
            }
        }

        if (damageableCount == 0) {
            return;
        }

        Random random = living.getRandom();
        int targetIndex = random.nextInt(damageableCount);
        int currentIndex = 0;

        for (int i = 0; i < EQUIPMENT_SLOTS.length; i++) {
            EquipmentSlot slot = EQUIPMENT_SLOTS[i];
            ItemStack stack = living.getEquippedStack(slot);
            if (!stack.isEmpty() && stack.isDamageable()) {
                if (currentIndex == targetIndex) {
                    int maxDurability = stack.getMaxDamage();
                    double fraction = 0.001 + (random.nextDouble() * 0.019);
                    int damageToApply = Math.max(1, (int) Math.round(maxDurability * fraction));

                    stack.damage(damageToApply, living, slot);

                    world.playSound(null, living.getX(), living.getY(), living.getZ(),
                            SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.35F, 1.8F);
                    break;
                }
                currentIndex++;
            }
        }
    }

    private void applyItemCorrosion(ServerWorld world, ServerPlayerEntity player, ItemStack stack, Random random) {
        int maxDurability = stack.getMaxDamage();
        double fraction = 0.001 + (random.nextDouble() * 0.019);
        int damageToApply = Math.max(1, (int) Math.round(maxDurability * fraction));

        stack.damage(damageToApply, world, player, NO_OP_BREAK_CALLBACK);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.35F, 1.8F);
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        boolean wasRaining = context.getOrDefault(PREVIOUS_WEATHER_KEY, false);

        for (ServerWorld world : server.getWorlds()) {
            if (world.getDimension().hasSkyLight()) {
                if (wasRaining) {
                    world.setWeather(0, 6000, true, false);
                } else {
                    world.setWeather(6000, 0, false, false);
                }
            }
        }

        MerryEvents.LOGGER.info("[RustRainEvent] Atmospheric conditions normalized: weather state restored.");
    }
}