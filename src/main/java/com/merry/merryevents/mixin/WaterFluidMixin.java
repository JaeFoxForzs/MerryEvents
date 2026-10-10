package com.merry.merryevents.mixin;

import com.merry.merryevents.event.world.LavaWaterSwapEvent;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin transposing water spread speed, flow decrease, and infinite source
 * creation.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {
    /**
     * Swaps water flow tick rate to sluggish lava speeds during active
     * transposition.
     *
     * @param world Current world view
     * @param cir   Callback info returnable
     */
    @Inject(method = "getTickRate", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapWaterTickRate(WorldView world, CallbackInfoReturnable<Integer> cir) {
        if (world instanceof World w && LavaWaterSwapEvent.isActive(w)) {
            cir.setReturnValue(w.getDimension().ultrawarm() ? 10 : 30);
        }
    }

    /**
     * Compresses water maximum spread distance from 7 down to 4 blocks during
     * active transposition.
     *
     * @param world Current world view
     * @param cir   Callback info returnable
     */
    @Inject(method = "getLevelDecreasePerBlock", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapWaterDecrease(WorldView world, CallbackInfoReturnable<Integer> cir) {
        if (world instanceof World w && LavaWaterSwapEvent.isActive(w)) {
            cir.setReturnValue(w.getDimension().ultrawarm() ? 1 : 2);
        }
    }

    /**
     * Disables infinite water source generation when transposition is active.
     *
     * @param world Current world instance
     * @param cir   Callback info returnable
     */
    @Inject(method = "isInfinite", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapWaterInfinite(World world, CallbackInfoReturnable<Boolean> cir) {
        if (LavaWaterSwapEvent.isActive(world)) {
            cir.setReturnValue(world.getGameRules().getBoolean(GameRules.LAVA_SOURCE_CONVERSION));
        }
    }
}