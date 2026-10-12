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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific architectural hazard event belonging to EventCategory.WORLD.
 * Collapses terrain blocks around the target player with an expanded radius,
 * dropping
 * all broken blocks as whole Silk Touch items, preserving block entity
 * container data.
 */
public final class FloorHoleEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("floor_hole");
    private static final String REMAINING_BLOCKS_KEY = "remaining_blocks";
    private static final String RADIUS_KEY = "horizontal_radius";
    private static final int MIN_BLOCKS = 64;
    private static final int MAX_BLOCKS = 512;
    private static final int MIN_RADIUS = 4;
    private static final int MAX_RADIUS = 10;

    @Override
    public Identifier getId() {
        return FloorHoleEvent.ID;
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

        Random random = targetPlayer.getRandom();
        int totalBlocks = FloorHoleEvent.MIN_BLOCKS
                + random.nextInt(FloorHoleEvent.MAX_BLOCKS - FloorHoleEvent.MIN_BLOCKS + 1);
        int radius = FloorHoleEvent.MIN_RADIUS
                + random.nextInt(FloorHoleEvent.MAX_RADIUS - FloorHoleEvent.MIN_RADIUS + 1);

        context.set(FloorHoleEvent.REMAINING_BLOCKS_KEY, totalBlocks);
        context.set(FloorHoleEvent.RADIUS_KEY, radius);

        MerryEvents.LOGGER.info(
                "[FloorHoleEvent] Initialized structural sinkhole decay for {} (Quota: {} blocks, Radius: {} blocks)",
                targetPlayer.getName().getString(), totalBlocks, radius);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks <= 0) {
            return;
        }

        int blocksRemaining = context.getInt(FloorHoleEvent.REMAINING_BLOCKS_KEY, 0);
        if (blocksRemaining <= 0) {
            return;
        }

        Random random = targetPlayer.getRandom();
        double expectedRate = (double) blocksRemaining / (double) remainingTicks;
        int toBreakCount = (int) expectedRate;
        double fractionalRate = expectedRate - (double) toBreakCount;

        if (random.nextDouble() < fractionalRate) {
            toBreakCount++;
        }

        if (toBreakCount <= 0) {
            return;
        }

        int radius = context.getInt(FloorHoleEvent.RADIUS_KEY, 6);
        ServerWorld world = targetPlayer.getServerWorld();
        BlockPos playerPos = targetPlayer.getBlockPos();
        BlockPos.Mutable searchPos = new BlockPos.Mutable();
        int successfullyBroken = 0;

        for (int i = 0; i < toBreakCount && blocksRemaining > 0; i++) {
            boolean found = false;

            for (int attempt = 0; attempt < 8; attempt++) {
                int dx = random.nextInt(radius * 2 + 1) - radius;
                int dz = random.nextInt(radius * 2 + 1) - radius;
                int dy = -random.nextInt(5);

                searchPos.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);

                if (!world.isChunkLoaded(searchPos.getX() >> 4, searchPos.getZ() >> 4)) {
                    continue;
                }

                BlockState state = world.getBlockState(searchPos);
                float hardness = state.getHardness(world, searchPos);

                if (!state.isAir() && hardness >= 0.0F) {
                    FloorHoleEvent.dropBlockSilkTouch(world, searchPos, state);
                    successfullyBroken++;
                    blocksRemaining--;
                    found = true;
                    break;
                }
            }

            if (!found && blocksRemaining > 0) {
                BlockPos steppingPos = targetPlayer.getSteppingPos();
                BlockState state = world.getBlockState(steppingPos);
                if (!state.isAir() && state.getHardness(world, steppingPos) >= 0.0F) {
                    FloorHoleEvent.dropBlockSilkTouch(world, steppingPos, state);
                    successfullyBroken++;
                    blocksRemaining--;
                }
            }
        }

        if (successfullyBroken > 0) {
            context.set(FloorHoleEvent.REMAINING_BLOCKS_KEY, blocksRemaining);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[FloorHoleEvent] Sinkhole decay concluded for {}",
                    targetPlayer.getName().getString());
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
}