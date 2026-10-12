package com.merry.merryevents.client.weather;

import com.merry.merryevents.client.ClientEventManager;
import com.merry.merryevents.event.weather.AcidRainEvent;
import com.merry.merryevents.event.weather.RustRainEvent;

/**
 * Client coordinator resolving dynamic RGB color tints for precipitation
 * geometry.
 * Evaluates active chaos weather event states with zero memory allocations
 * during rendering.
 */
public final class RainColorManager {
    // Acid rain: vivid toxic neon green
    private static final float ACID_R = 0.20F;
    private static final float ACID_G = 0.98F;
    private static final float ACID_B = 0.22F;

    // Rust rain: heavy oxidized burnt orange
    private static final float RUST_R = 0.95F;
    private static final float RUST_G = 0.42F;
    private static final float RUST_B = 0.10F;

    private RainColorManager() {
    }

    /**
     * Evaluates whether any active weather event requires precipitation recoloring.
     *
     * @return True if precipitation should be tinted
     */
    public static boolean hasActiveRainTint() {
        return ClientEventManager.isEventActive(AcidRainEvent.ID)
                || ClientEventManager.isEventActive(RustRainEvent.ID);
    }

    /**
     * Resolves the red color multiplier for precipitation vertices.
     *
     * @return Normalized red component between 0.0 and 1.0
     */
    public static float getTintRed() {
        if (ClientEventManager.isEventActive(AcidRainEvent.ID)) {
            return RainColorManager.ACID_R;
        }
        if (ClientEventManager.isEventActive(RustRainEvent.ID)) {
            return RainColorManager.RUST_R;
        }
        return 1.0F;
    }

    /**
     * Resolves the green color multiplier for precipitation vertices.
     *
     * @return Normalized green component between 0.0 and 1.0
     */
    public static float getTintGreen() {
        if (ClientEventManager.isEventActive(AcidRainEvent.ID)) {
            return RainColorManager.ACID_G;
        }
        if (ClientEventManager.isEventActive(RustRainEvent.ID)) {
            return RainColorManager.RUST_G;
        }
        return 1.0F;
    }

    /**
     * Resolves the blue color multiplier for precipitation vertices.
     *
     * @return Normalized blue component between 0.0 and 1.0
     */
    public static float getTintBlue() {
        if (ClientEventManager.isEventActive(AcidRainEvent.ID)) {
            return RainColorManager.ACID_B;
        }
        if (ClientEventManager.isEventActive(RustRainEvent.ID)) {
            return RainColorManager.RUST_B;
        }
        return 1.0F;
    }
}