package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.LobotomyClientEffect;
import net.minecraft.client.sound.Source;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting OpenAL audio source properties to apply real-time bitrate degradation and clipping distortion.
 */
@Mixin(Source.class)
public abstract class SourceMixin {
    @Shadow
    @Final
    private int pointer;

    /**
     * Intercepts source pitch assignments to quantize sample rates and inject frequency flutter during lobotomy shocks.
     *
     * @param pitch Inbound nominal sound pitch
     * @param ci    Callback info
     */
    @Inject(method = "setPitch", at = @At("HEAD"), cancellable = true)
    private void merryevents$degradeAudioPitch(float pitch, CallbackInfo ci) {
        if (LobotomyClientEffect.isLowBitrateAudioActive() && ci.isCancellable()) {
            ci.cancel();
            float degradedPitch = LobotomyClientEffect.degradePitch(pitch);
            AL10.alSourcef(this.pointer, 4099, degradedPitch);
        }
    }

    /**
     * Intercepts source volume assignments to boost gain past nominal DAC thresholds, inducing hardware clipping overdrive.
     *
     * @param volume Inbound nominal sound volume
     * @param ci     Callback info
     */
    @Inject(method = "setVolume", at = @At("HEAD"), cancellable = true)
    private void merryevents$overdriveAudioVolume(float volume, CallbackInfo ci) {
        if (LobotomyClientEffect.isLowBitrateAudioActive() && ci.isCancellable()) {
            ci.cancel();
            float distortedVolume = LobotomyClientEffect.degradeVolume(volume);
            AL10.alSourcef(this.pointer, 4106, distortedVolume);
        }
    }
}