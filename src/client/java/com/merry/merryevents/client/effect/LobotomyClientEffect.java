package com.merry.merryevents.client.effect;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client visual effect driver applying periodic pixelated viewport freezing, film grain, and audio bitcrushing.
 * Renders high-contrast, downscaled static snapshots that smoothly decay in opacity.
 */
public final class LobotomyClientEffect implements ClientChaosEffect {
    private static final Identifier FREEZE_TEXTURE_ID = MerryEvents.id("textures/vfx/lobotomy_freeze.png");
    private static final Identifier NOISE_TEXTURE_ID = MerryEvents.id("textures/vfx/lobotomy_noise.png");

    // Grid sizing and noise dimensions
    private static final int PIXEL_BLOCK_SIZE = 6;
    private static final int NOISE_SIZE = 64;

    // Configurable randomization bounds (in ticks: 20 ticks = 1 second)
    private static final int MIN_HOLD_TICKS = 8;        // 0.4 seconds hold
    private static final int MAX_HOLD_TICKS = 17;       // 0.85 seconds hold
    private static final int MIN_FADE_TICKS = 35;       // 1.75 seconds fade
    private static final int MAX_FADE_TICKS = 65;       // 3.25 seconds fade

    // Contrast scaling bounds
    private static final float MIN_CONTRAST = 1.6F;
    private static final float MAX_CONTRAST = 2.0F;

    // Audio pitch variation bounds
    private static final float MIN_PITCH = 0.82F;
    private static final float MAX_PITCH = 1.22F;

    // Quota-based stochastic scheduling state
    private static final int MIN_COOLDOWN_TICKS = 300; // Minimum 15 seconds between triggers
    private static int remainingLifetimeTicks = 0;
    private static int remainingQuota = 0;
    private static int cooldownTicks = 0;

    // Active frame state machines
    private static volatile boolean active = false;
    private static boolean registered = false;
    private static boolean freezeActive = false;
    private static int holdTicks = 0;
    private static int fadeTicks = 0;
    private static int currentFreezeElapsed = 0;

    private static NativeImageBackedTexture freezeTexture = null;
    private static NativeImageBackedTexture noiseTexture = null;

    /**
     * Queries whether low-bitrate audio degradation should currently affect playing sound sources.
     *
     * @return True if sound bitcrushing is active
     */
    public static boolean isLowBitrateAudioActive() {
        return active && freezeActive;
    }

    /**
     * Quantizes sound pitch into discrete low-sample-rate steps and injects acoustic flutter.
     *
     * @param originalPitch Inbound nominal sound pitch
     * @return Degraded bitcrushed pitch value
     */
    public static float degradePitch(float originalPitch) {
        float step = 0.12F;
        float quantized = Math.round(originalPitch / step) * step;
        float flutter = (float) Math.sin(System.currentTimeMillis() * 0.04) * 0.035F;
        return MathHelper.clamp(quantized * 0.85F + flutter, 0.45F, 1.95F);
    }

    /**
     * Overdrives sound gain past nominal clipping thresholds to produce distorted lo-fi clipping.
     *
     * @param originalVolume Inbound nominal sound volume
     * @return Overdriven clipping gain multiplier
     */
    public static float degradeVolume(float originalVolume) {
        return MathHelper.clamp(originalVolume * 2.2F, 0.0F, 3.5F);
    }

    /**
     * Initializes global client tick updates and HUD overlay rendering listeners.
     */
    public static void init() {
        if (registered) return;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active) {
                if (freezeActive) {
                    cleanupTextures();
                    freezeActive = false;
                }
                return;
            }

            // Advance freeze decay timeline
            if (freezeActive) {
                currentFreezeElapsed++;

                // Update animated static noise every 2 ticks
                if (currentFreezeElapsed % 2 == 0) {
                    updateNoiseTexture();
                }

                if (currentFreezeElapsed >= holdTicks + fadeTicks) {
                    freezeActive = false;
                    cleanupTextures();
                }
            }

            if (cooldownTicks > 0) {
                cooldownTicks--;
            }

            // Evaluate stochastic quota dispatching across remaining lifetime
            if (remainingLifetimeTicks > 0) {
                remainingLifetimeTicks--;

                if (!freezeActive && remainingQuota > 0 && cooldownTicks <= 0) {
                    double expectedRate = (double) remainingQuota / (double) remainingLifetimeTicks;
                    boolean forceTrigger = remainingLifetimeTicks <= remainingQuota * (MIN_COOLDOWN_TICKS + 40);

                    if (forceTrigger || ThreadLocalRandom.current().nextDouble() < expectedRate) {
                        triggerLobotomy(client);
                        remainingQuota--;
                        cooldownTicks = MIN_COOLDOWN_TICKS;
                    }
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!active || !freezeActive || freezeTexture == null) return;
            renderFreezeOverlay(context);
        });

        registered = true;
    }

    /**
     * Captures the active framebuffer, initiates texture baking, and plays the auditory cue.
     *
     * @param client Active Minecraft client instance
     */
    private static void triggerLobotomy(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        float pitch = MIN_PITCH + rand.nextFloat() * (MAX_PITCH - MIN_PITCH);
        float volume = 0.90F + rand.nextFloat() * 0.20F;
        client.getSoundManager().play(PositionedSoundInstance.master(MerryEvents.LOBOTOMY_SOUND, pitch, volume));

        holdTicks = rand.nextInt(MIN_HOLD_TICKS, MAX_HOLD_TICKS + 1);
        fadeTicks = rand.nextInt(MIN_FADE_TICKS, MAX_FADE_TICKS + 1);
        currentFreezeElapsed = 0;

        float contrast = MIN_CONTRAST + rand.nextFloat() * (MAX_CONTRAST - MIN_CONTRAST);

        captureAndBakeTexture(client, contrast);
        updateNoiseTexture();
        freezeActive = true;
    }

    /**
     * Downsamples the active framebuffer attachment, bakes film grain, and uploads the texture.
     *
     * @param client   Active Minecraft client instance
     * @param contrast Multiplication factor applied to color channels
     */
    private static void captureAndBakeTexture(MinecraftClient client, float contrast) {
        Framebuffer fb = client.getFramebuffer();
        if (fb == null) return;

        try {
            NativeImage fullImg = ScreenshotRecorder.takeScreenshot(fb);
            int fullW = fullImg.getWidth();
            int fullH = fullImg.getHeight();

            cleanupFreezeTexture();

            int lowW = Math.max(1, fullW / PIXEL_BLOCK_SIZE);
            int lowH = Math.max(1, fullH / PIXEL_BLOCK_SIZE);
            NativeImage pixelated = new NativeImage(lowW, lowH, false);
            ThreadLocalRandom rand = ThreadLocalRandom.current();

            for (int y = 0; y < lowH; y++) {
                for (int x = 0; x < lowW; x++) {
                    int srcX = Math.min(fullW - 1, x * PIXEL_BLOCK_SIZE + PIXEL_BLOCK_SIZE / 2);
                    // Standard direct vertical coordinate mapping without secondary inversion
                    int srcY = Math.min(fullH - 1, y * PIXEL_BLOCK_SIZE + PIXEL_BLOCK_SIZE / 2);

                    int abgr = fullImg.getColor(srcX, srcY);
                    int a = (abgr >> 24) & 0xFF;
                    int b = (abgr >> 16) & 0xFF;
                    int g = (abgr >> 8) & 0xFF;
                    int r = abgr & 0xFF;

                    // Baked analog film grain noise injection
                    int grain = rand.nextInt(61) - 30;

                    // Channel contrast enhancement
                    r = MathHelper.clamp((int) ((r - 128) * contrast + 128) + grain, 0, 255);
                    g = MathHelper.clamp((int) ((g - 128) * contrast + 128) + grain, 0, 255);
                    b = MathHelper.clamp((int) ((b - 128) * contrast + 128) + grain, 0, 255);

                    pixelated.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }

            fullImg.close();

            freezeTexture = new NativeImageBackedTexture(pixelated);
            client.getTextureManager().registerTexture(FREEZE_TEXTURE_ID, freezeTexture);

        } catch (Exception e) {
            MerryEvents.LOGGER.error("[Lobotomy] Failed to capture freeze frame", e);
        }
    }

    /**
     * Generates and uploads procedural dynamic static noise to simulate CRT/VHS grain crawl.
     */
    private static void updateNoiseTexture() {
        MinecraftClient client = MinecraftClient.getInstance();
        NativeImage noiseImg = new NativeImage(NOISE_SIZE, NOISE_SIZE, false);
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                int luminance = rand.nextInt(256);
                int alpha = rand.nextInt(180);
                noiseImg.setColor(x, y, (alpha << 24) | (luminance << 16) | (luminance << 8) | luminance);
            }
        }

        if (noiseTexture != null) {
            noiseTexture.close();
        }
        noiseTexture = new NativeImageBackedTexture(noiseImg);
        client.getTextureManager().registerTexture(NOISE_TEXTURE_ID, noiseTexture);
    }

    /**
     * Renders the baked downscaled freeze texture and dynamic film grain with decaying alpha.
     *
     * @param context Active graphical draw context
     */
    private static void renderFreezeOverlay(DrawContext context) {
        float alpha;

        if (currentFreezeElapsed < holdTicks) {
            alpha = 1.0F;
        } else {
            float fadeProgress = (float) (currentFreezeElapsed - holdTicks) / (float) fadeTicks;
            alpha = 1.0F - MathHelper.clamp(fadeProgress, 0.0F, 1.0F);
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Render primary high-contrast pixelated snapshot with baked grain
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        context.drawTexture(
                FREEZE_TEXTURE_ID,
                0, 0,
                0.0F, 0.0F,
                width, height,
                width, height
        );
        context.draw();

        // 2. Render dynamic crawling CRT noise overlay
        if (noiseTexture != null && alpha > 0.1F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * 0.22F);
            context.drawTexture(
                    NOISE_TEXTURE_ID,
                    0, 0,
                    0.0F, 0.0F,
                    width, height,
                    NOISE_SIZE, NOISE_SIZE
            );
            context.draw();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * Releases active snapshot textures and dynamic noise buffers.
     */
    private static void cleanupTextures() {
        cleanupFreezeTexture();
        if (noiseTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(NOISE_TEXTURE_ID);
            noiseTexture = null;
        }
    }

    /**
     * Releases the primary frozen snapshot texture.
     */
    private static void cleanupFreezeTexture() {
        if (freezeTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(FREEZE_TEXTURE_ID);
            freezeTexture = null;
        }
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        active = true;
        remainingLifetimeTicks = initialRemainingTicks;

        int totalSeconds = Math.max(120, initialRemainingTicks / 20);
        int minTriggers = Math.max(3, totalSeconds / 40);
        int maxTriggers = Math.max(minTriggers + 1, totalSeconds / 20);

        remainingQuota = ThreadLocalRandom.current().nextInt(minTriggers, maxTriggers + 1);
        cooldownTicks = 5;

        freezeActive = false;
    }

    @Override
    public void onStop(MinecraftClient client) {
        active = false;
        freezeActive = false;
        cleanupTextures();
    }
}