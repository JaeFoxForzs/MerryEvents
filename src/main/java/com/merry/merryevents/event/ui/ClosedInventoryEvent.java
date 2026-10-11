package com.merry.merryevents.event.ui;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific interface restriction event belonging to EventCategory.UI.
 * Suppresses client survival inventory and all external container screens for
 * the event duration.
 */
public final class ClosedInventoryEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("closed_inventory");

    @Override
    public Identifier getId() {
        return ClosedInventoryEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.UI;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Queries whether inventory access is currently locked for the target player.
     *
     * @param player Target server player entity
     * @return True if inventory lockout is active
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), ClosedInventoryEvent.ID);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.closeHandledScreen();
            MerryEvents.LOGGER.info("[ClosedInventoryEvent] Survival inventory locked for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer != null && targetPlayer.currentScreenHandler != targetPlayer.playerScreenHandler) {
            targetPlayer.closeHandledScreen();
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[ClosedInventoryEvent] Survival inventory restored for {}",
                    targetPlayer.getName().getString());
        }
    }
}