
package com.merry.merryevents.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventConfigEntryTest {
    @Test
    void normalizesNonPositiveMinimumDurationAndWeight() {
        EventConfigEntry entry = new EventConfigEntry(true, 0, 5, 0);

        assertEquals(10, entry.minDurationSeconds());
        assertEquals(10, entry.maxDurationSeconds());
        assertEquals(1, entry.weight());
    }

    @Test
    void raisesMaximumDurationToMinimumWhenRangeIsReversed() {
        EventConfigEntry entry = new EventConfigEntry(false, 30, 10, 2);

        assertEquals(30, entry.minDurationSeconds());
        assertEquals(30, entry.maxDurationSeconds());
        assertEquals(2, entry.weight());
        assertFalse(entry.enabled());
    }

    @Test
    void preservesValidValues() {
        EventConfigEntry entry = new EventConfigEntry(true, 15, 45, 3);

        assertEquals(15, entry.minDurationSeconds());
        assertEquals(45, entry.maxDurationSeconds());
        assertEquals(3, entry.weight());
        assertTrue(entry.enabled());
    }
}
