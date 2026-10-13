package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.BlockHarvestUtil;
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
 * with hand swinging, impact audio, progressive damage cracking, dynamic
 * held-tool acceleration,
 * and Silk Touch drops.
 */
public final class CrumblingFloorEvent implements ChaosEvent {
    private static final String SPEED_KEY = "crumb_speed";
    private static final String BREAKER_ID_KEY = "crumb_breaker_id";
    private static final String POS_X_KEY = "crumb_x";
    private static final String POS_Y_KEY = "crumb_y";
    private static final String POS_Z_KEY = "crumb_z";
    private static final String PROGRESS_KEY = "crumb_progress";
    private static final String TICK_COUNTER_KEY = "crumb_ticks";
    private static final String HAS_POS_KEY = "crumb_has_pos";

    private static final int BREAKER_ID_OFFSET = 100_000;

    @Override
    public Identifier getId() {
        return EventIds.CRUMBLING_FLOOR;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        float baseMiningSpeed = 4.0F + targetPlayer.getRandom().nextFloat() * 4.0F;
        int breakerEntityId = targetPlayer.getId() + CrumblingFloorEvent.BREAKER_ID_OFFSET;
        context.set(CrumblingFloorEvent.SPEED_KEY, baseMiningSpeed);
        context.set(CrumblingFloorEvent.BREAKER_ID_KEY, breakerEntityId);
        context.set(CrumblingFloorEvent.HAS_POS_KEY, false);

        MerryEvents.LOGGER.info(
                "[CrumblingFloorEvent] Flooring deterioration active for {} (BaseSpeed: {}x, BreakerId: {})",
                targetPlayer.getName().getString(), baseMiningSpeed, breakerEntityId);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null) {
            return;
        }

        int breakerEntityId = context.getInt(CrumblingFloorEvent.BREAKER_ID_KEY, -1);
        if (breakerEntityId == -1) {
            return;
        }

        BlockPos currentPos = targetPlayer.getSteppingPos();
        ServerWorld world = targetPlayer.getServerWorld();

        boolean hasSavedPos = context.getBoolean(CrumblingFloorEvent.HAS_POS_KEY, false);
        BlockPos savedPos = null;
        if (hasSavedPos) {
            savedPos = new BlockPos(
                    context.getInt(CrumblingFloorEvent.POS_X_KEY, 0),
                    context.getInt(CrumblingFloorEvent.POS_Y_KEY, 0),
                    context.getInt(CrumblingFloorEvent.POS_Z_KEY, 0));
        }

        if (!hasSavedPos || !currentPos.equals(savedPos)) {
            if (hasSavedPos && savedPos != null) {
                world.setBlockBreakingInfo(breakerEntityId, savedPos, -1);
            }
            context.set(CrumblingFloorEvent.HAS_POS_KEY, true);
            context.set(CrumblingFloorEvent.POS_X_KEY, currentPos.getX());
            context.set(CrumblingFloorEvent.POS_Y_KEY, currentPos.getY());
            context.set(CrumblingFloorEvent.POS_Z_KEY, currentPos.getZ());
            context.set(CrumblingFloorEvent.PROGRESS_KEY, 0.0F);
            context.set(CrumblingFloorEvent.TICK_COUNTER_KEY, 0);
        } else {
            BlockState blockState = world.getBlockState(currentPos);
            float hardness = blockState.getHardness(world, currentPos);

            if (blockState.isAir() || hardness < 0.0F || blockState.hasBlockEntity()) {
                return;
            }

            int tickCounter = context.getInt(CrumblingFloorEvent.TICK_COUNTER_KEY, 0) + 1;
            context.set(CrumblingFloorEvent.TICK_COUNTER_KEY, tickCounter);

            if (tickCounter % 4 == 0) {
                targetPlayer.swingHand(Hand.MAIN_HAND, true);
                BlockSoundGroup sounds = blockState.getSoundGroup();
                world.playSound(null, currentPos, sounds.getHitSound(), SoundCategory.BLOCKS,
                        (sounds.getVolume() + 1.0F) / 8.0F, sounds.getPitch() * 0.5F);
            }

            float effectiveSpeed = context.getFloat(CrumblingFloorEvent.SPEED_KEY, 4.0F);
            float playerToolSpeed = targetPlayer.getBlockBreakingSpeed(blockState);
            if (targetPlayer.getMainHandStack().isSuitableFor(blockState) || playerToolSpeed > 1.0F) {
                effectiveSpeed += playerToolSpeed;
            }

            float delta = hardness == 0.0F ? 1.0F : (effectiveSpeed / hardness / 30.0F);
            float progress = context.getFloat(CrumblingFloorEvent.PROGRESS_KEY, 0.0F) + delta;

            if (progress >= 1.0F) {
                world.setBlockBreakingInfo(breakerEntityId, currentPos, -1);
                BlockHarvestUtil.dropBlockSilkTouch(world, currentPos, blockState);
                context.set(CrumblingFloorEvent.PROGRESS_KEY, 0.0F);
                context.set(CrumblingFloorEvent.TICK_COUNTER_KEY, 0);
            } else {
                context.set(CrumblingFloorEvent.PROGRESS_KEY, progress);
                world.setBlockBreakingInfo(breakerEntityId, currentPos, (int) (progress * 10.0F));
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            boolean hasSavedPos = context.getBoolean(CrumblingFloorEvent.HAS_POS_KEY, false);
            if (hasSavedPos) {
                BlockPos savedPos = new BlockPos(
                        context.getInt(CrumblingFloorEvent.POS_X_KEY, 0),
                        context.getInt(CrumblingFloorEvent.POS_Y_KEY, 0),
                        context.getInt(CrumblingFloorEvent.POS_Z_KEY, 0));
                int breakerEntityId = context.getInt(CrumblingFloorEvent.BREAKER_ID_KEY, -1);
                if (breakerEntityId != -1) {
                    targetPlayer.getServerWorld().setBlockBreakingInfo(breakerEntityId, savedPos, -1);
                }
            }
        }
    }
}