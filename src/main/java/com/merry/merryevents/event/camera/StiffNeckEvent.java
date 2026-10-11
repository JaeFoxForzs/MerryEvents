package com.merry.merryevents.event.camera;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific sensory restriction event belonging to EventCategory.CAMERA.
 * Plays cracking audio cues while the client driver locks either yaw or pitch
 * mouse axes.
 */
public final class StiffNeckEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("stiff_neck");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.CAMERA;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.getServerWorld().playSound(
                    null,
                    targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.7F, 1.25F);
            targetPlayer.getServerWorld().playSound(
                    null,
                    targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 0.75F);

            MerryEvents.LOGGER.info("[StiffNeckEvent] Stiff neck effect started for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[StiffNeckEvent] Stiff neck effect ended for {}",
                    targetPlayer.getName().getString());
        }
    }
}