package com.merry.merryevents.api;

import net.minecraft.text.Text;

/**
 * Universal architectural categories for deterministic event conflict resolution.
 * Each constant encapsulates a bitwise mask enabling O(1) concurrency checks.
 */
public enum EventCategory {
    WEATHER("weather"),
    PHYSICS("physics"),
    WORLD("world"),
    MOBS("mobs"),
    STATUS("status"),
    INVENTORY("inventory"),
    VISUAL("visual"),
    SOUNDS("sounds");

    private final String id;
    private final int mask;

    EventCategory(String id) {
        this.id = id;
        this.mask = 1 << this.ordinal();
    }

    public String getId() {
        return this.id;
    }

    public int getMask() {
        return this.mask;
    }

    public String getTranslationKey() {
        return "category.merryevents." + this.id;
    }

    public Text getDisplayName() {
        return Text.translatable(getTranslationKey());
    }
}