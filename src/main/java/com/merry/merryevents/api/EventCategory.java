package com.merry.merryevents.api;

import net.minecraft.text.Text;

/** Universal architectural categories for event taxonomy and scheduling. */
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
    CAMERA("camera"),
    CONTROL("control");

    private final String id;

    EventCategory(String id) {
        this.id = id;
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