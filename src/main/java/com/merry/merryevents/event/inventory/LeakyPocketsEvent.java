package com.merry.merryevents.event.inventory;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific inventory disruption event belonging to
 * EventCategory.INVENTORY.
 * Distributes randomized drop quotas smoothly across all main inventory and
 * offhand slots
 * while strictly preserving equipped armor pieces.
 */
public final class LeakyPocketsEvent implements ChaosEvent {

    private static final String REMAINING_QUOTA_KEY = "remaining_quota";
    private static final int MIN_QUOTA = 16;
    private static final int MAX_QUOTA = 64;

    private static final int[] ELIGIBLE_SLOTS = new int[37];

    static {
        for (int i = 0; i < 36; i++) {
            LeakyPocketsEvent.ELIGIBLE_SLOTS[i] = i;
        }
        LeakyPocketsEvent.ELIGIBLE_SLOTS[36] = 40;
    }

    @Override
    public Identifier getId() {
        return EventIds.LEAKY_POCKETS;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.INVENTORY;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        Random random = targetPlayer.getRandom();
        int quota = LeakyPocketsEvent.MIN_QUOTA
                + random.nextInt(LeakyPocketsEvent.MAX_QUOTA - LeakyPocketsEvent.MIN_QUOTA + 1);
        context.set(LeakyPocketsEvent.REMAINING_QUOTA_KEY, quota);

        MerryEvents.LOGGER.info("[LeakyPocketsEvent] Active for {} with drop quota of {} items",
                targetPlayer.getName().getString(), quota);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive() || remainingTicks <= 0) {
            return;
        }

        int remainingQuota = context.getInt(LeakyPocketsEvent.REMAINING_QUOTA_KEY, 0);
        if (remainingQuota <= 0) {
            return;
        }

        Random random = targetPlayer.getRandom();
        double expectedRate = (double) remainingQuota / (double) remainingTicks;
        int dropsThisTick = (int) expectedRate;
        if (random.nextDouble() < expectedRate - dropsThisTick) {
            dropsThisTick++;
        }

        if (dropsThisTick <= 0) {
            return;
        }

        int successfullyDropped = 0;
        for (int i = 0; i < dropsThisTick && remainingQuota > 0; i++) {
            if (LeakyPocketsEvent.ejectRandomInventoryItem(targetPlayer, random)) {
                successfullyDropped++;
                remainingQuota--;
            }
        }

        if (successfullyDropped > 0) {
            context.set(LeakyPocketsEvent.REMAINING_QUOTA_KEY, remainingQuota);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[LeakyPocketsEvent] Normalized for {}", targetPlayer.getName().getString());
        }
    }

    private static boolean ejectRandomInventoryItem(ServerPlayerEntity player, Random random) {
        PlayerInventory inventory = player.getInventory();

        int occupiedCount = 0;
        for (int i = 0; i < LeakyPocketsEvent.ELIGIBLE_SLOTS.length; i++) {
            int slot = LeakyPocketsEvent.ELIGIBLE_SLOTS[i];
            if (!inventory.getStack(slot).isEmpty()) {
                occupiedCount++;
            }
        }

        if (occupiedCount == 0) {
            return false;
        }

        int targetIndex = random.nextInt(occupiedCount);
        int currentIndex = 0;
        int chosenSlot = -1;

        for (int i = 0; i < LeakyPocketsEvent.ELIGIBLE_SLOTS.length; i++) {
            int slot = LeakyPocketsEvent.ELIGIBLE_SLOTS[i];
            if (!inventory.getStack(slot).isEmpty()) {
                if (currentIndex == targetIndex) {
                    chosenSlot = slot;
                    break;
                }
                currentIndex++;
            }
        }

        if (chosenSlot == -1) {
            return false;
        }

        ItemStack stack = inventory.getStack(chosenSlot);
        int dropCount = Math.max(1, Math.min(stack.getCount(), 1 + random.nextInt(Math.min(4, stack.getCount()))));
        ItemStack droppedPortion = inventory.removeStack(chosenSlot, dropCount);

        if (!droppedPortion.isEmpty()) {
            ItemEntity entity = player.dropItem(droppedPortion, true, false);
            if (entity != null) {
                entity.setPickupDelay(30);
            }

            ServerWorld world = player.getServerWorld();
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7F, 0.6F);
            world.spawnParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + 0.2, player.getZ(), 5, 0.2, 0.1, 0.2, 0.05);

            MerryEvents.LOGGER.debug("[LeakyPocketsEvent] Ejected {} x {} from slot {} of {}",
                    dropCount, droppedPortion.getItem(), chosenSlot, player.getName().getString());
            return true;
        }

        return false;
    }
}