package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client effect handler pitching all environmental audio into a comedic cartoon
 * register
 * and substituting footsteps and jumps with authentic rubber squeaks and
 * springs.
 */
public final class RubberWorldClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;

    public static final Identifier RUBBER_SQUEAK_ID = Identifier.ofVanilla("entity.slime.squish_small");
    public static final Identifier RUBBER_SPRING_ID = Identifier.ofVanilla("entity.slime.jump");

    /**
     * Evaluates whether rubber acoustics are actively enabled on the local client.
     *
     * @return True if rubber sound effect is active
     */
    public static boolean isActive() {
        return RubberWorldClientEffect.active;
    }

    /**
     * Intercepts an inbound audio instance to apply cartoon pitch-shifting or
     * substitute rubber assets.
     * Defers actual pitch computation until SoundSystem initializes the underlying
     * sound asset.
     *
     * @param original Inbound sound instance submitted to SoundSystem
     * @return Transformed rubber sound instance or original sound if exempt
     */
    public static SoundInstance getTransformedSound(SoundInstance original) {
        if (original == null) {
            return null;
        }

        if (original instanceof RubberSoundInstance) {
            return original;
        }

        SoundCategory category = original.getCategory();
        if (category == SoundCategory.MUSIC || category == SoundCategory.RECORDS) {
            return original;
        }

        Identifier id = original.getId();
        if (RubberWorldClientEffect.RUBBER_SQUEAK_ID.equals(id) || RubberWorldClientEffect.RUBBER_SPRING_ID.equals(id)) {
            return original;
        }

        String path = id.getPath();

        // 1. Movement and jump/fall sounds: substitute with rubber squeaks and springs
        if (path.contains("step")) {
            float pitch = 1.6F + ThreadLocalRandom.current().nextFloat() * 0.3F;
            return new RubberSoundInstance(original, RubberWorldClientEffect.RUBBER_SQUEAK_ID, pitch, -1.0F);
        }

        if (path.contains("jump") || path.contains("fall") || path.contains("bounce")) {
            float pitch = 1.5F + ThreadLocalRandom.current().nextFloat() * 0.3F;
            return new RubberSoundInstance(original, RubberWorldClientEffect.RUBBER_SPRING_ID, pitch, -1.0F);
        }

        // 2. All other world sounds: preserve the sound itself, but multiply pitch by
        // 1.65x
        return new RubberSoundInstance(original, id, -1.0F, 1.65F);
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        RubberWorldClientEffect.active = true;
    }

    @Override
    public void onStop(MinecraftClient client) {
        RubberWorldClientEffect.active = false;
    }

    /**
     * Transparent delegating sound instance applying high cartoon pitch or rubber
     * substitutions
     * while retaining full 3D spatial positioning and volume attenuation.
     */
    private static final class RubberSoundInstance implements SoundInstance {
        private final SoundInstance original;
        private final Identifier soundId;
        private final float absolutePitch;
        private final float pitchMultiplier;
        @Nullable
        private Sound sound;

        RubberSoundInstance(SoundInstance original, Identifier soundId, float absolutePitch, float pitchMultiplier) {
            this.original = original;
            this.soundId = soundId;
            this.absolutePitch = absolutePitch;
            this.pitchMultiplier = pitchMultiplier;
        }

        @Override
        public Identifier getId() {
            return this.soundId;
        }

        @Override
        public @Nullable WeightedSoundSet getSoundSet(SoundManager soundManager) {
            // Eagerly initialize the original sound to prevent NPEs during volume/pitch
            // lookups
            this.original.getSoundSet(soundManager);

            WeightedSoundSet soundSet = soundManager.get(this.soundId);
            if (soundSet == null) {
                this.sound = SoundManager.MISSING_SOUND;
            } else {
                this.sound = soundSet.getSound(Random.create());
            }
            return soundSet;
        }

        @Override
        public Sound getSound() {
            return this.sound != null ? this.sound : SoundManager.INTENTIONALLY_EMPTY_SOUND;
        }

        @Override
        public SoundCategory getCategory() {
            return this.original.getCategory();
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public boolean isRelative() {
            return this.original.isRelative();
        }

        @Override
        public int getRepeatDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            float origVol = 1.0F;
            try {
                origVol = this.original.getVolume();
            } catch (Throwable ignored) {
            }
            return Math.max(0.35F, origVol);
        }

        @Override
        public float getPitch() {
            if (this.absolutePitch > 0.0F) {
                // Return our primitive float directly; SoundSystem will handle the internal
                // FloatSupplier
                return this.absolutePitch;
            } else {
                float origPitch = 1.0F;
                try {
                    origPitch = this.original.getPitch();
                } catch (Throwable ignored) {
                }
                return MathHelper.clamp(origPitch * this.pitchMultiplier, 0.5F, 2.0F);
            }
        }

        @Override
        public double getX() {
            return this.original.getX();
        }

        @Override
        public double getY() {
            return this.original.getY();
        }

        @Override
        public double getZ() {
            return this.original.getZ();
        }

        @Override
        public AttenuationType getAttenuationType() {
            return this.original.getAttenuationType();
        }

        @Override
        public boolean canPlay() {
            return this.original.canPlay();
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return this.original.shouldAlwaysPlay();
        }
    }
}