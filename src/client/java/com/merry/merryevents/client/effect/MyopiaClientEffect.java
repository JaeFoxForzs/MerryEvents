package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.shader.ClientShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Client post-processing driver engaging vanilla blur shaders during active
 * Myopia events.
 */
public final class MyopiaClientEffect implements ClientChaosEffect {
    // Vanilla native post-processing blur shader asset
    private static final Identifier BLUR_SHADER = Identifier.ofVanilla("shaders/post/blur.json");

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        ClientShaderManager.pushShader(client, MyopiaClientEffect.BLUR_SHADER);
    }

    @Override
    public void onStop(MinecraftClient client) {
        ClientShaderManager.popShader(client, MyopiaClientEffect.BLUR_SHADER);
    }
}