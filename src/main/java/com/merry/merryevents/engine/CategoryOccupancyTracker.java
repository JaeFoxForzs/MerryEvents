package com.merry.merryevents.engine;

import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventDefinition;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe category occupancy coordinator maintaining isolated global and
 * per-player execution buckets.
 * Enforces cross-stream mutual exclusion, handles multi-category declarations,
 * and tracks concurrent event counts.
 */
public final class CategoryOccupancyTracker {
    private final Map<EventCategory, CategoryBucket> globalBuckets = new EnumMap<>(EventCategory.class);
    private final Map<UUID, Map<EventCategory, CategoryBucket>> playerBuckets = new ConcurrentHashMap<>();

    public CategoryOccupancyTracker() {
        for (EventCategory category : EventCategory.values()) {
            this.globalBuckets.put(category, new CategoryBucket());
        }
    }

    /**
     * Evaluates whether all categories required by the prospective global event are
     * currently available globally.
     * Personal events do not block global events because they are preempted upon
     * ignition.
     *
     * @param definition Prospective global event declaration
     * @return True if all target categories can be claimed globally
     */
    public synchronized boolean canStartGlobal(EventDefinition definition) {
        Set<EventCategory> categories = definition.categories();
        boolean exclusive = CategoryOccupancyTracker.isEffectivelyExclusive(definition);

        for (EventCategory category : categories) {
            CategoryBucket globalBucket = this.globalBuckets.get(category);
            if (globalBucket == null) {
                continue;
            }

            if (exclusive) {
                if (!globalBucket.isEmpty()) {
                    return false;
                }
            } else {
                if (globalBucket.exclusiveEventId != null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Evaluates whether all categories required by the prospective player event are
     * currently available.
     * Blocked if an active global event occupies any target category or if the
     * player already runs an event in them.
     *
     * @param playerUuid Target player unique identifier
     * @param definition Prospective personal event declaration
     * @return True if all target categories can be claimed for this player
     */
    public synchronized boolean canStartPlayer(UUID playerUuid, EventDefinition definition) {
        Set<EventCategory> categories = definition.categories();
        boolean exclusive = CategoryOccupancyTracker.isEffectivelyExclusive(definition);

        for (EventCategory category : categories) {
            CategoryBucket globalBucket = this.globalBuckets.get(category);
            if (globalBucket != null) {
                if (globalBucket.exclusiveEventId != null) {
                    return false;
                }
                if (exclusive && !globalBucket.isEmpty()) {
                    return false;
                }
            }

            Map<EventCategory, CategoryBucket> pMap = this.playerBuckets.get(playerUuid);
            if (pMap != null) {
                CategoryBucket pBucket = pMap.get(category);
                if (pBucket != null) {
                    if (exclusive) {
                        if (!pBucket.isEmpty()) {
                            return false;
                        }
                    } else {
                        if (pBucket.exclusiveEventId != null) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    /**
     * Atomically claims required categories for an active global event instance.
     *
     * @param definition Active global event declaration
     */
    public synchronized void acquireGlobal(EventDefinition definition) {
        boolean exclusive = CategoryOccupancyTracker.isEffectivelyExclusive(definition);
        Identifier id = definition.id();

        for (EventCategory category : definition.categories()) {
            CategoryBucket bucket = this.globalBuckets.computeIfAbsent(category, k -> new CategoryBucket());
            if (exclusive) {
                bucket.exclusiveEventId = id;
            } else {
                bucket.nonExclusiveCount++;
            }
        }
    }

    /**
     * Atomically releases occupied categories following global event termination.
     *
     * @param definition Terminated global event declaration
     */
    public synchronized void releaseGlobal(EventDefinition definition) {
        boolean exclusive = CategoryOccupancyTracker.isEffectivelyExclusive(definition);

        for (EventCategory category : definition.categories()) {
            CategoryBucket bucket = this.globalBuckets.get(category);
            if (bucket != null) {
                if (exclusive) {
                    bucket.exclusiveEventId = null;
                } else {
                    bucket.nonExclusiveCount = Math.max(0, bucket.nonExclusiveCount - 1);
                }
            }
        }
    }

    /**
     * Atomically claims required categories for an active personal event instance.
     *
     * @param playerUuid Target player unique identifier
     * @param definition Active personal event declaration
     */
    public synchronized void acquirePlayer(UUID playerUuid, EventDefinition definition) {
        boolean exclusive = CategoryOccupancyTracker.isEffectivelyExclusive(definition);
        Identifier id = definition.id();

        Map<EventCategory, CategoryBucket> pMap = this.playerBuckets.computeIfAbsent(playerUuid,
                k -> new EnumMap<>(EventCategory.class));
        for (EventCategory category : definition.categories()) {
            CategoryBucket bucket = pMap.computeIfAbsent(category, k -> new CategoryBucket());
            if (exclusive) {
                bucket.exclusiveEventId = id;
            } else {
                bucket.nonExclusiveCount++;
            }
        }
    }

    /**
     * Atomically releases occupied categories following personal event termination.
     *
     * @param playerUuid Target player unique identifier
     * @param definition Terminated personal event declaration
     */
    public synchronized void releasePlayer(UUID playerUuid, EventDefinition definition) {
        boolean exclusive = CategoryOccupancyTracker.isEffectivelyExclusive(definition);

        Map<EventCategory, CategoryBucket> pMap = this.playerBuckets.get(playerUuid);
        if (pMap == null) {
            return;
        }

        for (EventCategory category : definition.categories()) {
            CategoryBucket bucket = pMap.get(category);
            if (bucket != null) {
                if (exclusive) {
                    bucket.exclusiveEventId = null;
                } else {
                    bucket.nonExclusiveCount = Math.max(0, bucket.nonExclusiveCount - 1);
                }
                if (bucket.isEmpty()) {
                    pMap.remove(category);
                }
            }
        }

        if (pMap.isEmpty()) {
            this.playerBuckets.remove(playerUuid);
        }
    }

    /**
     * Purges all tracked categories and buckets associated with an offline player
     * session.
     *
     * @param playerUuid Disconnected player identifier
     */
    public synchronized void clearPlayer(UUID playerUuid) {
        this.playerBuckets.remove(playerUuid);
    }

    /**
     * Resets all global and personal occupancy buckets upon server shutdown.
     */
    public synchronized void clearAll() {
        for (CategoryBucket bucket : this.globalBuckets.values()) {
            bucket.clear();
        }
        this.playerBuckets.clear();
    }

    /**
     * Evaluates whether a category is occupied either globally or on the specified
     * player.
     *
     * @param playerUuid Target player UUID, or null to query global scope only
     * @param category   Category to query
     * @return True if category has any active non-exclusive count or exclusive
     *         holder
     */
    public synchronized boolean isCategoryOccupied(@Nullable UUID playerUuid, EventCategory category) {
        CategoryBucket globalBucket = this.globalBuckets.get(category);
        if (globalBucket != null && !globalBucket.isEmpty()) {
            return true;
        }

        if (playerUuid != null) {
            Map<EventCategory, CategoryBucket> pMap = this.playerBuckets.get(playerUuid);
            if (pMap != null) {
                CategoryBucket pBucket = pMap.get(category);
                return pBucket != null && !pBucket.isEmpty();
            }
        }

        return false;
    }

    private static boolean isEffectivelyExclusive(EventDefinition definition) {
        if (definition.exclusiveInGroup()) {
            return true;
        }
        for (EventCategory category : definition.categories()) {
            if (!category.allowsConcurrentEvents()) {
                return true;
            }
        }
        return false;
    }

    private static final class CategoryBucket {
        int nonExclusiveCount = 0;
        @Nullable
        Identifier exclusiveEventId = null;

        boolean isEmpty() {
            return this.nonExclusiveCount == 0 && this.exclusiveEventId == null;
        }

        void clear() {
            this.nonExclusiveCount = 0;
            this.exclusiveEventId = null;
        }
    }
}