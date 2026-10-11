package com.merry.merryevents.util;

import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Thread-safe spatial entity scanner querying living entities within localized
 * player volumes.
 * Enforces per-tick processing budgets, round-robin player indexing, and
 * isolated snapshot consumption
 * to guarantee immunity against concurrent entity index mutations.
 */
public final class SpatialEntityScanner {

    private SpatialEntityScanner() {
    }

    /**
     * Functional callback invoked for each qualifying entity located within the
     * player interaction zone.
     *
     * @param <T> Target living entity type
     */
    @FunctionalInterface
    public interface EntityTargetProcessor<T extends LivingEntity> {
        /**
         * Processes an individual target entity matching search criteria.
         *
         * @param world  Server world containing the target
         * @param player Origin player establishing localized interaction boundaries
         * @param entity Target living entity being handled
         */
        void process(ServerWorld world, ServerPlayerEntity player, T entity);
    }

    /**
     * Spatial filter predicate evaluating whether a dimension qualifies for entity
     * scanning.
     */
    @FunctionalInterface
    public interface WorldFilter {
        /**
         * Evaluates whether the target server world qualifies for entity scanning
         * routines.
         *
         * @param world Server world candidate to inspect
         * @return True if world properties satisfy execution preconditions
         */
        boolean test(ServerWorld world);
    }

    /**
     * Executes budgeted round-robin living entity processing across active players
     * using isolated entity snapshots.
     * Guarantees zero concurrent modification exceptions when handling lethal
     * damage or entity spawning.
     *
     * @param <T>               Target living entity subclass
     * @param server            Authoritative server instance
     * @param context           Event execution state container storing round-robin
     *                          player indexing
     * @param playerIndexKey    Context storage key preserving circular player
     *                          search offsets
     * @param entityClass       Concrete entity class token for filtered queries
     * @param searchRadius      Horizontal and vertical expansion distance bounding
     *                          each player
     * @param maxTargetsPerTick Upper bound capping total entities processed across
     *                          all players in one tick
     * @param worldFilter       Predicate validating dimension eligibility prior to
     *                          entity lookups
     * @param entityFilter      Predicate filtering prospective targets within query
     *                          bounding boxes
     * @param processor         Consumer executing business logic on validated
     *                          target entities
     * @return Total count of unique entities processed during this scan cycle
     */
    public static <T extends LivingEntity> int scanAndProcess(
            MinecraftServer server,
            EventContext context,
            String playerIndexKey,
            Class<T> entityClass,
            double searchRadius,
            int maxTargetsPerTick,
            WorldFilter worldFilter,
            Predicate<T> entityFilter,
            EntityTargetProcessor<T> processor) {

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            return 0;
        }

        int startPlayerIndex = Math.floorMod(context.getInt(playerIndexKey, 0), players.size());
        context.set(playerIndexKey, (startPlayerIndex + 1) % players.size());

        int processedTargets = 0;
        Set<UUID> processedEntityIds = new HashSet<>();
        double searchRadiusSq = searchRadius * searchRadius;

        for (int playerOffset = 0; playerOffset < players.size()
                && processedTargets < maxTargetsPerTick; playerOffset++) {
            ServerPlayerEntity player = players.get((startPlayerIndex + playerOffset) % players.size());
            if (SpatialTargeting.isHazardImmune(player)) {
                continue;
            }

            ServerWorld world = player.getServerWorld();
            if (!worldFilter.test(world)) {
                continue;
            }

            Box searchArea = player.getBoundingBox().expand(searchRadius);
            List<T> targets = world.getEntitiesByClass(entityClass, searchArea, entityFilter);

            for (int i = 0; i < targets.size(); i++) {
                if (processedTargets >= maxTargetsPerTick) {
                    break;
                }

                T target = targets.get(i);
                if (!processedEntityIds.add(target.getUuid())) {
                    continue;
                }

                if (target.squaredDistanceTo(player.getX(), target.getY(), player.getZ()) > searchRadiusSq) {
                    continue;
                }

                processor.process(world, player, target);
                processedTargets++;
            }
        }

        return processedTargets;
    }
}