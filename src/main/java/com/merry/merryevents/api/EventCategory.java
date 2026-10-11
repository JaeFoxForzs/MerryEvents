package com.merry.merryevents.api;

import net.minecraft.text.Text;

/**
 * Universal architectural categories for deterministic event conflict
 * resolution.
 * Each constant encapsulates a bitwise mask enabling O(1) concurrency checks.
 */
public enum EventCategory {
    WEATHER("weather"),
    SKY("sky"),
    PHYSICS("physics"),
    WORLD("world"),
    MOBS("mobs"),
    STATUS("status"),
    INVENTORY("inventory"),
    VISION("vision"),
    VISUAL("visual"),
    UI("ui"),
    SOUNDS("sounds"),
    CAMERA("camera");

    private final String id;
    private final int mask;

    EventCategory(String id) {
        this.id = id;
        this.mask = 1 << this.ordinal();
    }

    /**
     * Resolves raw category identifier.
     *
     * @return String category identifier
     */
    public String getId() {
        return this.id;
    }

    /**
     * Resolves bitwise concurrency mask.
     *
     * @return Integer bitmask
     */
    public int getMask() {
        return this.mask;
    }

    /**
     * Resolves localization translation key.
     *
     * @return Translation key
     */
    public String getTranslationKey() {
        return "category.merryevents." + this.id;
    }

    /**
     * Resolves localized display text.
     *
     * @return Display text component
     */
    public Text getDisplayName() {
        return Text.translatable(getTranslationKey());
    }
}