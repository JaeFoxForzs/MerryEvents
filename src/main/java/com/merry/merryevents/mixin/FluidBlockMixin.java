package com.merry.merryevents.mixin;

import com.google.common.collect.ImmutableList;
import com.merry.merryevents.event.world.LavaWaterSwapEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin intercepting fluid neighbor interactions to transpose stone generation
 * rules.
 */
@Mixin(FluidBlock.class)
public abstract class FluidBlockMixin {
    @Shadow
    @Final
    public static ImmutableList<Direction> FLOW_DIRECTIONS;

    /**
     * Intercepts adjacent fluid reactions, solidifying water instead of lava when
     * transposition is active.
     *
     * @param world Active world instance
     * @param pos   Current fluid block position
     * @param state Current block state
     * @param cir   Callback info returnable
     */
    @Inject(method = "receiveNeighborFluids", at = @At("HEAD"), cancellable = true)
    private void merryevents$handleSwappedNeighborFluids(World world, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Boolean> cir) {
        if (!LavaWaterSwapEvent.isActive(world)) {
            return;
        }

        FluidState currentFluid = state.getFluidState();
        if (currentFluid.isIn(FluidTags.WATER)) {
            boolean hasSoulSoil = world.getBlockState(pos.down()).isOf(Blocks.SOUL_SOIL);

            for (int i = 0; i < FLOW_DIRECTIONS.size(); i++) {
                Direction direction = FLOW_DIRECTIONS.get(i);
                BlockPos neighborPos = pos.offset(direction.getOpposite());
                FluidState neighborFluid = world.getFluidState(neighborPos);

                if (neighborFluid.isIn(FluidTags.LAVA)) {
                    Block resultingBlock = currentFluid.isStill() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE;
                    world.setBlockState(pos, resultingBlock.getDefaultState());
                    world.syncWorldEvent(WorldEvents.LAVA_EXTINGUISHED, pos, 0);
                    cir.setReturnValue(false);
                    return;
                }

                if (hasSoulSoil && world.getBlockState(neighborPos).isOf(Blocks.BLUE_ICE)) {
                    world.setBlockState(pos, Blocks.BASALT.getDefaultState());
                    world.syncWorldEvent(WorldEvents.LAVA_EXTINGUISHED, pos, 0);
                    cir.setReturnValue(false);
                    return;
                }
            }
            cir.setReturnValue(true);
        } else if (currentFluid.isIn(FluidTags.LAVA)) {
            cir.setReturnValue(true);
        }
    }
}