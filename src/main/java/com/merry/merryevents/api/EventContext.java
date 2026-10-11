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

    /**
     * Enrolls an entity UUID for automatic discard upon event termination.
     *
     * @param entityUuid Target entity unique identifier
     */
    public void trackEntity(UUID entityUuid) {
        trackedEntities.add(entityUuid);
    }

    /**
     * Discards all tracked alive entities across loaded server worlds.
     *
     * @param server Authoritative MinecraftServer instance
     */
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

    /**
     * Retrieves an integer value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @return Resolved integer
     */
    public int getInt(String key, int defaultValue) {
        Object val = state.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }

    /**
     * Retrieves a double value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @return Resolved double
     */
    public double getDouble(String key, double defaultValue) {
        Object val = state.get(key);
        return val instanceof Number n ? n.doubleValue() : defaultValue;
    }

    /**
     * Retrieves a typed value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @param <T>          Target value type
     * @return Resolved value
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) state.getOrDefault(key, defaultValue);
    }

    /**
     * Stores an arbitrary object into state storage.
     *
     * @param key   State key
     * @param value Stored value
     */
    public void set(String key, Object value) {
        state.put(key, value);
    }

    /**
     * Atomically sets a boolean flag if not previously set.
     *
     * @param key State key
     * @return True if flag was successfully claimed for the first time
     */
    public boolean setFlag(String key) {
        return state.putIfAbsent(key, Boolean.TRUE) == null;
    }

    /**
     * Clears all state maps and entity tracking collections.
     */
    public void clear() {
        state.clear();
        trackedEntities.clear();
    }
}