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

public final class LobotomyClientEffect implements ClientChaosEffect {
    private static final Identifier FREEZE_TEXTURE_ID = MerryEvents.id("textures/vfx/lobotomy_freeze.png");
    private static final Identifier NOISE_TEXTURE_ID = MerryEvents.id("textures/vfx/lobotomy_noise.png");

    private static final int PIXEL_BLOCK_SIZE = 6;
    private static final int NOISE_SIZE = 64;

    private static final int MIN_HOLD_TICKS = 8;
    private static final int MAX_HOLD_TICKS = 17;
    private static final int MIN_FADE_TICKS = 35;
    private static final int MAX_FADE_TICKS = 65;

    private static final float MIN_CONTRAST = 1.6F;
    private static final float MAX_CONTRAST = 2.0F;

    private static final float MIN_PITCH = 0.82F;
    private static final float MAX_PITCH = 1.22F;

    private static final int MIN_COOLDOWN_TICKS = 300;
    private static int remainingLifetimeTicks = 0;
    private static int remainingQuota = 0;
    private static int cooldownTicks = 0;

    private static volatile boolean active = false;
    private static boolean registered = false;
    private static boolean freezeActive = false;
    private static int holdTicks = 0;
    private static int fadeTicks = 0;
    private static int currentFreezeElapsed = 0;

    private static NativeImageBackedTexture freezeTexture = null;
    private static NativeImageBackedTexture noiseTexture = null;

    public static boolean isLowBitrateAudioActive() {
        return active && freezeActive;
    }

    public static float degradePitch(float originalPitch) {
        float step = 0.12F;
        float quantized = Math.round(originalPitch / step) * step;
        float flutter = (float) Math.sin(System.currentTimeMillis() * 0.04) * 0.035F;
        return MathHelper.clamp(quantized * 0.85F + flutter, 0.45F, 1.95F);
    }

    public static float degradeVolume(float originalVolume) {
        return MathHelper.clamp(originalVolume * 2.2F, 0.0F, 3.5F);
    }

    public static void init() {
        if (registered) return;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active) {
                if (freezeActive) {
                    cleanupFreezeTexture();
                    freezeActive = false;
                }
                return;
            }

            if (freezeActive) {
                currentFreezeElapsed++;
                if (currentFreezeElapsed >= holdTicks + fadeTicks) {
                    freezeActive = false;
                    cleanupFreezeTexture();
                }
            }

            if (cooldownTicks > 0) {
                cooldownTicks--;
            }

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
        ensureNoiseTexture();
        freezeActive = true;
    }

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
                    int srcY = Math.min(fullH - 1, y * PIXEL_BLOCK_SIZE + PIXEL_BLOCK_SIZE / 2);

                    int abgr = fullImg.getColor(srcX, srcY);
                    int a = (abgr >> 24) & 0xFF;
                    int b = (abgr >> 16) & 0xFF;
                    int g = (abgr >> 8) & 0xFF;
                    int r = abgr & 0xFF;

                    int grain = rand.nextInt(61) - 30;

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

    // Текстура шума генерируется ровно один раз!
    private static void ensureNoiseTexture() {
        if (noiseTexture != null) return;

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

        noiseTexture = new NativeImageBackedTexture(noiseImg);
        client.getTextureManager().registerTexture(NOISE_TEXTURE_ID, noiseTexture);
    }

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

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        context.drawTexture(
                FREEZE_TEXTURE_ID,
                0, 0,
                0.0F, 0.0F,
                width, height,
                width, height
        );
        context.draw();

        // Живой шум получается за счёт рандомизации UV-смещения (0% нагрузки на CPU и 0 выделений памяти)
        if (noiseTexture != null && alpha > 0.1F) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha * 0.22F);
            float uShift = ThreadLocalRandom.current().nextInt(NOISE_SIZE);
            float vShift = ThreadLocalRandom.current().nextInt(NOISE_SIZE);
            context.drawTexture(
                    NOISE_TEXTURE_ID,
                    0, 0,
                    uShift, vShift,
                    width, height,
                    NOISE_SIZE, NOISE_SIZE
            );
            context.draw();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

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
        cleanupFreezeTexture();
    }
}