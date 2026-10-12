package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.ScrambledButtonsClientEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting screen initialization to scramble button labels cleanly
 * once per initialization cycle.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    /**
     * Intercepts screen initialization completion to randomize clickable button
     * text labels.
     *
     * @param client Active Minecraft client instance
     * @param width  Screen width
     * @param height Screen height
     * @param ci     Callback info
     */
    @Inject(method = "init(Lnet/minecraft/client/MinecraftClient;II)V", at = @At("TAIL"))
    private void merryevents$scrambleButtonsOnInit(MinecraftClient client, int width, int height, CallbackInfo ci) {
        if (ScrambledButtonsClientEffect.isActive()) {
            ScrambledButtonsClientEffect.scrambleScreen((Screen) (Object) this);
        }
    }
}