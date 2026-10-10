package com.merry.merryevents.config;

/**
 * Immutable configuration parameters for a specific chaos event.
 *
 * @param enabled Whether the event can be auto-triggered by the ChaosDirector
 * @param minDurationSeconds Minimum randomized duration when auto-triggered
 * @param maxDurationSeconds Maximum randomized duration when auto-triggered
 * @param weight Number of tickets inserted into the shuffle bag deck
 */
public record EventConfigEntry(
    boolean enabled,
    int minDurationSeconds,
    int maxDurationSeconds,
    int weight
) {
    public EventConfigEntry {
        if (minDurationSeconds <= 0) minDurationSeconds = 10;
        if (maxDurationSeconds < minDurationSeconds) maxDurationSeconds = minDurationSeconds;
        if (weight < 1) weight = 1;
    }

    public static EventConfigEntry ofDefault(int minDuration, int maxDuration) {
        return new EventConfigEntry(true, minDuration, maxDuration, 1);
    }
}