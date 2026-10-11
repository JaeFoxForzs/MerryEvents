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
 * Thread-safe configuration manager responsible for disk persistence and schema
 * synchronization.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("merryevents.json");

    private static volatile MerryConfig currentConfig = new MerryConfig();

    private ConfigManager() {
    }

    /**
     * Resolves the active root configuration instance.
     *
     * @return Active configuration
     */
    public static MerryConfig get() {
        return ConfigManager.currentConfig;
    }

    /**
     * Loads the configuration from disk, creating default schema if missing.
     */
    public static synchronized void load() {
        if (Files.exists(ConfigManager.CONFIG_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(ConfigManager.CONFIG_PATH)) {
                MerryConfig loaded = ConfigManager.GSON.fromJson(reader, MerryConfig.class);
                if (loaded != null) {
                    loaded.validate();
                    ConfigManager.currentConfig = loaded;
                    MerryEvents.LOGGER.info("[ConfigManager] Configuration loaded successfully.");
                }
            } catch (Exception e) {
                MerryEvents.LOGGER.error("[ConfigManager] Failed to read config file, falling back to defaults", e);
                ConfigManager.currentConfig = new MerryConfig();
            }
        } else {
            ConfigManager.currentConfig = new MerryConfig();
            ConfigManager.save();
        }
    }

    /**
     * Synchronizes registered event entries with configuration storage using game
     * design defaults.
     *
     * @param events Registered events map
     */
    public static synchronized void syncRegisteredEvents(Map<net.minecraft.util.Identifier, ChaosEvent> events) {
        if (events == null) {
            return;
        }

        boolean changed = false;
        Map<String, EventConfigEntry> eventMap = ConfigManager.currentConfig.getEvents();

        for (net.minecraft.util.Identifier id : events.keySet()) {
            String path = id.toString();
            if (!eventMap.containsKey(path)) {
                EventConfigEntry entry = ConfigManager.getDefaultEntryFor(id.getPath());
                eventMap.put(path, entry);
                changed = true;
            }
        }

        if (changed) {
            ConfigManager.save();
        }
    }

    /**
     * Serializes current configuration to disk.
     */
    public static synchronized void save() {
        try {
            Files.createDirectories(ConfigManager.CONFIG_PATH.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(ConfigManager.CONFIG_PATH)) {
                ConfigManager.GSON.toJson(ConfigManager.currentConfig, writer);
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
            case "personal_killer" -> EventConfigEntry.ofDefault(60, 240);
            case "anvil_rain" -> EventConfigEntry.ofDefault(60, 180);
            case "dense_darkness" -> EventConfigEntry.ofDefault(60, 180);
            case "orbital_strike" -> EventConfigEntry.ofDefault(30, 120);
            case "stiff_neck" -> EventConfigEntry.ofDefault(60, 240);
            case "solar_verdict" -> EventConfigEntry.ofDefault(600, 1200);
            case "rust_rain" -> EventConfigEntry.ofDefault(60, 180);
            case "food_rain" -> EventConfigEntry.ofDefault(30, 90);
            case "fire_storm" -> EventConfigEntry.ofDefault(30, 90);
            case "ghast_fire_storm" -> EventConfigEntry.ofDefault(30, 90);
            case "lava_water_swap" -> EventConfigEntry.ofDefault(60, 180);
            case "cobweb_nightmare" -> EventConfigEntry.ofDefault(120, 300);
            case "dont_step_on" -> EventConfigEntry.ofDefault(120, 300);
            case "floor_hole" -> EventConfigEntry.ofDefault(60, 240);
            case "crumbling_floor" -> EventConfigEntry.ofDefault(60, 240);
            case "wandering_blocks" -> EventConfigEntry.ofDefault(60, 240);
            case "flat_mobs" -> EventConfigEntry.ofDefault(120, 480);
            case "delayed_damage" -> EventConfigEntry.ofDefault(60, 180);
            case "water_drinker" -> EventConfigEntry.ofDefault(120, 600);
            case "kindergarten" -> EventConfigEntry.ofDefault(180, 480);
            case "perfect_ice" -> EventConfigEntry.ofDefault(120, 240);
            default -> EventConfigEntry.ofDefault(30, 90);
        };
    }
}