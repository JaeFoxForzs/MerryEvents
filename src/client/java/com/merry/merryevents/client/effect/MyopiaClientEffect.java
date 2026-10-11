package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.shader.ClientShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class MyopiaClientEffect implements ClientChaosEffect {
    // Ванильный встроенный шейдер размытия
    private static final Identifier BLUR_SHADER = Identifier.ofVanilla("shaders/post/blur.json");

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        ClientShaderManager.pushShader(client, BLUR_SHADER);
    }

    @Override
    public void onStop(MinecraftClient client) {
        ClientShaderManager.popShader(client, BLUR_SHADER);
    }
}