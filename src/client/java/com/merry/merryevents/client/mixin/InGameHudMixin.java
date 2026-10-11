package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.ClientEventManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting vanilla HUD rendering to enforce interface blackout
 * effects.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    /**
     * Cancels heads-up display rendering pipeline when a registered client effect
     * mandates HUD occlusion.
     *
     * @param context     Active GUI draw context
     * @param tickCounter Frame interpolation counter
     * @param ci          Callback info
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void merryevents$cancelHudRendering(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ClientEventManager.isHudHidden() && ci.isCancellable()) {
            ci.cancel();
        }
    }
}