package com.merry.merryevents.mixin;

import com.merry.merryevents.event.ui.ClosedInventoryEvent;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
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

import java.util.OptionalInt;

/**
 * Server-side player mixin intercepting container screen initialization to
 * enforce authoritative inventory lockouts.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    /**
     * Intercepts container screen requests to reject access during active
     * ClosedInventory events.
     *
     * @param factory Screen handler factory instance
     * @param cir     Callback returnable holding prospective sync id
     */
    @Inject(method = "openHandledScreen", at = @At("HEAD"), cancellable = true)
    private void merryevents$blockOpenHandledScreen(NamedScreenHandlerFactory factory,
            CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (ClosedInventoryEvent.isPlayerActive(player) && cir.isCancellable()) {
            cir.setReturnValue(OptionalInt.empty());
            player.playSoundToPlayer(SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.PLAYERS, 1.0F, 1.2F);
            player.sendMessage(
                    Text.translatable("event.merryevents.closed_inventory.blocked").formatted(Formatting.RED),
                    true);
        }
    }

    /**
     * Intercepts horse inventory screen requests to reject access during active
     * ClosedInventory events.
     *
     * @param horse     Target mount entity
     * @param inventory Target mount inventory
     * @param ci        Callback info
     */
    @Inject(method = "openHorseInventory", at = @At("HEAD"), cancellable = true)
    private void merryevents$blockOpenHorseInventory(AbstractHorseEntity horse, Inventory inventory, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (ClosedInventoryEvent.isPlayerActive(player) && ci.isCancellable()) {
            ci.cancel();
            player.playSoundToPlayer(SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.PLAYERS, 1.0F, 1.2F);
            player.sendMessage(
                    Text.translatable("event.merryevents.closed_inventory.blocked").formatted(Formatting.RED),
                    true);
        }
    }
}