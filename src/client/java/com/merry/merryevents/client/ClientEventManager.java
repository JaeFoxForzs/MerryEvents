package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.shader.ClientShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side lifecycle manager coordinating visual and auditory chaos effects.
 * Receives server synchronization packets, tracks active effects, and handles
 * graceful resets.
 */
public final class ClientEventManager {
    private static final Map<Identifier, ClientChaosEffect> EFFECT_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Identifier, ClientChaosEffect> ACTIVE_EFFECTS = new ConcurrentHashMap<>();

    private ClientEventManager() {
    }

    /**
     * Registers a client effect driver mapped to a specific chaos event identifier.
     *
     * @param eventId Target event identifier
     * @param effect  Client effect driver implementation
     */
    public static void registerEffect(Identifier eventId, ClientChaosEffect effect) {
        ClientEventManager.EFFECT_REGISTRY.put(eventId, effect);
    }

    /**
     * Processes authoritative synchronization payloads from the server to toggle
     * effects.
     *
     * @param client        Active Minecraft client instance
     * @param eventId       Target event identifier
     * @param durationTicks Remaining event lifetime in ticks
     * @param active        True if event is active, false if stopped
     */
    public static void handleEventSync(MinecraftClient client, Identifier eventId, int durationTicks, boolean active) {
        if (active) {
            ClientChaosEffect effect = ClientEventManager.EFFECT_REGISTRY.get(eventId);
            if (effect != null) {
                ClientEventManager.ACTIVE_EFFECTS.put(eventId, effect);
                effect.onStart(client, durationTicks);
                MerryEvents.LOGGER.info("[ClientEventManager] Started visual effect: {}", eventId);
            }
        } else {
            ClientChaosEffect effect = ClientEventManager.ACTIVE_EFFECTS.remove(eventId);
            if (effect != null) {
                effect.onStop(client);
                MerryEvents.LOGGER.info("[ClientEventManager] Stopped visual effect: {}", eventId);
            }
        }
    }

    /**
     * Queries whether an effect is actively rendering on the client.
     *
     * @param eventId Target event identifier
     * @return True if currently active
     */
    public static boolean isEventActive(Identifier eventId) {
        return ClientEventManager.ACTIVE_EFFECTS.containsKey(eventId);
    }

    /**
     * Queries whether any active effect requires the heads-up display to be hidden.
     *
     * @return True if HUD should be suppressed
     */
    public static boolean isHudHidden() {
        for (ClientChaosEffect effect : ClientEventManager.ACTIVE_EFFECTS.values()) {
            if (effect.shouldHideHud()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queries whether horizontal mouse look input is locked by any active effect.
     *
     * @return True if yaw rotation should be locked
     */
    public static boolean isYawLocked() {
        for (ClientChaosEffect effect : ClientEventManager.ACTIVE_EFFECTS.values()) {
            if (effect.isYawLocked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queries whether vertical mouse look input is locked by any active effect.
     *
     * @return True if pitch rotation should be locked
     */
    public static boolean isPitchLocked() {
        for (ClientChaosEffect effect : ClientEventManager.ACTIVE_EFFECTS.values()) {
            if (effect.isPitchLocked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stops all active effects and resets shaders upon server disconnection.
     *
     * @param client Active Minecraft client instance
     */
    public static void reset(MinecraftClient client) {
        for (ClientChaosEffect effect : ClientEventManager.ACTIVE_EFFECTS.values()) {
            effect.onStop(client);
        }
        ClientEventManager.ACTIVE_EFFECTS.clear();
        ClientShaderManager.reset(client);
        MerryEvents.LOGGER.info("[ClientEventManager] Reset all active client visual states.");
    }
}