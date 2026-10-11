package com.merry.merryevents.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventContextTest {
    @Test
    void returnsDefaultsForMissingValues() {
        EventContext context = new EventContext();

        assertEquals(7, context.getInt("missing-int", 7));
        assertEquals(2.5, context.getDouble("missing-double", 2.5), 0.0);
        assertEquals("fallback", context.getOrDefault("missing-object", "fallback"));
    }

    @Test
    void readsStoredNumbersAsRequestedNumericTypes() {
        EventContext context = new EventContext();
        context.set("integer", 12);
        context.set("decimal", 3.75);

        assertEquals(12, context.getInt("integer", 0));
        assertEquals(12.0, context.getDouble("integer", 0.0), 0.0);
        assertEquals(3, context.getInt("decimal", 0));
        assertEquals(3.75, context.getDouble("decimal", 0.0), 0.0);
    }

    @Test
    void setFlagCanOnlyBeClaimedOnceUntilCleared() {
        EventContext context = new EventContext();

        assertTrue(context.setFlag("once"));
        assertFalse(context.setFlag("once"));

        context.clear();

        assertTrue(context.setFlag("once"));
    }

    @Test
    void clearRemovesStoredValues() {
        EventContext context = new EventContext();
        context.set("value", 42);

        context.clear();

        assertEquals(-1, context.getInt("value", -1));
    }
}
