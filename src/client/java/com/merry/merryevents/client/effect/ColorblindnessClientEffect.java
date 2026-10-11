package com.merry.merryevents.client.effect;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.mixin.GameRendererAccessor;
import com.merry.merryevents.client.mixin.PostEffectProcessorAccessor;
import com.merry.merryevents.client.shader.ClientShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client visual effect driver applying color convolution shaders.
 * Dynamically binds uniform 3x3 transformation matrices to simulate specific
 * color deficiencies.
 */
public final class ColorblindnessClientEffect implements ClientChaosEffect {
    private static final Identifier COLORBLIND_SHADER = MerryEvents.id("shaders/post/colorblind.json");

    // Matrix parameter layouts: { rX, rY, rZ, gX, gY, gZ, bX, bY, bZ }
    private static final float[][] PROFILES = {
            // 0. Protanopia (red deficiency)
            { 0.56667F, 0.43333F, 0.00000F, 0.55833F, 0.44167F, 0.00000F, 0.00000F, 0.24167F, 0.75833F },
            // 1. Deuteranopia (green deficiency)
            { 0.62500F, 0.37500F, 0.00000F, 0.70000F, 0.30000F, 0.00000F, 0.00000F, 0.30000F, 0.70000F },
            // 2. Tritanopia (blue deficiency)
            { 0.95000F, 0.05000F, 0.00000F, 0.00000F, 0.43333F, 0.56667F, 0.00000F, 0.47500F, 0.52500F },
            // 3. Achromatopsia (monochrome)
            { 0.29900F, 0.58700F, 0.11400F, 0.29900F, 0.58700F, 0.11400F, 0.29900F, 0.58700F, 0.11400F },
            // 4. Protanomaly (attenuated red)
            { 0.81667F, 0.18333F, 0.00000F, 0.33333F, 0.66667F, 0.00000F, 0.00000F, 0.12500F, 0.87500F },
            // 5. Deuteranomaly (attenuated green)
            { 0.80000F, 0.20000F, 0.00000F, 0.25833F, 0.74167F, 0.00000F, 0.00000F, 0.14167F, 0.85833F }
    };

    private float[] currentMatrix = ColorblindnessClientEffect.PROFILES[0];

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        int index = ThreadLocalRandom.current().nextInt(ColorblindnessClientEffect.PROFILES.length);
        this.currentMatrix = ColorblindnessClientEffect.PROFILES[index];

        ClientShaderManager.pushShader(client, ColorblindnessClientEffect.COLORBLIND_SHADER);
        this.updateUniforms(client);
    }

    @Override
    public void onTick(MinecraftClient client, int remainingTicks) {
        this.updateUniforms(client);
    }

    @Override
    public void onStop(MinecraftClient client) {
        ClientShaderManager.popShader(client, ColorblindnessClientEffect.COLORBLIND_SHADER);
    }

    private void updateUniforms(MinecraftClient client) {
        if (client.gameRenderer == null)
            return;

        PostEffectProcessor processor = ((GameRendererAccessor) client.gameRenderer).merryevents$getPostProcessor();
        if (processor == null)
            return;

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) processor).merryevents$getPasses();
        for (PostEffectPass pass : passes) {
            GlUniform red = pass.getProgram().getUniformByName("RedMatrix");
            GlUniform green = pass.getProgram().getUniformByName("GreenMatrix");
            GlUniform blue = pass.getProgram().getUniformByName("BlueMatrix");
            GlUniform sat = pass.getProgram().getUniformByName("Saturation");
            GlUniform scale = pass.getProgram().getUniformByName("ColorScale");
            GlUniform offset = pass.getProgram().getUniformByName("Offset");

            if (red != null && green != null && blue != null) {
                red.set(this.currentMatrix[0], this.currentMatrix[1], this.currentMatrix[2]);
                green.set(this.currentMatrix[3], this.currentMatrix[4], this.currentMatrix[5]);
                blue.set(this.currentMatrix[6], this.currentMatrix[7], this.currentMatrix[8]);
            }
            if (sat != null) {
                sat.set(1.0F); // 1.0 represents nominal unadjusted color saturation
            }
            if (scale != null) {
                scale.set(1.0F, 1.0F, 1.0F);
            }
            if (offset != null) {
                offset.set(0.0F, 0.0F, 0.0F);
            }
        }
    }
}