package com.merry.merryevents.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Thread-safe configuration manager responsible for disk synchronization and schema updates.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("merryevents.json");

    private static volatile MerryConfig currentConfig = new MerryConfig();

    private ConfigManager() {
    }

    public static MerryConfig get() {
        return currentConfig;
    }

    /**
     * Loads the configuration from disk, creating default schema and population if missing.
     */
    public static synchronized void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
                MerryConfig loaded = GSON.fromJson(reader, MerryConfig.class);
                if (loaded != null) {
                    loaded.validate();
                    currentConfig = loaded;
                    MerryEvents.LOGGER.info("[ConfigManager] Configuration loaded successfully.");
                }
            } catch (Exception e) {
                MerryEvents.LOGGER.error("[ConfigManager] Failed to read config file, falling back to defaults", e);
                currentConfig = new MerryConfig();
            }
        } else {
            currentConfig = new MerryConfig();
            save();
        }
    }

    /**
     * Synchronizes registered events into the config with default durations from game design specs.
     *
     * @param events Registered events map
     */
    public static synchronized void syncRegisteredEvents(Map<net.minecraft.util.Identifier, ChaosEvent> events) {
        boolean changed = false;
        Map<String, EventConfigEntry> eventMap = currentConfig.getEvents();

        for (net.minecraft.util.Identifier id : events.keySet()) {
            String path = id.toString();
            if (!eventMap.containsKey(path)) {
                EventConfigEntry entry = getDefaultEntryFor(id.getPath());
                eventMap.put(path, entry);
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    /**
     * Serializes current configuration to disk atomically.
     */
    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(currentConfig, writer);
            }
        } catch (IOException e) {
            MerryEvents.LOGGER.error("[ConfigManager] Failed to persist config to disk", e);
        }
    }

    private static EventConfigEntry getDefaultEntryFor(String eventPath) {
        return switch (eventPath) {
            case "acid_rain" -> EventConfigEntry.ofDefault(120, 480);
            case "moon_gravity" -> EventConfigEntry.ofDefault(180, 600);
            case "false_water" -> EventConfigEntry.ofDefault(120, 420);
            case "exploding_mobs" -> EventConfigEntry.ofDefault(120, 360);
            case "fragility" -> EventConfigEntry.ofDefault(60, 240);
            case "diamond_rush" -> EventConfigEntry.ofDefault(60, 180);
            case "ghost_gui" -> EventConfigEntry.ofDefault(60, 180);
            case "paranoia_sounds" -> EventConfigEntry.ofDefault(60, 180);
            default -> EventConfigEntry.ofDefault(30, 90);
        };
    }
}