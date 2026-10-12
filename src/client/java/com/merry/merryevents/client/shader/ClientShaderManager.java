package com.merry.merryevents.client.shader;

import com.merry.merryevents.client.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe stack coordinator for client post-processing shaders.
 * Guarantees conflict-free layered rendering and deterministic pipeline
 * disposal.
 */
public final class ClientShaderManager {
    private static final Deque<Identifier> SHADER_STACK = new ArrayDeque<>();

    private ClientShaderManager() {
    }

    /**
     * Pushes a target shader onto the stack and compiles it into the active
     * framebuffer.
     *
     * @param client   Active Minecraft client instance
     * @param shaderId Identifier resolving to target post shader JSON
     */
    public static synchronized void pushShader(MinecraftClient client, Identifier shaderId) {
        SHADER_STACK.remove(shaderId);
        SHADER_STACK.push(shaderId);
        applyTopShader(client);
    }

    /**
     * Pops target shader from the stack and re-evaluates the active post-effect.
     *
     * @param client   Active Minecraft client instance
     * @param shaderId Identifier of the shader to remove
     */
    public static synchronized void popShader(MinecraftClient client, Identifier shaderId) {
        SHADER_STACK.remove(shaderId);
        applyTopShader(client);
    }

    /**
     * Re-applies the top post-processing shader to the active framebuffer upon
     * dimension change.
     *
     * @param client Active Minecraft client instance
     */
    public static synchronized void reapply(MinecraftClient client) {
        applyTopShader(client);
    }

    /**
     * Empties the shader stack and closes active post-processing pipelines.
     *
     * @param client Active Minecraft client instance
     */
    public static synchronized void reset(MinecraftClient client) {
        SHADER_STACK.clear();
        client.gameRenderer.disablePostProcessor();
    }

    private static void applyTopShader(MinecraftClient client) {
        if (SHADER_STACK.isEmpty()) {
            client.gameRenderer.disablePostProcessor();
        } else {
            ((GameRendererAccessor) client.gameRenderer).merryevents$loadPostProcessor(SHADER_STACK.peek());
        }
    }
}