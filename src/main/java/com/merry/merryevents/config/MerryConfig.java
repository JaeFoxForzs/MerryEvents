package com.merry.merryevents.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root configuration container serialized to disk.
 * Encapsulates dual scheduler interval boundaries, retry backoff divisors, and
 * per-event settings.
 */
public final class MerryConfig {
    private boolean autoDirectorEnabled = true;
    private int minGlobalIntervalSeconds = 180;
    private int maxGlobalIntervalSeconds = 600;
    private int minPersonalIntervalSeconds = 60;
    private int maxPersonalIntervalSeconds = 240;
    private int retryDivisor = 3;
    private boolean broadcastAnnouncements = true;
    private Map<String, EventConfigEntry> events = new LinkedHashMap<>();

    public MerryConfig() {
    }

    public boolean isAutoDirectorEnabled() {
        return this.autoDirectorEnabled;
    }

    public void setAutoDirectorEnabled(boolean autoDirectorEnabled) {
        this.autoDirectorEnabled = autoDirectorEnabled;
    }

    public int getMinGlobalIntervalSeconds() {
        return this.minGlobalIntervalSeconds;
    }

    public void setMinGlobalIntervalSeconds(int minGlobalIntervalSeconds) {
        this.minGlobalIntervalSeconds = minGlobalIntervalSeconds;
    }

    public int getMaxGlobalIntervalSeconds() {
        return this.maxGlobalIntervalSeconds;
    }

    public void setMaxGlobalIntervalSeconds(int maxGlobalIntervalSeconds) {
        this.maxGlobalIntervalSeconds = maxGlobalIntervalSeconds;
    }

    public int getMinPersonalIntervalSeconds() {
        return this.minPersonalIntervalSeconds;
    }

    public void setMinPersonalIntervalSeconds(int minPersonalIntervalSeconds) {
        this.minPersonalIntervalSeconds = minPersonalIntervalSeconds;
    }

    public int getMaxPersonalIntervalSeconds() {
        return this.maxPersonalIntervalSeconds;
    }

    public void setMaxPersonalIntervalSeconds(int maxPersonalIntervalSeconds) {
        this.maxPersonalIntervalSeconds = maxPersonalIntervalSeconds;
    }

    public int getRetryDivisor() {
        return this.retryDivisor;
    }

    public void setRetryDivisor(int retryDivisor) {
        this.retryDivisor = retryDivisor;
    }

    public int getMinIntervalSeconds() {
        return this.minGlobalIntervalSeconds;
    }

    public int getMaxIntervalSeconds() {
        return this.maxGlobalIntervalSeconds;
    }

    public boolean shouldBroadcastAnnouncements() {
        return this.broadcastAnnouncements;
    }

    public void setBroadcastAnnouncements(boolean broadcastAnnouncements) {
        this.broadcastAnnouncements = broadcastAnnouncements;
    }

    public Map<String, EventConfigEntry> getEvents() {
        return this.events;
    }

    /**
     * Enforces valid minimum and maximum interval bounds across global and personal
     * schedulers.
     */
    public void validate() {
        if (this.minGlobalIntervalSeconds <= 10) {
            this.minGlobalIntervalSeconds = 10;
        }
        if (this.maxGlobalIntervalSeconds < this.minGlobalIntervalSeconds) {
            this.maxGlobalIntervalSeconds = this.minGlobalIntervalSeconds;
        }
        if (this.minPersonalIntervalSeconds <= 10) {
            this.minPersonalIntervalSeconds = 10;
        }
        if (this.maxPersonalIntervalSeconds < this.minPersonalIntervalSeconds) {
            this.maxPersonalIntervalSeconds = this.minPersonalIntervalSeconds;
        }
        if (this.retryDivisor < 1) {
            this.retryDivisor = 1;
        }
    }
}