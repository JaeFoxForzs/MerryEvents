package com.merry.merryevents.mixin;

import com.merry.merryevents.event.world.LavaWaterSwapEvent;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.fluid.Fluid;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Mixin intercepting entity fluid detection to transpose water and lava
 * mechanics during active events.
 */
@Mixin(Entity.class)
public abstract class EntityFluidSwapMixin {
    @Shadow
    public abstract World getWorld();

    @Shadow
    protected boolean touchingWater;

    @Shadow
    protected boolean firstUpdate;

    @Shadow
    @Final
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Shadow
    @Final
    private Set<TagKey<Fluid>> submergedFluidTag;

    /**
     * Transposes lava immersion checks to evaluate water contact when transposition
     * is active.
     *
     * @param cir Callback info returnable for isInLava
     */
    @Inject(method = "isInLava", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapIsInLava(CallbackInfoReturnable<Boolean> cir) {
        if (LavaWaterSwapEvent.isActive(this.getWorld())) {
            cir.setReturnValue(!this.firstUpdate && this.touchingWater);
        }
    }

    /**
     * Transposes water immersion checks to evaluate lava contact when transposition
     * is active.
     *
     * @param cir Callback info returnable for isTouchingWater
     */
    @Inject(method = "isTouchingWater", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapIsTouchingWater(CallbackInfoReturnable<Boolean> cir) {
        if (LavaWaterSwapEvent.isActive(this.getWorld())) {
            cir.setReturnValue(!this.firstUpdate && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0);
        }
    }

    /**
     * Inverts fluid tag queries for respiratory and eye-level submergence checks.
     *
     * @param fluidTag Target fluid tag being tested
     * @param cir      Callback info returnable for isSubmergedIn
     */
    @Inject(method = "isSubmergedIn", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapSubmergedTag(TagKey<Fluid> fluidTag, CallbackInfoReturnable<Boolean> cir) {
        if (LavaWaterSwapEvent.isActive(this.getWorld())) {
            if (fluidTag == FluidTags.WATER) {
                cir.setReturnValue(this.submergedFluidTag.contains(FluidTags.LAVA));
            } else if (fluidTag == FluidTags.LAVA) {
                cir.setReturnValue(this.submergedFluidTag.contains(FluidTags.WATER));
            }
        }
    }

    /**
     * Inverts fluid height values to synchronize kinematic swimming vectors and
     * jump boosts.
     *
     * @param fluid Target fluid tag being queried
     * @param cir   Callback info returnable for getFluidHeight
     */
    @Inject(method = "getFluidHeight", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapFluidHeight(TagKey<Fluid> fluid, CallbackInfoReturnable<Double> cir) {
        if (LavaWaterSwapEvent.isActive(this.getWorld())) {
            if (fluid == FluidTags.WATER) {
                cir.setReturnValue(this.fluidHeight.getDouble(FluidTags.LAVA));
            } else if (fluid == FluidTags.LAVA) {
                cir.setReturnValue(this.fluidHeight.getDouble(FluidTags.WATER));
            }
        }
    }
}