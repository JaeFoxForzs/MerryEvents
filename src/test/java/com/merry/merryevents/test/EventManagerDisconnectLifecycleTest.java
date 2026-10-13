package com.merry.merryevents.test;

import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.engine.EventPersistentState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

public class EventManagerDisconnectLifecycleTest {

    private static final Identifier DUMMY_CAMERA_ID = Identifier.of("merryevents", "dummy_camera");
    private static final Identifier DUMMY_GLOBAL_CAMERA_ID = Identifier.of("merryevents", "dummy_global_camera");

    private EventManager eventManager;
    private DummyCameraEvent personalEvent;
    private DummyGlobalCameraEvent globalEvent;

    @BeforeEach
    void setUp() {
        this.eventManager = EventManager.getInstance();
        this.eventManager.resetRuntimeState();

        this.personalEvent = new DummyCameraEvent(EventManagerDisconnectLifecycleTest.DUMMY_CAMERA_ID);
        this.globalEvent = new DummyGlobalCameraEvent(EventManagerDisconnectLifecycleTest.DUMMY_GLOBAL_CAMERA_ID);

        this.eventManager.register(this.personalEvent);
        this.eventManager.register(this.globalEvent);
    }

    @Test
    @DisplayName("Disconnecting a player must release category locks globally and personally")
    void testDisconnectReleasesCategoryOccupancy() {
        UUID playerUuid = UUID.randomUUID();

        // 1. Start active personal event directly via UUID overload
        boolean started = this.eventManager.startPlayerEvent(null, playerUuid, null,
                EventManagerDisconnectLifecycleTest.DUMMY_CAMERA_ID, 200);
        Assertions.assertTrue(started, "Personal event must start successfully for player");

        Assertions.assertTrue(
                this.eventManager.isPlayerEventActive(playerUuid, EventManagerDisconnectLifecycleTest.DUMMY_CAMERA_ID),
                "Personal event must be active for player");
        Assertions.assertTrue(this.eventManager.getActivePlayerEvents().containsKey(playerUuid),
                "The active player map must include the player");

        // 2. Simulate player disconnect via UUID overload
        this.eventManager.onPlayerDisconnect(null, playerUuid);

        // 3. Verify category is freed and activePlayerEvents map does not retain player
        Assertions.assertFalse(
                this.eventManager.isPlayerEventActive(playerUuid, EventManagerDisconnectLifecycleTest.DUMMY_CAMERA_ID),
                "Personal event must be removed from player upon disconnect");
        Assertions.assertFalse(this.eventManager.getActivePlayerEvents().containsKey(playerUuid),
                "Disconnect must remove the player's active event map");
        Assertions.assertTrue(this.personalEvent.stopped,
                "onStopServer must be invoked during player disconnect cleanup");
    }

    @Test
    @DisplayName("Dormant events loaded from persistence must not occupy active categories while player is offline")
    void testDormantEventsDoNotBlockCategories() {
        UUID offlinePlayer = UUID.randomUUID();

        EventPersistentState state = new EventPersistentState();
        state.playerEvents.computeIfAbsent(offlinePlayer, k -> new HashMap<>())
                .put(EventManagerDisconnectLifecycleTest.DUMMY_CAMERA_ID, new EventPersistentState.SavedEventData(
                        EventManagerDisconnectLifecycleTest.DUMMY_CAMERA_ID, 200, new NbtCompound()));
        this.eventManager.initPersistentState(null, state);

        // Dormant event is NOT in activePlayerEvents
        Assertions.assertFalse(
                this.eventManager.isPlayerEventActive(offlinePlayer,
                        EventManagerDisconnectLifecycleTest.DUMMY_CAMERA_ID),
                "Dormant event must not be marked as active in operational map");
        Assertions.assertFalse(this.eventManager.getActivePlayerEvents().containsKey(offlinePlayer),
                "Dormant events must not be active until the player rejoins");
    }

    private static class DummyCameraEvent implements ChaosEvent {
        private final Identifier id;
        boolean stopped = false;

        DummyCameraEvent(Identifier id) {
            this.id = id;
        }

        @Override
        public Identifier getId() {
            return this.id;
        }

        @Override
        public EventCategory getCategory() {
            return EventCategory.CAMERA;
        }

        @Override
        public boolean isGlobal() {
            return false;
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

    private static class DummyGlobalCameraEvent implements ChaosEvent {
        private final Identifier id;

        DummyGlobalCameraEvent(Identifier id) {
            this.id = id;
        }

        @Override
        public Identifier getId() {
            return this.id;
        }

        @Override
        public EventCategory getCategory() {
            return EventCategory.CAMERA;
        }

        @Override
        public boolean isGlobal() {
            return true;
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
}