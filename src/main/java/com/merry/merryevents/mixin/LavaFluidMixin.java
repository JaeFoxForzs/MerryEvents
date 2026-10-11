package com.merry.merryevents.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.merry.merryevents.event.world.LavaWaterSwapEvent;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin transposing lava spread speed, infinite source creation, and downward
 * solidification mechanics during active fluid swap events.
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
     * Expands lava maximum spread distance to match water limits during active
     * transposition.
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
     * Wraps fluid tag checks during downward flow to suppress stone formation when
     * lava falls into water during fluid swap events.
     *
     * @param instance Target fluid state tested
     * @param tag      Fluid tag checked by caller
     * @param original Original operation wrapper
     * @param world    Local world access parameter from enclosing flow method
     * @return False when swap is active to prevent stone solidification, otherwise
     *         vanilla check result
     */
    @WrapOperation(method = "flow", at = @At(value = "INVOKE", target = "Lnet/minecraft/fluid/FluidState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean merryevents$wrapLavaDownWaterCheck(FluidState instance, TagKey<Fluid> tag,
            Operation<Boolean> original, @Local(argsOnly = true) WorldAccess world) {
        if (world instanceof World w && LavaWaterSwapEvent.isActive(w)) {
            return false;
        }
        return original.call(instance, tag);
    }
}