package com.merry.merryevents.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merry.merryevents.event.status.DelayedDamageEvent;
import com.merry.merryevents.event.status.WaterDrinkerEvent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin intercepting player damage and eating routines to enforce delayed
 * damage buffering and hydration metabolism.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * Intercepts food nutrition application to suppress hunger replenishment during
     * active WaterDrinkerEvents across logical sides.
     *
     * @param hungerManager Player hunger manager instance
     * @param foodComponent Food data component being consumed
     * @param original      Original HungerManager eat invocation wrapper
     */
    @WrapOperation(method = "eatFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;eat(Lnet/minecraft/component/type/FoodComponent;)V"))
    private void merryevents$suppressFoodNourishment(HungerManager hungerManager, FoodComponent foodComponent,
            Operation<Void> original) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            if (WaterDrinkerEvent.isPlayerActive(player)) {
                WaterDrinkerEvent.onConsumeFood(player);
                return;
            }
        } else if (WaterDrinkerEvent.isClientActive()) {
            return;
        }
        original.call(hungerManager, foodComponent);
    }

    /**
     * Bypasses secondary armor mitigation when applying flushed delayed damage
     * batches.
     *
     * @param player   Target player entity
     * @param source   Damage source
     * @param amount   Damage intensity
     * @param original Original applyArmorToDamage invocation wrapper
     * @return Raw damage amount if currently flushing delayed damage, otherwise
     *         original mitigated value
     */
    @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;applyArmorToDamage(Lnet/minecraft/entity/damage/DamageSource;F)F"))
    private float merryevents$skipArmorWhenFlushing(PlayerEntity player, DamageSource source, float amount,
            Operation<Float> original) {
        if (DelayedDamageEvent.isFlushing()) {
            return amount;
        }
        return original.call(player, source, amount);
    }

    /**
     * Bypasses secondary enchantment and resistance modifiers when applying flushed
     * delayed damage batches.
     *
     * @param player   Target player entity
     * @param source   Damage source
     * @param amount   Damage intensity
     * @param original Original modifyAppliedDamage invocation wrapper
     * @return Raw damage amount if currently flushing delayed damage, otherwise
     *         original modified value
     */
    @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F"))
    private float merryevents$skipModifiersWhenFlushing(PlayerEntity player, DamageSource source, float amount,
            Operation<Float> original) {
        if (DelayedDamageEvent.isFlushing()) {
            return amount;
        }
        return original.call(player, source, amount);
    }

    /**
     * Intercepts health reduction in applyDamage to buffer post-mitigation damage
     * without immediate heart loss.
     *
     * @param player    Target player entity
     * @param newHealth Prospective health value after damage
     * @param original  Original setHealth invocation wrapper
     */
    @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setHealth(F)V"))
    private void merryevents$bufferEffectiveDamage(PlayerEntity player, float newHealth, Operation<Void> original) {
        if ((Object) this instanceof ServerPlayerEntity serverPlayer
                && DelayedDamageEvent.isPlayerActive(serverPlayer)) {
            if (DelayedDamageEvent.isFlushing()) {
                original.call(player, newHealth);
                return;
            }

            float damageAmount = player.getHealth() - newHealth;
            if (damageAmount > 0.0F) {
                DelayedDamageEvent.bufferDamage(serverPlayer, damageAmount);
                original.call(player, player.getHealth());
                return;
            }
        }
        original.call(player, newHealth);
    }
}