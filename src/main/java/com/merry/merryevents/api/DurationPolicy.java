package com.merry.merryevents.api;

/**
 * Defines how an event's active duration is tracked and terminated.
 */
public enum DurationPolicy {
    /**
     * Decrements standard server ticks until exhaustion.
     */
    TIMED,

    /**
     * Executes initial ignition routines and immediately terminates in the same
     * server tick.
     */
    INSTANT,

    /**
     * Persists indefinitely until explicit conditions request early termination via
     * EventContext.
     */
    UNTIL_CONDITION
}