package com.merry.merryevents.mixin;

import com.merry.merryevents.event.world.LavaWaterSwapEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting base flow mechanics to create stone when transposed water
 * flows down into lava.
 */
@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
    /**
     * Solidifies target lava into stone when transposed water flows downwards into
     * it.
     *
     * @param world      World access instance
     * @param pos        Target block position
     * @param state      Current block state
     * @param direction  Spread direction vector
     * @param fluidState Active fluid state being placed
     * @param ci         Callback info
     */
    @Inject(method = "flow", at = @At("HEAD"), cancellable = true)
    private void merryevents$waterFlowDownIntoLava(WorldAccess world, BlockPos pos, BlockState state,
            Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (direction == Direction.DOWN && world instanceof World w && LavaWaterSwapEvent.isActive(w)) {
            if (fluidState.isIn(FluidTags.WATER)) {
                FluidState targetFluid = world.getFluidState(pos);
                if (targetFluid.isIn(FluidTags.LAVA)) {
                    if (state.getBlock() instanceof FluidBlock) {
                        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    }
                    w.syncWorldEvent(WorldEvents.LAVA_EXTINGUISHED, pos, 0);
                    if (ci.isCancellable()) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}