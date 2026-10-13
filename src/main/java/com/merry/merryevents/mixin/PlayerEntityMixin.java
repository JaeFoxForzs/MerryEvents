package com.merry.merryevents.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merry.merryevents.event.physics.KineticRecoilEvent;
import com.merry.merryevents.event.status.DelayedDamageEvent;
import com.merry.merryevents.event.status.EquilibriumEvent;
import com.merry.merryevents.event.status.FireSkinEvent;
import com.merry.merryevents.event.status.WaterDrinkerEvent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin intercepting player damage, attack, and eating routines to enforce
 * delayed
 * damage buffering, hydration metabolism, equilibrium pacifism, and thermal
 * interactions.
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
     * Blocks incoming damage during active Equilibrium events while strictly
     * protecting
     * against softlocks from void or kill commands.
     *
     * @param source Inbound damage source
     * @param amount Damage amount
     * @param cir    Callback returnable providing damage handling outcome
     */
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void merryevents$preventEquilibriumIncomingDamage(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && EquilibriumEvent.isPlayerActive(player)) {
            if (!source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL)) {
                cir.setReturnValue(false);
                player.getServerWorld().spawnParticles(ParticleTypes.ENCHANTED_HIT,
                        player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.25, 0.4, 0.25, 0.1);
                player.playSoundToPlayer(SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.8F, 1.3F);
            }
        }
    }

    /**
     * Neutralizes outgoing attacks against living entities during active
     * Equilibrium events.
     *
     * @param target Targeted entity
     * @param ci     Callback info
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void merryevents$preventEquilibriumOutgoingAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player && EquilibriumEvent.isPlayerActive(player)) {
            ci.cancel();
            player.playSoundToPlayer(SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.6F, 1.8F);
            player.sendMessage(
                    Text.translatable("event.merryevents.equilibrium.blocked_attack").formatted(Formatting.GRAY),
                    true);
        }
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

    /**
     * Intercepts entity damage in player attacks to trigger kinetic recoil impulses
     * and apply thermal fire skin ignition.
     *
     * @param target   Attacked entity
     * @param source   Damage source
     * @param amount   Calculated attack damage amount
     * @param original Original damage invocation wrapper
     * @return True if damage was successfully dealt to the target
     */
    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean merryevents$handleAttackInteractions(Entity target, DamageSource source, float amount,
            Operation<Boolean> original) {
        boolean damaged = original.call(target, source, amount);
        if (damaged && (Object) this instanceof ServerPlayerEntity player) {
            if (KineticRecoilEvent.isPlayerActive(player)) {
                KineticRecoilEvent.onAttackEntity(player, target, amount);
            }
            if (FireSkinEvent.isCombustionActive(player)) {
                float fireSeconds = player.getServerWorld().getDimension().ultrawarm() ? 12.0F : 8.0F;
                target.setOnFireFor(fireSeconds);
            }
        }
        return damaged;
    }
}