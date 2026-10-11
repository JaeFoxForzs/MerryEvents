package com.merry.merryevents.mixin;

import com.merry.merryevents.event.physics.PerfectIceEvent;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin intercepting block friction queries to override surface slipperiness
 * during active PerfectIceEvents.
 */
@Mixin(Block.class)
public abstract class BlockSlipperinessMixin {

    /**
     * Intercepts block slipperiness queries to return blue ice friction values when
     * the perfect ice event is active.
     *
     * @param cir Callback info returnable
     */
    @Inject(method = "getSlipperiness", at = @At("HEAD"), cancellable = true)
    private void merryevents$overrideSlipperiness(CallbackInfoReturnable<Float> cir) {
        if (PerfectIceEvent.isActive(null)) {
            cir.setReturnValue(PerfectIceEvent.BLUE_ICE_SLIPPERINESS);
        }
    }
}