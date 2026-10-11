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
 * Transposes entity fluid detection and immersion mechanics during active
 * LavaWaterSwapEvents.
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
     * Intercepts lava contact checks to return true only if the entity is currently
     * touching water.
     *
     * @param cir Callback returnable for isInLava
     */
    @Inject(method = "isInLava", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapIsInLava(CallbackInfoReturnable<Boolean> cir) {
        if (LavaWaterSwapEvent.isActive(this.getWorld())) {
            cir.setReturnValue(!this.firstUpdate && this.touchingWater);
        }
    }

    /**
     * Intercepts water contact checks to return true only if the entity is touching
     * lava.
     *
     * @param cir Callback returnable for isTouchingWater
     */
    @Inject(method = "isTouchingWater", at = @At("HEAD"), cancellable = true)
    private void merryevents$swapIsTouchingWater(CallbackInfoReturnable<Boolean> cir) {
        if (LavaWaterSwapEvent.isActive(this.getWorld())) {
            cir.setReturnValue(!this.firstUpdate && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0);
        }
    }

    /**
     * Transposes eye-level fluid tag queries between water and lava for respiration
     * and visual fog logic.
     *
     * @param fluidTag Fluid tag being tested
     * @param cir      Callback returnable for isSubmergedIn
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
     * Transposes fluid height levels between water and lava for buoyancy and
     * kinematic swimming velocities.
     *
     * @param fluid Target fluid tag queried
     * @param cir   Callback returnable for getFluidHeight
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