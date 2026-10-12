package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific deteriorating terrain event belonging to EventCategory.WORLD.
 * Actively simulates the player breaking the block directly underneath them
 * with hand swinging,
 * impact audio, progressive damage cracking, dynamic held-tool acceleration,
 * and Silk Touch drops.
 */
public final class CrumblingFloorEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("crumbling_floor");
    private static final String STATE_KEY = "crumble_state";
    private static final int BREAKER_ID_OFFSET = 100_000;

    @Override
    public Identifier getId() {
        return CrumblingFloorEvent.ID;
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

        // Stone pickaxe tool mining speed multiplier is 4.0F, diamond pickaxe is 8.0F
        float baseMiningSpeed = 4.0F + targetPlayer.getRandom().nextFloat() * 4.0F;
        int breakerEntityId = targetPlayer.getId() + CrumblingFloorEvent.BREAKER_ID_OFFSET;
        context.set(CrumblingFloorEvent.STATE_KEY, new BreakState(baseMiningSpeed, breakerEntityId));

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

        BreakState state = context.getOrDefault(CrumblingFloorEvent.STATE_KEY, null);
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

            float effectiveSpeed = state.speedMultiplier;
            float playerToolSpeed = targetPlayer.getBlockBreakingSpeed(blockState);
            if (targetPlayer.getMainHandStack().isSuitableFor(blockState) || playerToolSpeed > 1.0F) {
                effectiveSpeed += playerToolSpeed;
            }

            float delta = hardness == 0.0F ? 1.0F : (effectiveSpeed / hardness / 30.0F);
            state.progress += delta;

            if (state.progress >= 1.0F) {
                world.setBlockBreakingInfo(state.breakerEntityId, currentPos, -1);
                CrumblingFloorEvent.dropBlockSilkTouch(world, currentPos, blockState);
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
            BreakState state = context.getOrDefault(CrumblingFloorEvent.STATE_KEY, null);
            if (state != null && state.pos != null) {
                targetPlayer.getServerWorld().setBlockBreakingInfo(state.breakerEntityId, state.pos, -1);
            }
        }
    }

    /**
     * Harvests and drops the targeted block as a complete Silk Touch item stack,
     * serializing block entity data and preventing container scatter.
     *
     * @param world Server world instance
     * @param pos   Target block coordinate
     * @param state Block state being destroyed
     */
    private static void dropBlockSilkTouch(ServerWorld world, BlockPos pos, BlockState state) {
        Item item = state.getBlock().asItem();
        if (item != Items.AIR) {
            ItemStack stack = new ItemStack(item);
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.setStackNbt(stack, world.getRegistryManager());
            }
            Block.dropStack(world, pos, stack);
        }
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
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