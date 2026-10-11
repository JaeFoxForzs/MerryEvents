package com.merry.merryevents.mixin;

import com.merry.merryevents.event.status.WaterDrinkerEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin intercepting potion consumption to replenish hunger and saturation when
 * drinking water bottles.
 */
@Mixin(PotionItem.class)
public abstract class PotionItemMixin {

    /**
     * Intercepts potion consumption to replenish hunger and saturation when
     * drinking water bottles during active WaterDrinkerEvents.
     *
     * @param stack Item stack being consumed
     * @param world World in which consumption occurred
     * @param user  Living entity finishing item usage
     * @param cir   Callback info returnable
     */
    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void merryevents$onDrinkWaterPotion(ItemStack stack, World world, LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            WaterDrinkerEvent.onDrinkPotion(player, stack);
        }
    }
}