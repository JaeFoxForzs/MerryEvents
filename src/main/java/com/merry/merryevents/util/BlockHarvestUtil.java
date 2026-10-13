package com.merry.merryevents.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;

/**
 * Common block harvesting utility preserving container safety and simulating
 * Silk Touch drops.
 */
public final class BlockHarvestUtil {
    private BlockHarvestUtil() {
    }

    /**
     * Harvests and drops the targeted block as a complete Silk Touch item stack,
     * serializing block entity data and preventing container scatter.
     *
     * @param world Server world instance
     * @param pos   Target block coordinate
     * @param state Block state being destroyed
     */
    public static void dropBlockSilkTouch(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity()) {
            return;
        }
        Item item = state.getBlock().asItem();
        if (item != Items.AIR) {
            ItemStack stack = new ItemStack(item);
            Block.dropStack(world, pos, stack);
        }
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
    }
}