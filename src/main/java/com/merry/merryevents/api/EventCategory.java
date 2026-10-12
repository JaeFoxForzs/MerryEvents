package com.merry.merryevents.api;

import net.minecraft.text.Text;

/**
 * Universal architectural categories for deterministic event conflict
 * resolution.
 * Each constant encapsulates a bitwise mask enabling O(1) concurrency checks
 * and declares whether non-exclusive concurrent events are permitted.
 */
public enum EventCategory {
    WEATHER("weather", false),
    SKY("sky", false),
    PHYSICS("physics", true),
    WORLD("world", true),
    MOBS("mobs", true),
    STATUS("status", true),
    INVENTORY("inventory", true),
    VISION("vision", false),
    VISUAL("visual", false),
    UI("ui", false),
    SOUNDS("sounds", true),
    CAMERA("camera", false),
    CONTROL("control", false);

    private final String id;
    private final int mask;
    private final boolean allowConcurrent;

    EventCategory(String id, boolean allowConcurrent) {
        this.id = id;
        this.mask = 1 << this.ordinal();
        this.allowConcurrent = allowConcurrent;
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
     * Evaluates whether non-exclusive events are permitted to execute concurrently
     * within this category.
     *
     * @return True if category supports concurrency without mutual exclusion
     */
    public boolean allowsConcurrentEvents() {
        return this.allowConcurrent;
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
        return Text.translatable(this.getTranslationKey());
    }
}