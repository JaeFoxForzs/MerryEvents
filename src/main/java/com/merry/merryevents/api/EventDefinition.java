package com.merry.merryevents.api;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.Set;

/**
 * Immutable declaration describing static characteristics, categorization,
 * duration policy, and scheduling weights of a chaos event.
 *
 * @param id                   Unique event namespaced identifier
 * @param categories           Set of conflict categories this event occupies
 * @param scope                Execution scope (GLOBAL or PLAYER)
 * @param durationPolicy       Lifetime termination model
 * @param exclusiveInGroup     True if this event locks its categories
 *                             exclusively
 * @param defaultDurationTicks Default lifetime in server ticks
 * @param defaultWeight        Default selection ticket weight in roulette wheel
 * @param isWip                True to exclude from automated scheduler ignition
 */
public record EventDefinition(
        Identifier id,
        Set<EventCategory> categories,
        EventScope scope,
        DurationPolicy durationPolicy,
        boolean exclusiveInGroup,
        int defaultDurationTicks,
        int defaultWeight,
        boolean isWip) {

    public EventDefinition {
        if (id == null) {
            throw new IllegalArgumentException("Event identifier cannot be null");
        }
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("Event must belong to at least one category");
        }
        categories = Collections.unmodifiableSet(categories);
        if (scope == null) {
            scope = EventScope.PLAYER;
        }
        if (durationPolicy == null) {
            durationPolicy = DurationPolicy.TIMED;
        }
        if (defaultDurationTicks <= 0 && durationPolicy == DurationPolicy.TIMED) {
            defaultDurationTicks = 600; // 30 seconds nominal fallback
        }
        if (defaultWeight < 1) {
            defaultWeight = 1;
        }
    }

    /**
     * Constructs a standard timed event definition with single category.
     *
     * @param id                   Event identifier
     * @param category             Primary category
     * @param scope                Execution scope
     * @param defaultDurationTicks Duration in ticks
     * @param defaultWeight        Selection weight
     * @return Initialized definition instance
     */
    public static EventDefinition standard(
            Identifier id,
            EventCategory category,
            EventScope scope,
            int defaultDurationTicks,
            int defaultWeight) {
        return new EventDefinition(
                id,
                Set.of(category),
                scope,
                DurationPolicy.TIMED,
                true,
                defaultDurationTicks,
                defaultWeight,
                false);
    }
}