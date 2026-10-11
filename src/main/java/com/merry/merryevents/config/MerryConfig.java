package com.merry.merryevents.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root configuration container serialized to disk.
 */
public final class MerryConfig {
    private boolean autoDirectorEnabled = true;
    private int minIntervalSeconds = 180;
    private int maxIntervalSeconds = 420;
    private boolean broadcastAnnouncements = true;
    private Map<String, EventConfigEntry> events = new LinkedHashMap<>();

    public MerryConfig() {
    }

    public boolean isAutoDirectorEnabled() {
        return autoDirectorEnabled;
    }

    public void setAutoDirectorEnabled(boolean autoDirectorEnabled) {
        this.autoDirectorEnabled = autoDirectorEnabled;
    }

    public int getMinIntervalSeconds() {
        return minIntervalSeconds;
    }

    public int getMaxIntervalSeconds() {
        return maxIntervalSeconds;
    }

    public boolean shouldBroadcastAnnouncements() {
        return broadcastAnnouncements;
    }

    public Map<String, EventConfigEntry> getEvents() {
        return events;
    }

    /**
     * Enforces valid minimum and maximum interval bounds.
     */
    public void validate() {
        if (minIntervalSeconds <= 10)
            minIntervalSeconds = 10;
        if (maxIntervalSeconds < minIntervalSeconds)
            maxIntervalSeconds = minIntervalSeconds;
    }
}