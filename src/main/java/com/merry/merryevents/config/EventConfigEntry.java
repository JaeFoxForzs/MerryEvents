package com.merry.merryevents.config;

/**
 * Immutable configuration parameters for an individual chaos event.
 *
 * @param enabled            True if the event can be dispatched automatically
 *                           by ChaosDirector
 * @param minDurationSeconds Minimum randomized duration in seconds
 * @param maxDurationSeconds Maximum randomized duration in seconds
 * @param weight             Selection weight tickets in the shuffle bag
 */
public record EventConfigEntry(
        boolean enabled,
        int minDurationSeconds,
        int maxDurationSeconds,
        int weight) {
    public EventConfigEntry {
        if (minDurationSeconds <= 0)
            minDurationSeconds = 10;
        if (maxDurationSeconds < minDurationSeconds)
            maxDurationSeconds = minDurationSeconds;
        if (weight < 1)
            weight = 1;
    }

    /**
     * Creates default configuration parameters with single weight ticket.
     *
     * @param minDuration Minimum duration in seconds
     * @param maxDuration Maximum duration in seconds
     * @return Initialized configuration entry
     */
    public static EventConfigEntry ofDefault(int minDuration, int maxDuration) {
        return new EventConfigEntry(true, minDuration, maxDuration, 1);
    }
}