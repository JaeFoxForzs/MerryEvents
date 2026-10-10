package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.shader.ClientShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientEventManager {
    private static final Map<Identifier, ClientChaosEffect> EFFECT_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<Identifier, ClientChaosEffect> ACTIVE_EFFECTS = new ConcurrentHashMap<>();

    private ClientEventManager() {
    }

    public static void registerEffect(Identifier eventId, ClientChaosEffect effect) {
        EFFECT_REGISTRY.put(eventId, effect);
    }

    public static void handleEventSync(MinecraftClient client, Identifier eventId, int durationTicks, boolean active) {
        if (active) {
            ClientChaosEffect effect = EFFECT_REGISTRY.get(eventId);
            if (effect != null) {
                ACTIVE_EFFECTS.put(eventId, effect);
                effect.onStart(client, durationTicks);
                MerryEvents.LOGGER.info("[ClientEventManager] Started visual effect: {}", eventId);
            }
        } else {
            ClientChaosEffect effect = ACTIVE_EFFECTS.remove(eventId);
            if (effect != null) {
                effect.onStop(client);
                MerryEvents.LOGGER.info("[ClientEventManager] Stopped visual effect: {}", eventId);
            }
        }
    }

    public static boolean isEventActive(Identifier eventId) {
        return ACTIVE_EFFECTS.containsKey(eventId);
    }

    public static boolean isHudHidden() {
        for (ClientChaosEffect effect : ACTIVE_EFFECTS.values()) {
            if (effect.shouldHideHud()) {
                return true;
            }
        }
        return false;
    }

    public static void reset(MinecraftClient client) {
        for (ClientChaosEffect effect : ACTIVE_EFFECTS.values()) {
            effect.onStop(client);
        }
        ACTIVE_EFFECTS.clear();
        ClientShaderManager.reset(client);
        MerryEvents.LOGGER.info("[ClientEventManager] Reset all active client visual states.");
    }
}