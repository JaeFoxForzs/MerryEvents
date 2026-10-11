package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific architectural hazard event belonging to EventCategory.WORLD.
 * Periodically obliterates the structural support block directly beneath the
 * target player.
 */
public final class FloorHoleEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("floor_hole");
    private static final int INTERVAL_TICKS = 900; // 45 seconds

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WORLD;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks % INTERVAL_TICKS != 0) {
            return;
        }

        ServerWorld world = targetPlayer.getServerWorld();
        BlockPos steppingPos = targetPlayer.getSteppingPos();
        BlockState state = world.getBlockState(steppingPos);

        if (!state.isAir() && state.getHardness(world, steppingPos) >= 0.0F) {
            world.breakBlock(steppingPos, false);
            world.playSound(null, steppingPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.5F);
            MerryEvents.LOGGER.debug("[FloorHoleEvent] Opened sinkhole under {}", targetPlayer.getName().getString());
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
    }
}