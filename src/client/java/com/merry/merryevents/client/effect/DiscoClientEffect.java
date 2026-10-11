package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.ClientEventManager;
import com.merry.merryevents.client.api.ClientChaosEffect;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * Client visual effect driver interpolating full-screen RGB tints across the
 * active viewport.
 */
public final class DiscoClientEffect implements ClientChaosEffect {
    private static boolean registered = false;
    private static volatile boolean active = false;

    // Palette sequence: Vivid Red -> Emerald Green -> Neon Acid
    private static final int[][] COLORS = {
            { 255, 25, 45 }, // Vivid Red
            { 20, 220, 40 }, // Emerald Green
            { 180, 255, 10 } // Neon Acid
    };

    /**
     * Binds global HUD rendering listener to project synchronized viewport tints.
     */
    public static void init() {
        if (DiscoClientEffect.registered)
            return;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!DiscoClientEffect.active)
                return;
            DiscoClientEffect.renderDiscoTint(context);
        });
        DiscoClientEffect.registered = true;
    }

    private static void renderDiscoTint(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || ClientEventManager.isHudHidden())
            return;

        long time = System.currentTimeMillis();
        // Periodic color rotation cadence: 2000 milliseconds per color step
        double phase = (time % 6000L) / 2000.0;
        int index = (int) phase;
        float progress = (float) (phase - index);

        // Smooth cosine easing curve
        float t = 0.5F * (1.0F - MathHelper.cos(progress * (float) Math.PI));

        int[] c1 = DiscoClientEffect.COLORS[index % DiscoClientEffect.COLORS.length];
        int[] c2 = DiscoClientEffect.COLORS[(index + 1) % DiscoClientEffect.COLORS.length];

        int r = (int) MathHelper.lerp(t, c1[0], c2[0]);
        int g = (int) MathHelper.lerp(t, c1[1], c2[1]);
        int b = (int) MathHelper.lerp(t, c1[2], c2[2]);

        // Dynamic alpha pulse bounded within 65 to 105 out of 255
        float pulse = MathHelper.sin((time % 1000) / 1000.0F * (float) Math.PI * 2.0F);
        int alpha = (int) (85 + pulse * 20);

        int color = (alpha << 24) | (r << 16) | (g << 8) | b;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        context.fill(0, 0, width, height, color);
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        DiscoClientEffect.active = true;
    }

    @Override
    public void onStop(MinecraftClient client) {
        DiscoClientEffect.active = false;
    }
}