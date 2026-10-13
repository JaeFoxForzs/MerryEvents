package com.merry.merryevents.event.inventory;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific hotbar constraint event belonging to EventCategory.INVENTORY.
 * Distributes a quota of forced slot rotations while continuously neutralizing
 * manual slot changes and drops.
 */
public final class StickyFingersEvent implements ChaosEvent {

    private static final String LOCKED_SLOT_KEY = "locked_slot";
    private static final String REMAINING_SWITCHES_KEY = "remaining_switches";
    private static final int MIN_SWITCH_QUOTA = 16;
    private static final int MAX_SWITCH_QUOTA = 64;

    @Override
    public Identifier getId() {
        return EventIds.STICKY_FINGERS;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.INVENTORY;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Evaluates whether Sticky Fingers restrictions are currently active for the
     * specified player.
     *
     * @param player Target server player entity
     * @return True if sticky fingers restrictions are active
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), EventIds.STICKY_FINGERS);
    }

    /**
     * Resolves the authoritative locked slot index for the target player.
     *
     * @param player Target server player entity
     * @return Authoritative locked hotbar slot index
     */
    public static int getLockedSlot(ServerPlayerEntity player) {
        EventContext context = EventManager.getInstance().getPlayerEventContext(player.getUuid(), EventIds.STICKY_FINGERS);
        if (context == null) {
            return player.getInventory().selectedSlot;
        }
        return context.getInt(StickyFingersEvent.LOCKED_SLOT_KEY, player.getInventory().selectedSlot);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        Random random = targetPlayer.getRandom();
        int quota = StickyFingersEvent.MIN_SWITCH_QUOTA + random.nextInt(StickyFingersEvent.MAX_SWITCH_QUOTA - StickyFingersEvent.MIN_SWITCH_QUOTA + 1);
        int initialSlot = targetPlayer.getInventory().selectedSlot;

        context.set(StickyFingersEvent.LOCKED_SLOT_KEY, initialSlot);
        context.set(StickyFingersEvent.REMAINING_SWITCHES_KEY, quota);

        targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_SLIME_BLOCK_PLACE, SoundCategory.PLAYERS, 1.0F, 1.2F);
        MerryEvents.LOGGER.info("[StickyFingersEvent] Active for {} (initial slot: {}, switch quota: {})",
                targetPlayer.getName().getString(), initialSlot, quota);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive() || remainingTicks <= 0) {
            return;
        }

        int lockedSlot = context.getInt(StickyFingersEvent.LOCKED_SLOT_KEY, 0);
        if (targetPlayer.getInventory().selectedSlot != lockedSlot) {
            targetPlayer.getInventory().selectedSlot = lockedSlot;
            targetPlayer.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(lockedSlot));
        }

        int remainingSwitches = context.getInt(StickyFingersEvent.REMAINING_SWITCHES_KEY, 0);
        if (remainingSwitches <= 0) {
            return;
        }

        Random random = targetPlayer.getRandom();
        double expectedRate = (double) remainingSwitches / (double) remainingTicks;
        int switchesThisTick = (int) expectedRate;
        if (random.nextDouble() < expectedRate - switchesThisTick) {
            switchesThisTick++;
        }

        if (switchesThisTick > 0) {
            int newSlot = (lockedSlot + 1 + random.nextInt(PlayerInventory.getHotbarSize() - 1))
                    % PlayerInventory.getHotbarSize();
            context.set(StickyFingersEvent.LOCKED_SLOT_KEY, newSlot);
            context.set(StickyFingersEvent.REMAINING_SWITCHES_KEY, remainingSwitches - 1);

            targetPlayer.getInventory().selectedSlot = newSlot;
            targetPlayer.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(newSlot));
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_SLIME_BLOCK_STEP, SoundCategory.PLAYERS, 0.9F, 1.5F);

            MerryEvents.LOGGER.debug("[StickyFingersEvent] Quota switch executed: locked slot {} on {}",
                    newSlot, targetPlayer.getName().getString());
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8F, 1.0F);
            MerryEvents.LOGGER.info("[StickyFingersEvent] Restrictions normalized for {}",
                    targetPlayer.getName().getString());
        }
    }
}