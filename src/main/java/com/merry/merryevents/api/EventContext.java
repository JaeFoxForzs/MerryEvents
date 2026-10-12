package com.merry.merryevents.api;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated, thread-safe runtime state container for an active chaos event
 * instance.
 * Supports lifecycle-bound entity tracking, graceful early termination
 * requests,
 * and robust NBT serialization for primitives, strings, collections, and nested
 * compounds.
 */
public final class EventContext {
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final Set<UUID> trackedEntities = ConcurrentHashMap.newKeySet();
    private volatile boolean stopRequested = false;

    /**
     * Enrolls an entity UUID for automatic discard upon event termination.
     *
     * @param entityUuid Target entity unique identifier
     */
    public void trackEntity(UUID entityUuid) {
        if (entityUuid != null) {
            this.trackedEntities.add(entityUuid);
        }
    }

    /**
     * Discards all tracked alive entities across loaded server worlds.
     *
     * @param server Authoritative MinecraftServer instance
     */
    public void discardTrackedEntities(MinecraftServer server) {
        if (this.trackedEntities.isEmpty()) {
            return;
        }

        for (UUID uuid : this.trackedEntities) {
            for (ServerWorld world : server.getWorlds()) {
                var entity = world.getEntity(uuid);
                if (entity != null && entity.isAlive()) {
                    entity.discard();
                    break;
                }
            }
        }
        this.trackedEntities.clear();
    }

    /**
     * Queries enrolled entity identifiers.
     *
     * @return Set of tracked entity UUIDs
     */
    public Set<UUID> getTrackedEntities() {
        return this.trackedEntities;
    }

    /**
     * Requests graceful event termination on the next tick evaluation.
     */
    public void requestStop() {
        this.stopRequested = true;
    }

    /**
     * Queries whether an internal termination request was issued.
     *
     * @return True if event requested early completion
     */
    public boolean isStopRequested() {
        return this.stopRequested;
    }

    /**
     * Retrieves an integer value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @return Resolved integer
     */
    public int getInt(String key, int defaultValue) {
        Object val = this.state.get(key);
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
        Object val = this.state.get(key);
        return val instanceof Number n ? n.doubleValue() : defaultValue;
    }

    /**
     * Retrieves a float value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @return Resolved float
     */
    public float getFloat(String key, float defaultValue) {
        Object val = this.state.get(key);
        return val instanceof Number n ? n.floatValue() : defaultValue;
    }

    /**
     * Retrieves a long value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @return Resolved long
     */
    public long getLong(String key, long defaultValue) {
        Object val = this.state.get(key);
        return val instanceof Number n ? n.longValue() : defaultValue;
    }

    /**
     * Retrieves a boolean value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @return Resolved boolean
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object val = this.state.get(key);
        return val instanceof Boolean b ? b : defaultValue;
    }

    /**
     * Retrieves a string value from state storage.
     *
     * @param key          State key
     * @param defaultValue Default value if absent
     * @return Resolved string
     */
    public String getString(String key, String defaultValue) {
        Object val = this.state.get(key);
        return val instanceof String s ? s : defaultValue;
    }

    /**
     * Retrieves a list of strings from state storage.
     *
     * @param key State key
     * @return Resolved list of strings or empty list if absent
     */
    public List<String> getStringList(String key) {
        Object val = this.state.get(key);
        if (val instanceof List<?> rawList) {
            List<String> result = new ArrayList<>(rawList.size());
            for (Object obj : rawList) {
                if (obj != null) {
                    result.add(obj.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    /**
     * Stores a list of strings into state storage.
     *
     * @param key    State key
     * @param values String collection to store
     */
    public void setStringList(String key, Collection<String> values) {
        if (values == null) {
            this.state.remove(key);
        } else {
            this.state.put(key, new ArrayList<>(values));
        }
    }

    /**
     * Retrieves a set of strings from state storage.
     *
     * @param key State key
     * @return Resolved set of strings or empty set if absent
     */
    public Set<String> getStringSet(String key) {
        return new HashSet<>(this.getStringList(key));
    }

    /**
     * Retrieves a nested NbtCompound from state storage.
     *
     * @param key State key
     * @return Resolved NbtCompound or null if absent
     */
    public NbtCompound getCompound(String key) {
        Object val = this.state.get(key);
        return val instanceof NbtCompound comp ? comp : null;
    }

    /**
     * Stores a nested NbtCompound into state storage.
     *
     * @param key      State key
     * @param compound Target NbtCompound
     */
    public void setCompound(String key, NbtCompound compound) {
        if (compound == null) {
            this.state.remove(key);
        } else {
            this.state.put(key, compound);
        }
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
        return (T) this.state.getOrDefault(key, defaultValue);
    }

    /**
     * Stores an arbitrary object into state storage.
     *
     * @param key   State key
     * @param value Stored value
     */
    public void set(String key, Object value) {
        if (value == null) {
            this.state.remove(key);
        } else {
            this.state.put(key, value);
        }
    }

    /**
     * Atomically sets a boolean flag if not previously set.
     *
     * @param key State key
     * @return True if flag was successfully claimed for the first time
     */
    public boolean setFlag(String key) {
        return this.state.putIfAbsent(key, Boolean.TRUE) == null;
    }

    /**
     * Clears all state maps and entity tracking collections.
     */
    public void clear() {
        this.state.clear();
        this.trackedEntities.clear();
        this.stopRequested = false;
    }

    /**
     * Serializes contextual state and tracked entity references into target NBT
     * storage.
     *
     * @return Populated NbtCompound representing isolated context state
     */
    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        NbtCompound values = new NbtCompound();

        for (Map.Entry<String, Object> entry : this.state.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();

            if (v instanceof Integer i) {
                values.putInt(k, i);
            } else if (v instanceof Double d) {
                values.putDouble(k, d);
            } else if (v instanceof Float f) {
                values.putFloat(k, f);
            } else if (v instanceof Long l) {
                values.putLong(k, l);
            } else if (v instanceof Boolean b) {
                values.putBoolean(k, b);
            } else if (v instanceof String s) {
                values.putString(k, s);
            } else if (v instanceof UUID uuid) {
                values.putUuid(k, uuid);
            } else if (v instanceof NbtCompound comp) {
                values.put(k, comp.copy());
            } else if (v instanceof Collection<?> col) {
                NbtList listTag = new NbtList();
                for (Object item : col) {
                    if (item != null) {
                        listTag.add(NbtString.of(item.toString()));
                    }
                }
                values.put(k, listTag);
            }
        }
        tag.put("values", values);

        NbtList entityList = new NbtList();
        for (UUID uuid : this.trackedEntities) {
            NbtCompound entityTag = new NbtCompound();
            entityTag.putUuid("id", uuid);
            entityList.add(entityTag);
        }
        tag.put("trackedEntities", entityList);
        tag.putBoolean("stopRequested", this.stopRequested);

        return tag;
    }

    /**
     * Deserializes contextual state and tracked entity references from NBT storage.
     *
     * @param tag Source NbtCompound
     */
    public void fromNbt(NbtCompound tag) {
        this.clear();
        if (tag == null) {
            return;
        }

        if (tag.contains("values", NbtElement.COMPOUND_TYPE)) {
            NbtCompound values = tag.getCompound("values");
            for (String key : values.getKeys()) {
                byte type = values.getType(key);
                switch (type) {
                    case NbtElement.INT_TYPE -> this.state.put(key, values.getInt(key));
                    case NbtElement.DOUBLE_TYPE -> this.state.put(key, values.getDouble(key));
                    case NbtElement.FLOAT_TYPE -> this.state.put(key, values.getFloat(key));
                    case NbtElement.LONG_TYPE -> this.state.put(key, values.getLong(key));
                    case NbtElement.BYTE_TYPE -> this.state.put(key, values.getBoolean(key));
                    case NbtElement.STRING_TYPE -> this.state.put(key, values.getString(key));
                    case NbtElement.COMPOUND_TYPE -> this.state.put(key, values.getCompound(key));
                    case NbtElement.LIST_TYPE -> {
                        NbtList rawList = values.getList(key, NbtElement.STRING_TYPE);
                        List<String> items = new ArrayList<>(rawList.size());
                        for (int i = 0; i < rawList.size(); i++) {
                            items.add(rawList.getString(i));
                        }
                        this.state.put(key, items);
                    }
                    case NbtElement.INT_ARRAY_TYPE -> {
                        if (values.containsUuid(key)) {
                            this.state.put(key, values.getUuid(key));
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        if (tag.contains("trackedEntities", NbtElement.LIST_TYPE)) {
            NbtList entityList = tag.getList("trackedEntities", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < entityList.size(); i++) {
                NbtCompound entityTag = entityList.getCompound(i);
                if (entityTag.containsUuid("id")) {
                    this.trackedEntities.add(entityTag.getUuid("id"));
                }
            }
        }

        this.stopRequested = tag.getBoolean("stopRequested");
    }
}