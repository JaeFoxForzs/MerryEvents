package com.merry.merryevents.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated state container for an active event instance.
 * Prevents memory leaks and provides lifecycle-bound entity tracking.
 */
public final class EventContext {
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final Set<UUID> trackedEntities = ConcurrentHashMap.newKeySet();

    public void trackEntity(UUID entityUuid) {
        trackedEntities.add(entityUuid);
    }

    public void discardTrackedEntities(MinecraftServer server) {
        for (UUID uuid : trackedEntities) {
            for (ServerWorld world : server.getWorlds()) {
                var entity = world.getEntity(uuid);
                if (entity != null && entity.isAlive()) {
                    entity.discard();
                    break;
                }
            }
        }
        trackedEntities.clear();
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) state.getOrDefault(key, defaultValue);
    }

    public void set(String key, Object value) {
        state.put(key, value);
    }

    public boolean setFlag(String key) {
        return state.putIfAbsent(key, Boolean.TRUE) == null;
    }

    public void clear() {
        state.clear();
        trackedEntities.clear();
    }
}