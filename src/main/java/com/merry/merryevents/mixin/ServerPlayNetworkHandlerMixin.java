package com.merry.merryevents.mixin;

import com.merry.merryevents.event.inventory.StickyFingersEvent;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting player action packets to enforce hotbar slot locks and
 * toss suppression.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    /**
     * Neutralizes client requests to switch hotbar slots when Sticky Fingers is
     * active.
     *
     * @param packet Inbound slot update packet
     * @param ci     Callback info
     */
    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void merryevents$lockSelectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (StickyFingersEvent.isPlayerActive(this.player) && ci.isCancellable()) {
            ci.cancel();
            int lockedSlot = StickyFingersEvent.getLockedSlot(this.player);
            this.player.getInventory().selectedSlot = lockedSlot;
            this.player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(lockedSlot));
        }
    }

    /**
     * Intercepts item drop actions to prevent tossing carried items when Sticky
     * Fingers is active.
     *
     * @param packet Inbound player action packet
     * @param ci     Callback info
     */
    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void merryevents$preventItemDrop(PlayerActionC2SPacket packet, CallbackInfo ci) {
        PlayerActionC2SPacket.Action action = packet.getAction();
        if ((action == PlayerActionC2SPacket.Action.DROP_ITEM || action == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS)
                && StickyFingersEvent.isPlayerActive(this.player) && ci.isCancellable()) {
            ci.cancel();
            this.player.sendMessage(
                    Text.translatable("event.merryevents.sticky_fingers.blocked").formatted(Formatting.RED), true);
            this.player.playSoundToPlayer(SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.PLAYERS, 0.7F, 1.2F);

            int slot = this.player.getInventory().selectedSlot;
            this.player.networkHandler.sendPacket(
                    new ScreenHandlerSlotUpdateS2CPacket(-2, 0, slot, this.player.getInventory().getStack(slot)));
        }
    }
}