package com.merry.merryevents.test;

import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.api.DurationPolicy;
import com.merry.merryevents.api.EventDefinition;
import com.merry.merryevents.api.EventScope;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class EventManagerCategoryOccupancyTest {

        private final Identifier testGlobalId = Identifier.of("test", "global_vision");
        private final Identifier testPersonalId = Identifier.of("test", "personal_vision");
        private final UUID testPlayerUuid = UUID.randomUUID();
        private final UUID otherPlayerUuid = UUID.randomUUID();

        private EventManager manager;
        private DummyEvent globalVisionEvent;
        private DummyEvent personalVisionEvent;

        @BeforeEach
        public void setup() {
                this.manager = EventManager.getInstance();
                this.manager.resetRuntimeState();

                this.globalVisionEvent = new DummyEvent(this.testGlobalId, EventCategory.VISION, true,
                                Set.of(this.testPersonalId));
                this.personalVisionEvent = new DummyEvent(this.testPersonalId, EventCategory.VISION, false);

                this.manager.register(this.globalVisionEvent);
                this.manager.register(this.personalVisionEvent);
                this.manager.buildConflictMatrix();
        }

        @Test
        @DisplayName("Global events preempt only explicitly conflicting personal events")
        public void testCategoryOccupancyAndGlobalPreemption() {
                boolean startedPersonal = this.manager.startPlayerEvent(null, this.testPlayerUuid, null,
                                this.testPersonalId,
                                200);
                Assertions.assertTrue(startedPersonal, "Personal event must start successfully for Player 1");
                Assertions.assertTrue(this.manager.isPlayerEventActive(this.testPlayerUuid, this.testPersonalId));
                Assertions.assertTrue(this.manager.canStartGlobal(this.globalVisionEvent));
                boolean startedGlobal = this.manager.startGlobalEvent(null, this.testGlobalId, 300);
                Assertions.assertTrue(startedGlobal,
                                "Global event must start and preempt conflicting personal events");
                Assertions.assertFalse(this.manager.isPlayerEventActive(this.testPlayerUuid, this.testPersonalId),
                                "Personal event on Player 1 must be preempted and removed");
                Assertions.assertTrue(this.personalVisionEvent.stopped,
                                "Preempted personal event must receive onStopServer callback");
                Assertions.assertTrue(this.manager.isGlobalEventActive(this.testGlobalId));
                Assertions.assertFalse(this.manager.canStartPlayer(this.testPlayerUuid, this.personalVisionEvent));
                Assertions.assertFalse(this.manager.canStartPlayer(this.otherPlayerUuid, this.personalVisionEvent));
        }

        @Test
        @DisplayName("Non-exclusive global events can share a concurrent category")
        public void testConcurrentGlobalEventsCanShareCategory() {
                ConfiguredDummyEvent first = this.registerConfiguredEvent(
                                Identifier.of("test", "global_physics_first"), EventCategory.PHYSICS,
                                Set.of(EventCategory.PHYSICS), true, false);
                ConfiguredDummyEvent second = this.registerConfiguredEvent(
                                Identifier.of("test", "global_physics_second"), EventCategory.PHYSICS,
                                Set.of(EventCategory.PHYSICS), true, false);

                Assertions.assertTrue(this.manager.canStartGlobal(first));
                Assertions.assertTrue(this.manager.startGlobalEvent(null, first.getId(), 200));
                Assertions.assertTrue(this.manager.canStartGlobal(second));
                Assertions.assertTrue(this.manager.startGlobalEvent(null, second.getId(), 200));
        }

        @Test
        @DisplayName("Non-exclusive personal events can share a concurrent category for one player")
        public void testConcurrentPersonalEventsCanShareCategory() {
                ConfiguredDummyEvent first = this.registerConfiguredEvent(
                                Identifier.of("test", "personal_physics_first"), EventCategory.PHYSICS,
                                Set.of(EventCategory.PHYSICS), false, false);
                ConfiguredDummyEvent second = this.registerConfiguredEvent(
                                Identifier.of("test", "personal_physics_second"), EventCategory.PHYSICS,
                                Set.of(EventCategory.PHYSICS), false, false);

                Assertions.assertTrue(this.manager.canStartPlayer(this.testPlayerUuid, first));
                Assertions.assertTrue(
                                this.manager.startPlayerEvent(null, this.testPlayerUuid, null, first.getId(), 200));
                Assertions.assertTrue(this.manager.canStartPlayer(this.testPlayerUuid, second));
                Assertions.assertTrue(
                                this.manager.startPlayerEvent(null, this.testPlayerUuid, null, second.getId(), 200));
        }

        @Test
        @DisplayName("Candidate category checks include every declared category")
        public void testCandidateChecksEveryDeclaredCategory() {
                ConfiguredDummyEvent occupyingEvent = this.registerConfiguredEvent(
                                Identifier.of("test", "personal_status_occupant"), EventCategory.STATUS,
                                Set.of(EventCategory.STATUS), false, true);
                ConfiguredDummyEvent candidate = this.registerConfiguredEvent(
                                Identifier.of("test", "personal_multicategory_candidate"), EventCategory.PHYSICS,
                                new LinkedHashSet<>(Set.of(EventCategory.PHYSICS, EventCategory.STATUS)), false, true);

                Assertions.assertTrue(this.manager.startPlayerEvent(null, this.testPlayerUuid, null,
                                occupyingEvent.getId(), 200));
                Assertions.assertTrue(this.manager.canStartPlayer(this.testPlayerUuid, candidate));
        }

        @Test
        @DisplayName("Same-category events are compatible unless they declare a conflict")
        public void testCategoryDoesNotBlockConcurrency() {
                ConfiguredDummyEvent first = this.registerConfiguredEvent(
                                Identifier.of("test", "personal_vision_first"), EventCategory.VISION,
                                Set.of(EventCategory.VISION), false, false);
                ConfiguredDummyEvent second = this.registerConfiguredEvent(
                                Identifier.of("test", "personal_vision_second"), EventCategory.VISION,
                                Set.of(EventCategory.VISION), false, false);

                Assertions.assertTrue(
                                this.manager.startPlayerEvent(null, this.testPlayerUuid, null, first.getId(), 200));
                Assertions.assertTrue(this.manager.canStartPlayer(this.testPlayerUuid, second));
        }

        @Test
        @DisplayName("A player cannot exceed the personal event concurrency cap")
        public void testPersonalEventConcurrencyCap() {
                for (int i = 1; i <= EventManager.MAX_CONCURRENT_PLAYER_EVENTS; i++) {
                        ConfiguredDummyEvent event = this.registerConfiguredEvent(
                                        Identifier.of("test", "personal_cap_" + i), EventCategory.STATUS,
                                        Set.of(EventCategory.STATUS), false, false);
                        Assertions.assertTrue(this.manager.startPlayerEvent(
                                        null, this.testPlayerUuid, null, event.getId(), 200));
                }
                ConfiguredDummyEvent overLimit = this.registerConfiguredEvent(
                                Identifier.of("test", "personal_cap_over_limit"), EventCategory.STATUS,
                                Set.of(EventCategory.STATUS), false, false);
                Assertions.assertFalse(this.manager.startPlayerEvent(
                                null, this.testPlayerUuid, null, overLimit.getId(), 200));
        }

        @Test
        @DisplayName("Declared conflicts are symmetric and global events preempt only conflicting personal events")
        public void testDeclaredConflictAndPreemption() {
                Identifier globalId = Identifier.of("test", "global_conflict");
                Identifier personalId = Identifier.of("test", "personal_conflict");
                ConfiguredDummyEvent global = new ConfiguredDummyEvent(globalId, EventCategory.VISION,
                                Set.of(EventCategory.VISION), true, Set.of(personalId));
                ConfiguredDummyEvent personal = new ConfiguredDummyEvent(personalId, EventCategory.CAMERA,
                                Set.of(EventCategory.CAMERA), false, Set.of());
                this.manager.register(global);
                this.manager.register(personal);
                this.manager.buildConflictMatrix();

                Assertions.assertTrue(this.manager.startPlayerEvent(null, this.testPlayerUuid, null, personalId, 200));
                Assertions.assertTrue(this.manager.hasConflict(personalId, globalId));
                Assertions.assertTrue(this.manager.startGlobalEvent(null, globalId, 200));
                Assertions.assertFalse(this.manager.isPlayerEventActive(this.testPlayerUuid, personalId));
        }

        private ConfiguredDummyEvent registerConfiguredEvent(Identifier id, EventCategory primaryCategory,
                        Set<EventCategory> categories, boolean global, boolean exclusive) {
                ConfiguredDummyEvent event = new ConfiguredDummyEvent(id, primaryCategory, categories, global,
                                Set.of());
                this.manager.register(event);
                this.manager.buildConflictMatrix();
                return event;
        }

        private static class ConfiguredDummyEvent implements ChaosEvent {
                private final EventDefinition definition;
                private final EventCategory primaryCategory;
                private final boolean global;

                ConfiguredDummyEvent(Identifier id, EventCategory primaryCategory, Set<EventCategory> categories,
                                boolean global, Set<Identifier> conflictsWith) {
                        this.primaryCategory = primaryCategory;
                        this.global = global;
                        this.definition = new EventDefinition(id, categories,
                                        global ? EventScope.GLOBAL : EventScope.PLAYER, DurationPolicy.TIMED,
                                        conflictsWith, 600, 1,
                                        false);
                }

                @Override
                public EventDefinition getDefinition() {
                        return this.definition;
                }

                @Override
                public EventCategory getCategory() {
                        return this.primaryCategory;
                }

                @Override
                public boolean isGlobal() {
                        return this.global;
                }

                @Override
                public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
                                EventContext context, int durationTicks) {
                }

                @Override
                public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
                                EventContext context, int remainingTicks) {
                }

                @Override
                public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
                                EventContext context) {
                }
        }

        private static class DummyEvent implements ChaosEvent {
                private final Identifier id;
                private final EventCategory category;
                private final boolean global;
                private final Set<Identifier> conflictsWith;
                boolean stopped = false;

                DummyEvent(Identifier id, EventCategory category, boolean global) {
                        this(id, category, global, Set.of());
                }

                DummyEvent(Identifier id, EventCategory category, boolean global, Set<Identifier> conflictsWith) {
                        this.id = id;
                        this.category = category;
                        this.global = global;
                        this.conflictsWith = conflictsWith;
                }

                @Override
                public EventDefinition getDefinition() {
                        return EventDefinition.withConflicts(this.id, this.category,
                                        this.global ? EventScope.GLOBAL : EventScope.PLAYER, this.conflictsWith);
                }

                @Override
                public Identifier getId() {
                        return this.id;
                }

                @Override
                public EventCategory getCategory() {
                        return this.category;
                }

                @Override
                public boolean isGlobal() {
                        return this.global;
                }

                @Override
                public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
                                EventContext context, int durationTicks) {
                }

                @Override
                public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
                                EventContext context, int remainingTicks) {
                }

                @Override
                public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
                                EventContext context) {
                        this.stopped = true;
                }
        }
}
