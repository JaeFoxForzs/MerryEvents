package com.merry.merryevents.client.mixin;

import com.merry.merryevents.client.effect.RubberWorldClientEffect;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Client mixin intercepting low-level sound engine dispatch to substitute world
 * audio during RubberWorld events.
 */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    /**
     * Intercepts sound playback arguments to redirect audio waveforms to squeaking
     * rubber ducks and springs.
     *
     * @param sound Prospective sound instance queued for playback
     * @return Transformed rubber sound instance if active, otherwise original sound
     */
    @ModifyVariable(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), argsOnly = true)
    private SoundInstance merryevents$transformRubberWorldSounds(SoundInstance sound) {
        if (RubberWorldClientEffect.isActive()) {
            return RubberWorldClientEffect.getTransformedSound(sound);
        }
        return sound;
    }
}