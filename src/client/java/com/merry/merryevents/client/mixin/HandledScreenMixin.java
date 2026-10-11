package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.ClosedInventoryClientEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting all container screen initializations to block display
 * during ClosedInventory events.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    /**
     * Aborts screen initialization and returns the client to world view when
     * inventory and container access is forbidden.
     *
     * @param ci Callback info
     */
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void merryevents$blockHandledScreenInit(CallbackInfo ci) {
        if (ClosedInventoryClientEffect.isActive() && ci.isCancellable()) {
            ci.cancel();
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(null);
            ClosedInventoryClientEffect.notifyLockout(client);
        }
    }
}