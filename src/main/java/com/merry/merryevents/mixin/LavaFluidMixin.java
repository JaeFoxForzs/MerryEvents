package com.merry.merryevents.mixin;

import com.merry.merryevents.event.world.LavaWaterSwapEvent;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin transposing lava spread speed, infinite source creation, and downward
 * solidification.
 */
@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin {
    /**
     * Accelerates lava flow tick rate to water speeds during active transposition.
     *
     * @param world Current world view
     * @param cir   Callback info returnable
     */
    @Inject(method = "getTickRate", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapLavaTickRate(WorldView world, CallbackInfoReturnable<Integer> cir) {
        if (world instanceof World w && LavaWaterSwapEvent.isActive(w)) {
            cir.setReturnValue(5);
        }
    }

    /**
     * Expands lava maximum spread distance to 7 blocks during active transposition.
     *
     * @param world Current world view
     * @param cir   Callback info returnable
     */
    @Inject(method = "getLevelDecreasePerBlock", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapLavaDecrease(WorldView world, CallbackInfoReturnable<Integer> cir) {
        if (world instanceof World w && LavaWaterSwapEvent.isActive(w)) {
            cir.setReturnValue(1);
        }
    }

    /**
     * Enables infinite lava source creation from adjacent sources when
     * transposition is active.
     *
     * @param world Current world instance
     * @param cir   Callback info returnable
     */
    @Inject(method = "isInfinite", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapLavaInfinite(World world, CallbackInfoReturnable<Boolean> cir) {
        if (LavaWaterSwapEvent.isActive(world)) {
            cir.setReturnValue(world.getGameRules().getBoolean(GameRules.WATER_SOURCE_CONVERSION));
        }
    }

    /**
     * Suppresses downward stone formation when lava falls onto water during active
     * transposition.
     *
     * @param instance   Target fluid state being tested
     * @param tag        Fluid tag checked by vanilla logic
     * @param world      World access instance
     * @param pos        Target block position
     * @param state      Target block state
     * @param direction  Spread direction vector
     * @param fluidState Active fluid state
     * @return False if transposition is active to bypass stone creation and allow
     *         fluid flow
     */
    @Redirect(method = "flow", at = @At(value = "INVOKE", target = "Lnet/minecraft/fluid/FluidState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean merryevents$redirectLavaDownWaterCheck(FluidState instance, TagKey<Fluid> tag, WorldAccess world,
            BlockPos pos, BlockState state, Direction direction, FluidState fluidState) {
        if (world instanceof World w && LavaWaterSwapEvent.isActive(w)) {
            return false;
        }
        return instance.isIn(tag);
    }
}