package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.shader.ClientShaderManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side lifecycle coordinator managing visual, auditory, and input effect
 * drivers.
 * Guarantees idempotent network synchronization, updates remaining tick
 * counters,
 * and hosts the centralized client tick dispatch pipeline.
 */
public final class ClientEventManager {
    private static final Map<Identifier, ClientChaosEffect> EFFECT_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Identifier, ActiveClientEffectEntry> ACTIVE_EFFECTS = new ConcurrentHashMap<>();
    private static boolean tickListenerRegistered = false;

    private ClientEventManager() {
    }

    /**
     * Initializes global client tick event bindings for active effect updates.
     */
    public static void init() {
        if (ClientEventManager.tickListenerRegistered) {
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || ClientEventManager.ACTIVE_EFFECTS.isEmpty()) {
                return;
            }

            for (Map.Entry<Identifier, ActiveClientEffectEntry> entry : ClientEventManager.ACTIVE_EFFECTS.entrySet()) {
                ActiveClientEffectEntry activeEntry = entry.getValue();
                try {
                    activeEntry.effect.onTick(client, activeEntry.remainingTicks);
                } catch (Throwable t) {
                    MerryEvents.LOGGER.error("[ClientEventManager] Exception in client onTick for '{}'",
                            entry.getKey(), t);
                }

                if (activeEntry.remainingTicks > 0) {
                    activeEntry.remainingTicks--;
                }
            }
        });

        ClientEventManager.tickListenerRegistered = true;
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
     * Processes authoritative synchronization payloads from the server.
     * Idempotent: avoids calling onStart multiple times for an already active
     * effect upon reconnect/respawn.
     *
     * @param client        Active Minecraft client instance
     * @param eventId       Target event identifier
     * @param durationTicks Remaining event lifetime in ticks
     * @param active        True if event is active, false if stopped
     * @param startSequence Incremental sequence distinguishing distinct starts from
     *                      re-synchronizations
     */
    public static void handleEventSync(MinecraftClient client, Identifier eventId, int durationTicks, boolean active,
            int startSequence) {
        if (active) {
            ActiveClientEffectEntry existing = ClientEventManager.ACTIVE_EFFECTS.get(eventId);
            if (existing != null) {
                existing.remainingTicks = durationTicks;
                if (existing.startSequence == startSequence) {
                    try {
                        existing.effect.onResume(client);
                    } catch (Throwable t) {
                        MerryEvents.LOGGER.error("[ClientEventManager] Exception in onResume for effect '{}'", eventId,
                                t);
                    }
                    return;
                }

                // Sequence rollover: previous incarnation terminated, new start initiated on
                // server
                try {
                    existing.effect.onStop(client);
                } catch (Throwable t) {
                    MerryEvents.LOGGER.error(
                            "[ClientEventManager] Exception in onStop during sequence rollover for '{}'", eventId, t);
                }
                ClientEventManager.ACTIVE_EFFECTS.remove(eventId);
            }

            ClientChaosEffect effect = ClientEventManager.EFFECT_REGISTRY.get(eventId);
            if (effect != null) {
                ClientEventManager.ACTIVE_EFFECTS.put(eventId,
                        new ActiveClientEffectEntry(effect, durationTicks, startSequence));
                try {
                    effect.onStart(client, durationTicks);
                } catch (Throwable t) {
                    MerryEvents.LOGGER.error("[ClientEventManager] Exception in onStart for effect '{}'", eventId, t);
                }
                MerryEvents.LOGGER.info("[ClientEventManager] Started visual effect: {} (seq={})", eventId,
                        startSequence);
            }
        } else {
            ActiveClientEffectEntry entry = ClientEventManager.ACTIVE_EFFECTS.remove(eventId);
            if (entry != null) {
                try {
                    entry.effect.onStop(client);
                } catch (Throwable t) {
                    MerryEvents.LOGGER.error("[ClientEventManager] Exception in onStop for effect '{}'", eventId, t);
                }
                MerryEvents.LOGGER.info("[ClientEventManager] Stopped visual effect: {}", eventId);
            }
        }
    }

    /**
     * Backward-compatible synchronization handler defaulting sequence to zero.
     *
     * @param client        Active Minecraft client instance
     * @param eventId       Target event identifier
     * @param durationTicks Remaining lifetime in ticks
     * @param active        Active status
     */
    public static void handleEventSync(MinecraftClient client, Identifier eventId, int durationTicks, boolean active) {
        ClientEventManager.handleEventSync(client, eventId, durationTicks, active, 0);
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
     * Resolves the active ignition sequence identifier for the specified effect.
     *
     * @param eventId Target event identifier
     * @return Sequence index, or -1 if the effect is not active
     */
    public static int getEffectSequence(Identifier eventId) {
        ActiveClientEffectEntry entry = ClientEventManager.ACTIVE_EFFECTS.get(eventId);
        return entry != null ? entry.startSequence : -1;
    }

    /**
     * Queries whether any active effect requires the heads-up display to be hidden.
     *
     * @return True if HUD should be suppressed
     */
    public static boolean isHudHidden() {
        for (ActiveClientEffectEntry entry : ClientEventManager.ACTIVE_EFFECTS.values()) {
            if (entry.effect.shouldHideHud()) {
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
        for (ActiveClientEffectEntry entry : ClientEventManager.ACTIVE_EFFECTS.values()) {
            if (entry.effect.isYawLocked()) {
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
        for (ActiveClientEffectEntry entry : ClientEventManager.ACTIVE_EFFECTS.values()) {
            if (entry.effect.isPitchLocked()) {
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
        for (ActiveClientEffectEntry entry : ClientEventManager.ACTIVE_EFFECTS.values()) {
            try {
                entry.effect.onStop(client);
            } catch (Throwable t) {
                MerryEvents.LOGGER.error("[ClientEventManager] Exception during reset onStop", t);
            }
        }
        ClientEventManager.ACTIVE_EFFECTS.clear();
        ClientShaderManager.reset(client);
        MerryEvents.LOGGER.info("[ClientEventManager] Reset all active client visual states.");
    }

    private static final class ActiveClientEffectEntry {
        final ClientChaosEffect effect;
        int remainingTicks;
        final int startSequence;

        ActiveClientEffectEntry(ClientChaosEffect effect, int remainingTicks, int startSequence) {
            this.effect = effect;
            this.remainingTicks = remainingTicks;
            this.startSequence = startSequence;
        }
    }
}