package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific deteriorating terrain event belonging to EventCategory.WORLD.
 * Actively simulates the player breaking the block directly underneath them
 * with
 * hand swinging, impact audio, and cracking overlay packets.
 */
public final class CrumblingFloorEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("crumbling_floor");
    private static final String STATE_KEY = "crumble_state";
    private static final int BREAKER_ID_OFFSET = 100_000;

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
        if (targetPlayer == null) {
            return;
        }

        float miningSpeed = (4.0F + targetPlayer.getRandom().nextFloat() * 4.0F) / 3.0F;
        int breakerEntityId = targetPlayer.getId() + BREAKER_ID_OFFSET;
        context.set(STATE_KEY, new BreakState(miningSpeed, breakerEntityId));

        MerryEvents.LOGGER.info("[CrumblingFloorEvent] Flooring deterioration active for {} (Speed: {}x, FakeId: {})",
                targetPlayer.getName().getString(), miningSpeed, breakerEntityId);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null) {
            return;
        }

        BreakState state = context.getOrDefault(STATE_KEY, null);
        if (state == null) {
            return;
        }

        BlockPos currentPos = targetPlayer.getSteppingPos();
        ServerWorld world = targetPlayer.getServerWorld();

        if (!currentPos.equals(state.pos)) {
            if (state.pos != null) {
                world.setBlockBreakingInfo(state.breakerEntityId, state.pos, -1);
            }
            state.pos = currentPos;
            state.progress = 0.0F;
            state.tickCounter = 0;
        } else {
            BlockState blockState = world.getBlockState(currentPos);
            float hardness = blockState.getHardness(world, currentPos);

            if (blockState.isAir() || hardness < 0.0F) {
                return;
            }

            state.tickCounter++;

            if (state.tickCounter % 4 == 0) {
                targetPlayer.swingHand(Hand.MAIN_HAND, true);
                BlockSoundGroup sounds = blockState.getSoundGroup();
                world.playSound(null, currentPos, sounds.getHitSound(), SoundCategory.BLOCKS,
                        (sounds.getVolume() + 1.0F) / 8.0F, sounds.getPitch() * 0.5F);
            }

            float delta = hardness == 0.0F ? 1.0F : (state.speedMultiplier / hardness / 30.0F);
            state.progress += delta;

            if (state.progress >= 1.0F) {
                world.setBlockBreakingInfo(state.breakerEntityId, currentPos, -1);
                world.breakBlock(currentPos, true, targetPlayer);
                state.progress = 0.0F;
                state.tickCounter = 0;
            } else {
                world.setBlockBreakingInfo(state.breakerEntityId, currentPos, (int) (state.progress * 10.0F));
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            BreakState state = context.getOrDefault(STATE_KEY, null);
            if (state != null && state.pos != null) {
                targetPlayer.getServerWorld().setBlockBreakingInfo(state.breakerEntityId, state.pos, -1);
            }
        }
    }

    /**
     * Mutable, zero-allocation tracking container handling active crumble
     * execution.
     */
    private static final class BreakState {
        final float speedMultiplier;
        final int breakerEntityId;
        BlockPos pos;
        float progress;
        int tickCounter;

        BreakState(float speedMultiplier, int breakerEntityId) {
            this.speedMultiplier = speedMultiplier;
            this.breakerEntityId = breakerEntityId;
            this.progress = 0.0F;
            this.tickCounter = 0;
        }
    }
}