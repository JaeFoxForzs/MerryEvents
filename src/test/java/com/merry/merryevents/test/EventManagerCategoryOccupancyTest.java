package com.merry.merryevents.test;

import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        this.globalVisionEvent = new DummyEvent(this.testGlobalId, EventCategory.VISION, true);
        this.personalVisionEvent = new DummyEvent(this.testPersonalId, EventCategory.VISION, false);

        this.manager.register(this.globalVisionEvent);
        this.manager.register(this.personalVisionEvent);
    }

    @Test
    @DisplayName("Verify global preemption and player category isolation semantics")
    public void testCategoryOccupancyAndGlobalPreemption() {
        // 1. Initial state: category VISION is completely free
        Assertions.assertFalse(this.manager.isCategoryOccupiedGlobally(EventCategory.VISION));
        Assertions.assertFalse(this.manager.isCategoryOccupiedForPlayer(this.testPlayerUuid, EventCategory.VISION));

        // 2. Start personal event on Player 1 via safe UUID overload (no
        // ServerPlayerEntity mock needed)
        boolean startedPersonal = this.manager.startPlayerEvent(null, this.testPlayerUuid, null, this.testPersonalId,
                200);
        Assertions.assertTrue(startedPersonal, "Personal event must start successfully for Player 1");

        Assertions.assertTrue(this.manager.isPlayerEventActive(this.testPlayerUuid, this.testPersonalId));
        Assertions.assertTrue(this.manager.isCategoryOccupiedForPlayer(this.testPlayerUuid, EventCategory.VISION));

        // Player 2 is NOT blocked by Player 1's personal event
        Assertions.assertFalse(this.manager.isCategoryOccupiedForPlayer(this.otherPlayerUuid, EventCategory.VISION));

        // Global category is NOT blocked by personal events; Global Preemption will
        // override it
        Assertions.assertFalse(this.manager.isCategoryOccupiedGlobally(EventCategory.VISION));

        // 3. Ignite Global event in the same category -> must PREEMPT Player 1's
        // personal event
        boolean startedGlobal = this.manager.startGlobalEvent(null, this.testGlobalId, 300);
        Assertions.assertTrue(startedGlobal,
                "Global event must successfully start and preempt conflicting personal events");

        // Player 1's personal event must now be terminated
        Assertions.assertFalse(this.manager.isPlayerEventActive(this.testPlayerUuid, this.testPersonalId),
                "Personal event on Player 1 must be preempted and removed");
        Assertions.assertTrue(this.personalVisionEvent.stopped,
                "Preempted personal event must receive onStopServer callback");

        // Global event is now actively occupying category VISION
        Assertions.assertTrue(this.manager.isGlobalEventActive(this.testGlobalId));
        Assertions.assertTrue(this.manager.isCategoryOccupiedGlobally(EventCategory.VISION));

        // Now both players are blocked from starting new personal events in category
        // VISION while global is running
        Assertions.assertTrue(this.manager.isCategoryOccupiedForPlayer(this.testPlayerUuid, EventCategory.VISION));
        Assertions.assertTrue(this.manager.isCategoryOccupiedForPlayer(this.otherPlayerUuid, EventCategory.VISION));
    }

    private static class DummyEvent implements ChaosEvent {
        private final Identifier id;
        private final EventCategory category;
        private final boolean global;
        boolean stopped = false;

        DummyEvent(Identifier id, EventCategory category, boolean global) {
            this.id = id;
            this.category = category;
            this.global = global;
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
                EventContext context) {
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