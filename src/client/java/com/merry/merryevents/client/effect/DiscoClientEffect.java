package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.ClientEventManager;
import com.merry.merryevents.client.api.ClientChaosEffect;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

public final class DiscoClientEffect implements ClientChaosEffect {
    private static boolean registered = false;
    private static volatile boolean active = false;

    // Ключевые цвета: Красный -> Зеленый -> Кислотный/Неоновый
    private static final int[][] COLORS = {
            {255, 25, 45},   // Ярко-красный
            {20, 220, 40},   // Зелёный
            {180, 255, 10}   // Кислотный неоновый
    };

    public static void init() {
        if (registered) return;
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!active) return;
            renderDiscoTint(context);
        });
        registered = true;
    }

    private static void renderDiscoTint(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || ClientEventManager.isHudHidden()) return;

        long time = System.currentTimeMillis();
        // Цикл смены цветов: каждые 2 секунды (2000 мс на фазу)
        double phase = (time % 6000L) / 2000.0;
        int index = (int) phase;
        float progress = (float) (phase - index);

        // Плавный cosine easing
        float t = 0.5F * (1.0F - MathHelper.cos(progress * (float) Math.PI));

        int[] c1 = COLORS[index % COLORS.length];
        int[] c2 = COLORS[(index + 1) % COLORS.length];

        int r = (int) MathHelper.lerp(t, c1[0], c2[0]);
        int g = (int) MathHelper.lerp(t, c1[1], c2[1]);
        int b = (int) MathHelper.lerp(t, c1[2], c2[2]);

        // Пульсация прозрачности: от 65 до 105 (из 255) для сочного, но прозрачного эффекта
        float pulse = MathHelper.sin((time % 1000) / 1000.0F * (float) Math.PI * 2.0F);
        int alpha = (int) (85 + pulse * 20);

        int color = (alpha << 24) | (r << 16) | (g << 8) | b;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        context.fill(0, 0, width, height, color);
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        active = true;
    }

    @Override
    public void onStop(MinecraftClient client) {
        active = false;
    }
}