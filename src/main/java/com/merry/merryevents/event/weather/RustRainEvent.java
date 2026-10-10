package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
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
import net.minecraft.util.math.ChunkSectionPos;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Global atmospheric corrosion event belonging to EventCategory.WEATHER.
 * Randomly selects damageable items from exposed player inventories and erodes
 * 0.1% to 2.0% durability.
 */
public final class RustRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("rust_rain");

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
        MerryEvents.LOGGER.info("[RustRainEvent] Atmospheric corrosion active: rain corrodes inventory contents.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }

            ServerWorld world = player.getServerWorld();
            BlockPos pos = player.getBlockPos();

            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()),
                    ChunkSectionPos.getSectionCoord(pos.getZ()))) {
                continue;
            }

            if (!world.hasRain(pos)) {
                continue;
            }

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
                continue;
            }

            int targetIndex = player.getRandom().nextInt(damageableCount);
            int currentIndex = 0;

            for (int i = 0; i < totalSlots; i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty() && stack.isDamageable()) {
                    if (currentIndex == targetIndex) {
                        int maxDurability = stack.getMaxDamage();
                        double fraction = 0.001 + (player.getRandom().nextDouble() * 0.019);
                        int damageToApply = Math.max(1, (int) Math.round(maxDurability * fraction));

                        stack.damage(damageToApply, world, player, NO_OP_BREAK_CALLBACK);

                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 0.35F, 1.8F);
                        break;
                    }
                    currentIndex++;
                }
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[RustRainEvent] Atmospheric corrosion subsided.");
    }
}