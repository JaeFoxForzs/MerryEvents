package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.SpatialEntityScanner;
import com.merry.merryevents.util.SpatialTargeting;
import com.merry.merryevents.util.WeatherUtil;
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
 * Gradually degrades equipped and inventory item durability for rain-exposed
 * entities within localized player volumes.
 */
public final class RustRainEvent implements ChaosEvent {
    private static final String PLAYER_INDEX_KEY = "rust_rain_player_idx";
    private static final double SEARCH_RADIUS = 64.0;
    private static final int MAX_TARGETS_PER_TICK = 128;

    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();
    private static final Consumer<Item> NO_OP_BREAK_CALLBACK = item -> {
    };

    @Override
    public Identifier getId() {
        return EventIds.RUST_RAIN;
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
        WeatherUtil.startRain(server, 6000);
        MerryEvents.LOGGER.info(
                "[RustRainEvent] Atmospheric corrosion initiated: corrosive downpour forced across surface dimensions.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

        SpatialEntityScanner.scanAndProcess(
                server,
                context,
                RustRainEvent.PLAYER_INDEX_KEY,
                LivingEntity.class,
                RustRainEvent.SEARCH_RADIUS,
                RustRainEvent.MAX_TARGETS_PER_TICK,
                world -> world.getDimension().hasSkyLight() && world.isRaining(),
                living -> living.isAlive() && !living.isInvulnerable(),
                (world, player, living) -> {
                    BlockPos pos = living.getBlockPos();
                    if (!WeatherUtil.isExposedToRain(world, pos, living)) {
                        return;
                    }

                    if (living instanceof ServerPlayerEntity serverPlayer) {
                        if (SpatialTargeting.isHazardImmune(serverPlayer)) {
                            return;
                        }
                        this.corrodePlayerInventory(world, serverPlayer);
                    } else {
                        this.corrodeMobEquipment(world, living);
                    }
                });
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
                    this.applyItemCorrosion(world, player, stack, random);
                    break;
                }
                currentIndex++;
            }
        }
    }

    private void corrodeMobEquipment(ServerWorld world, LivingEntity living) {
        int damageableCount = 0;

        for (int i = 0; i < RustRainEvent.EQUIPMENT_SLOTS.length; i++) {
            ItemStack stack = living.getEquippedStack(RustRainEvent.EQUIPMENT_SLOTS[i]);
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

        for (int i = 0; i < RustRainEvent.EQUIPMENT_SLOTS.length; i++) {
            EquipmentSlot slot = RustRainEvent.EQUIPMENT_SLOTS[i];
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

        stack.damage(damageToApply, world, player, RustRainEvent.NO_OP_BREAK_CALLBACK);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.35F, 1.8F);
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        WeatherUtil.stopRain(server);
        MerryEvents.LOGGER.info("[RustRainEvent] Atmospheric conditions normalized: weather cleared.");
    }
}