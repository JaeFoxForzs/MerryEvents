package com.merry.merryevents.client.api;

import net.minecraft.client.MinecraftClient;

/**
 * Client-only effect driver receiving authoritative server state
 * synchronization.
 * Controls local camera transformations, HUD visibility, post-shaders, and
 * visual rendering.
 */
public interface ClientChaosEffect {
    /**
     * Initializes client-side visual, audio, or input listeners upon event start.
     *
     * @param client                Active Minecraft client instance
     * @param initialRemainingTicks Total remaining lifetime in server ticks
     */
    void onStart(MinecraftClient client, int initialRemainingTicks);

    /**
     * Disposes of client-side overrides and restores default gameplay visual
     * states.
     *
     * @param client Active Minecraft client instance
     */
    void onStop(MinecraftClient client);

    /**
     * Executes client-side tick updates for animations, particles, and local
     * interpolation.
     *
     * @param client         Active Minecraft client instance
     * @param remainingTicks Ticks remaining before termination
     */
    default void onTick(MinecraftClient client, int remainingTicks) {
    }

    /**
     * Evaluates whether the vanilla in-game HUD should be occluded during this
     * effect.
     *
     * @return True to suppress HUD rendering
     */
    default boolean shouldHideHud() {
        return false;
    }

    /**
     * Evaluates whether horizontal camera rotation should be locked.
     *
     * @return True to neutralize mouse yaw deltas
     */
    default boolean isYawLocked() {
        return false;
    }

    /**
     * Evaluates whether vertical camera rotation should be locked.
     *
     * @return True to neutralize mouse pitch deltas
     */
    default boolean isPitchLocked() {
        return false;
    }
}