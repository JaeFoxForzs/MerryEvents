package com.merry.merryevents.api;

/**
 * Defines the architectural distribution scope of a chaos event.
 */
public enum EventScope {
    /**
     * Applies to all online players and surface worlds simultaneously.
     */
    GLOBAL,

    /**
     * Bound exclusively to an individual player session.
     */
    PLAYER
}