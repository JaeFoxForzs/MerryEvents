package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client sensory disruption driver executing stochastic orientation snaps
 * paired with elastic camera roll and optical FOV jolts.
 * Distributes a discrete quota of impulses evenly across event lifetime with
 * zero memory allocation per frame.
 */
public final class EpilepsyClientEffect implements ClientChaosEffect {
    private static final int MIN_QUOTA = 128;
    private static final int MAX_QUOTA = 1024;

    private static volatile boolean active = false;
    private static boolean registered = false;
    private static int remainingLifetimeTicks = 0;
    private static int remainingQuota = 0;

    private static float currentRoll = 0.0F;
    private static float prevRoll = 0.0F;
    private static float fovOffset = 0.0F;
    private static float prevFovOffset = 0.0F;

    /**
     * Queries whether epilepsy sensory disruption is actively running on the
     * client.
     *
     * @return True if epilepsy effect is active
     */
    public static boolean isActive() {
        return EpilepsyClientEffect.active;
    }

    /**
     * Resolves the interpolated camera roll angle in degrees for the current
     * partial frame.
     *
     * @param tickDelta Frame interpolation delta
     * @return Interpolated Z-axis camera roll in degrees
     */
    public static float getCameraRoll(float tickDelta) {
        return MathHelper.lerp(tickDelta, EpilepsyClientEffect.prevRoll, EpilepsyClientEffect.currentRoll);
    }

    /**
     * Resolves the interpolated optical FOV displacement for the current partial
     * frame.
     *
     * @param tickDelta Frame interpolation delta
     * @return Interpolated optical field of view delta
     */
    public static double getFovOffset(float tickDelta) {
        return MathHelper.lerp(tickDelta, EpilepsyClientEffect.prevFovOffset, EpilepsyClientEffect.fovOffset);
    }

    /**
     * Initializes global client tick listeners updating quota evaluations and
     * elastic oscillation decays.
     */
    public static void init() {
        if (EpilepsyClientEffect.registered) {
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            EpilepsyClientEffect.prevRoll = EpilepsyClientEffect.currentRoll;
            EpilepsyClientEffect.currentRoll *= 0.58F;
            if (Math.abs(EpilepsyClientEffect.currentRoll) < 0.05F) {
                EpilepsyClientEffect.currentRoll = 0.0F;
            }

            EpilepsyClientEffect.prevFovOffset = EpilepsyClientEffect.fovOffset;
            EpilepsyClientEffect.fovOffset *= 0.58F;
            if (Math.abs(EpilepsyClientEffect.fovOffset) < 0.05F) {
                EpilepsyClientEffect.fovOffset = 0.0F;
            }

            if (!EpilepsyClientEffect.active || client.player == null || EpilepsyClientEffect.remainingLifetimeTicks <= 0 || EpilepsyClientEffect.remainingQuota <= 0) {
                return;
            }

            EpilepsyClientEffect.remainingLifetimeTicks--;

            double rate = (double) EpilepsyClientEffect.remainingQuota / (double) EpilepsyClientEffect.remainingLifetimeTicks;
            if (ThreadLocalRandom.current().nextDouble() < rate) {
                EpilepsyClientEffect.triggerCameraSnap(client);
                EpilepsyClientEffect.remainingQuota--;
            }
        });

        EpilepsyClientEffect.registered = true;
    }

    /**
     * Executes an orientation snap, injects roll skew, triggers an FOV pulse, and
     * emits a neck crack acoustic cue.
     *
     * @param client Active Minecraft client instance
     */
    private static void triggerCameraSnap(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        float yawDelta = (rand.nextFloat() - 0.5F) * 160.0F;
        float pitchDelta = (rand.nextFloat() - 0.5F) * 60.0F;

        client.player.setYaw(client.player.getYaw() + yawDelta);
        client.player.setPitch(MathHelper.clamp(client.player.getPitch() + pitchDelta, -90.0F, 90.0F));

        EpilepsyClientEffect.currentRoll = rand.nextBoolean() ? rand.nextFloat(16.0F, 26.0F) : -rand.nextFloat(16.0F, 26.0F);
        EpilepsyClientEffect.prevRoll = EpilepsyClientEffect.currentRoll;

        EpilepsyClientEffect.fovOffset = rand.nextBoolean() ? rand.nextFloat(20.0F, 30.0F) : -rand.nextFloat(14.0F, 22.0F);
        EpilepsyClientEffect.prevFovOffset = EpilepsyClientEffect.fovOffset;

        client.player.playSound(SoundEvents.ENTITY_SKELETON_STEP, 0.5F, 1.2F + rand.nextFloat() * 0.4F);
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        EpilepsyClientEffect.active = true;
        EpilepsyClientEffect.remainingLifetimeTicks = Math.max(1, initialRemainingTicks);
        EpilepsyClientEffect.remainingQuota = ThreadLocalRandom.current().nextInt(EpilepsyClientEffect.MIN_QUOTA, EpilepsyClientEffect.MAX_QUOTA + 1);
        EpilepsyClientEffect.currentRoll = 0.0F;
        EpilepsyClientEffect.prevRoll = 0.0F;
        EpilepsyClientEffect.fovOffset = 0.0F;
        EpilepsyClientEffect.prevFovOffset = 0.0F;
    }

    @Override
    public void onStop(MinecraftClient client) {
        EpilepsyClientEffect.active = false;
        EpilepsyClientEffect.remainingLifetimeTicks = 0;
        EpilepsyClientEffect.remainingQuota = 0;
        EpilepsyClientEffect.currentRoll = 0.0F;
        EpilepsyClientEffect.prevRoll = 0.0F;
        EpilepsyClientEffect.fovOffset = 0.0F;
        EpilepsyClientEffect.prevFovOffset = 0.0F;
    }
}