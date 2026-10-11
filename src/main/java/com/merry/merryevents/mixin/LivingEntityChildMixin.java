package com.merry.merryevents.mixin;

import com.merry.merryevents.event.mobs.KindergartenEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin enforcing child status flags and high-pitched vocal acoustics on all
 * living entities during active KindergartenEvents.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityChildMixin extends Entity {

    protected LivingEntityChildMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    /**
     * Intercepts baby state queries to return true for all living entities during
     * active KindergartenEvents.
     *
     * @param cir Callback info returnable
     */
    @Inject(method = "isBaby", at = @At("HEAD"), cancellable = true)
    private void merryevents$forceBabyState(CallbackInfoReturnable<Boolean> cir) {
        if (KindergartenEvent.isActive(this)) {
            cir.setReturnValue(true);
        }
    }
}