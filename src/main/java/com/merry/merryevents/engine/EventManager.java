package com.merry.merryevents.engine;

import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.DurationPolicy;
import com.merry.merryevents.api.EventDefinition;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.network.EventSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative lifecycle coordinator and fault-isolated dispatcher for chaos
 * events.
 * Enforces cross-stream single-source-of-truth (SSOT) conflict checks, global
 * preemption,
 * clean start/resume separation, dormant offline state preservation, and safe
 * synchronization.
 */
public final class EventManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("merryevents");
    private static final EventManager INSTANCE = new EventManager();

    private final Map<Identifier, ChaosEvent> registry = new ConcurrentHashMap<>();
    private final Map<Identifier, ActiveEventInstance> activeGlobalEvents = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Identifier, ActiveEventInstance>> activePlayerEvents = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Identifier, ActiveEventInstance>> dormantPlayerEvents = new ConcurrentHashMap<>();

    private final List<Identifier> globalRemovalQueue = new ArrayList<>();
    private final List<Identifier> playerRemovalQueue = new ArrayList<>();

    private final CategoryOccupancyTracker categoryTracker = new CategoryOccupancyTracker();
    private EventPersistentState persistentState;
    private int fallbackStartSequence = 1;

    private EventManager() {
    }

    public static EventManager getInstance() {
        return EventManager.INSTANCE;
    }

    private int nextStartSequence() {
        if (this.persistentState != null) {
            return this.persistentState.incrementStartSequence();
        }
        return this.fallbackStartSequence++;
    }

    /**
     * Discovers and registers all bundled events via standard Java ServiceLoader.
     */
    public void registerDefaultEvents() {
        ServiceLoader<ChaosEvent> loader = ServiceLoader.load(ChaosEvent.class, EventManager.class.getClassLoader());
        for (ChaosEvent event : loader) {
            this.register(event);
        }
        EventManager.LOGGER.info("[EventManager] Loaded {} chaos events via ServiceLoader.", this.registry.size());
    }

    public void register(ChaosEvent event) {
        this.registry.put(event.getId(), event);
        try {
            event.registerHooks();
        } catch (Throwable t) {
            EventManager.LOGGER.error("[EventManager] Exception registering hooks for event '{}'", event.getId(), t);
        }
        EventManager.LOGGER.info("[EventManager] Registered chaos event '{}' in category {}", event.getId(),
                event.getCategory());
    }

    public Map<Identifier, ChaosEvent> getRegistry() {
        return Collections.unmodifiableMap(this.registry);
    }

    public @Nullable ChaosEvent getEvent(Identifier id) {
        return this.registry.get(id);
    }

    public Set<Identifier> getRegisteredEventIds() {
        return this.registry.keySet();
    }

    public Set<Identifier> getActiveGlobalEventIds() {
        return this.activeGlobalEvents.keySet();
    }

    public Map<Identifier, ActiveEventInstance> getActiveGlobalEvents() {
        return Collections.unmodifiableMap(this.activeGlobalEvents);
    }

    public Map<UUID, Map<Identifier, ActiveEventInstance>> getActivePlayerEvents() {
        return Collections.unmodifiableMap(this.activePlayerEvents);
    }

    public Map<UUID, Map<Identifier, ActiveEventInstance>> getDormantPlayerEvents() {
        return Collections.unmodifiableMap(this.dormantPlayerEvents);
    }

    public int getGlobalCategoryMask() {
        int mask = 0;
        for (ActiveEventInstance instance : this.activeGlobalEvents.values()) {
            mask |= instance.event.getCategory().getMask();
        }
        return mask;
    }

    public int getPlayerCategoryMask(UUID uuid) {
        Map<Identifier, ActiveEventInstance> map = this.activePlayerEvents.get(uuid);
        if (map == null || map.isEmpty()) {
            return 0;
        }
        int mask = 0;
        for (ActiveEventInstance instance : map.values()) {
            mask |= instance.event.getCategory().getMask();
        }
        return mask;
    }

    public boolean isCategoryOccupiedGlobally(EventCategory category) {
        return this.categoryTracker.isCategoryOccupied(null, category);
    }

    public boolean isCategoryOccupiedForPlayer(UUID uuid, EventCategory category) {
        return this.categoryTracker.isCategoryOccupied(uuid, category);
    }

    /**
     * Checks whether a global event can claim all of its declared categories.
     *
     * @param definition Event category declaration
     * @return True if the event's category concurrency rules permit it to start
     */
    public boolean hasCategoryCapacityForGlobalEvent(EventDefinition definition) {
        return this.categoryTracker.canStartGlobal(definition);
    }

    /**
     * Checks whether a personal event can claim all of its declared categories for
     * the specified player.
     *
     * @param playerUuid Target player UUID
     * @param definition Event category declaration
     * @return True if the event's category concurrency rules permit it to start
     */
    public boolean hasCategoryCapacityForPlayerEvent(UUID playerUuid, EventDefinition definition) {
        return this.categoryTracker.canStartPlayer(playerUuid, definition);
    }

    public boolean isGlobalEventActive(Identifier eventId) {
        return this.activeGlobalEvents.containsKey(eventId);
    }

    public boolean isPlayerEventActive(UUID playerUuid, Identifier eventId) {
        Map<Identifier, ActiveEventInstance> map = this.activePlayerEvents.get(playerUuid);
        return map != null && map.containsKey(eventId);
    }

    public @Nullable EventContext getPlayerEventContext(UUID playerUuid, Identifier eventId) {
        Map<Identifier, ActiveEventInstance> map = this.activePlayerEvents.get(playerUuid);
        if (map != null) {
            ActiveEventInstance inst = map.get(eventId);
            if (inst != null) {
                return inst.context;
            }
        }
        return null;
    }

    public void initPersistentState(MinecraftServer server, EventPersistentState state) {
        this.persistentState = state;
        ConfigManager.syncRegisteredEvents(this.registry);
        this.categoryTracker.clearAll();

        this.activeGlobalEvents.clear();
        for (Map.Entry<Identifier, EventPersistentState.SavedEventData> entry : state.globalEvents.entrySet()) {
            ChaosEvent event = this.registry.get(entry.getKey());
            if (event != null && event.isGlobal()) {
                int seq = this.nextStartSequence();
                ActiveEventInstance instance = new ActiveEventInstance(event, entry.getValue().remainingTicks(), seq);
                instance.context.fromNbt(entry.getValue().contextNbt());
                this.activeGlobalEvents.put(entry.getKey(), instance);
                this.categoryTracker.acquireGlobal(event.getDefinition());

                try {
                    instance.event.onResumeServer(server, null, instance.context);
                    EventManager.LOGGER.info("[EventManager] Resumed global event '{}' ({} ticks remaining)",
                            entry.getKey(), instance.remainingTicks);
                } catch (Throwable t) {
                    EventManager.LOGGER.error("[EventManager] Failed to resume global event '{}'. Discarding.",
                            entry.getKey(), t);
                    this.emergencyStopGlobal(server, entry.getKey(), instance);
                }
            }
        }

        this.activePlayerEvents.clear();
        this.dormantPlayerEvents.clear();

        for (Map.Entry<UUID, Map<Identifier, EventPersistentState.SavedEventData>> pEntry : state.playerEvents
                .entrySet()) {
            UUID uuid = pEntry.getKey();
            Map<Identifier, ActiveEventInstance> map = new ConcurrentHashMap<>();

            for (Map.Entry<Identifier, EventPersistentState.SavedEventData> eEntry : pEntry.getValue().entrySet()) {
                ChaosEvent event = this.registry.get(eEntry.getKey());
                if (event != null && !event.isGlobal()) {
                    int seq = this.nextStartSequence();
                    ActiveEventInstance instance = new ActiveEventInstance(event, eEntry.getValue().remainingTicks(),
                            seq);
                    instance.context.fromNbt(eEntry.getValue().contextNbt());
                    map.put(eEntry.getKey(), instance);
                }
            }

            if (!map.isEmpty()) {
                this.dormantPlayerEvents.put(uuid, map);
            }
        }

        ChaosDirector director = ChaosDirector.getInstance();
        if (state.globalCountdown > 0) {
            director.setGlobalCountdown(state.globalCountdown);
        } else {
            director.initializeGlobalCountdown();
        }

        if (!state.playerCountdowns.isEmpty()) {
            director.restorePlayerCountdowns(state.playerCountdowns);
        }

        EventCategory restoredCategory = null;
        if (state.lastGlobalCategory != null) {
            try {
                restoredCategory = EventCategory.valueOf(state.lastGlobalCategory);
            } catch (IllegalArgumentException ignored) {
            }
        }
        director.restoreHistories(state.lastGlobalEventId, restoredCategory, state.playerHistories);

        state.markDirty();
    }

    public boolean startGlobalEvent(MinecraftServer server, Identifier eventId, int durationTicks) {
        ChaosEvent event = this.registry.get(eventId);
        if (event == null || !event.isGlobal()) {
            EventManager.LOGGER.warn("[EventManager] Invalid global event target: {}", eventId);
            return false;
        }

        if (!this.hasCategoryCapacityForGlobalEvent(event.getDefinition())) {
            EventManager.LOGGER.warn("[EventManager] Conflict: categories for global event '{}' currently occupied",
                    eventId);
            return false;
        }

        if (!event.canStart(server, null)) {
            EventManager.LOGGER.warn("[EventManager] Pre-flight check rejected global event '{}'", eventId);
            return false;
        }

        for (EventCategory cat : event.getCategories()) {
            this.preemptConflictingPlayerEvents(server, cat);
        }

        int sequence = this.nextStartSequence();
        ActiveEventInstance instance = new ActiveEventInstance(event, durationTicks, sequence);
        this.activeGlobalEvents.put(eventId, instance);
        this.categoryTracker.acquireGlobal(event.getDefinition());

        try {
            event.onStartServer(server, null, instance.context, durationTicks);
        } catch (Throwable t) {
            EventManager.LOGGER.error("[EventManager] Exception during onStartServer for global event '{}'", eventId,
                    t);
            this.emergencyStopGlobal(server, eventId, instance);
            return false;
        }

        if (this.persistentState != null) {
            this.persistentState.markDirty();
        }

        if (server != null) {
            EventSyncPayload payload = new EventSyncPayload(eventId, event.getCategory().name(), durationTicks, true,
                    true, sequence);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (ServerPlayNetworking.canSend(player, payload.getId())) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }

        EventManager.LOGGER.info("[EventManager] Started global event '{}' ({} ticks, seq={})", eventId, durationTicks,
                sequence);
        return true;
    }

    private void preemptConflictingPlayerEvents(@Nullable MinecraftServer server, EventCategory category) {
        for (Map.Entry<UUID, Map<Identifier, ActiveEventInstance>> pEntry : this.activePlayerEvents.entrySet()) {
            UUID playerUuid = pEntry.getKey();
            List<Identifier> toStop = new ArrayList<>();

            for (Map.Entry<Identifier, ActiveEventInstance> eEntry : pEntry.getValue().entrySet()) {
                if (eEntry.getValue().event.getCategories().contains(category)) {
                    toStop.add(eEntry.getKey());
                }
            }

            for (int i = 0; i < toStop.size(); i++) {
                Identifier id = toStop.get(i);
                EventManager.LOGGER.info(
                        "[EventManager] Preempting personal event '{}' for player {} due to incoming global category {}",
                        id, playerUuid, category);
                this.stopPlayerEventByUuid(server, playerUuid, id);
            }
        }
    }

    public boolean startPlayerEvent(MinecraftServer server, ServerPlayerEntity player, Identifier eventId,
            int durationTicks) {
        return this.startPlayerEvent(server, player.getUuid(), player, eventId, durationTicks);
    }

    public boolean startPlayerEvent(@Nullable MinecraftServer server, UUID uuid, @Nullable ServerPlayerEntity player,
            Identifier eventId, int durationTicks) {
        ChaosEvent event = this.registry.get(eventId);
        if (event == null || event.isGlobal()) {
            EventManager.LOGGER.warn("[EventManager] Invalid player event target: {}", eventId);
            return false;
        }

        if (!this.hasCategoryCapacityForPlayerEvent(uuid, event.getDefinition())) {
            String name = player != null ? player.getName().getString() : uuid.toString();
            EventManager.LOGGER.warn("[EventManager] Conflict: categories for event '{}' active for {}", eventId, name);
            return false;
        }

        if (!event.canStart(server, player)) {
            String name = player != null ? player.getName().getString() : uuid.toString();
            EventManager.LOGGER.warn("[EventManager] Pre-flight check rejected event '{}' on {}", eventId, name);
            return false;
        }

        int sequence = this.nextStartSequence();
        ActiveEventInstance instance = new ActiveEventInstance(event, durationTicks, sequence);
        this.activePlayerEvents.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(eventId, instance);
        this.categoryTracker.acquirePlayer(uuid, event.getDefinition());

        try {
            event.onStartServer(server, player, instance.context, durationTicks);
        } catch (Throwable t) {
            String name = player != null ? player.getName().getString() : uuid.toString();
            EventManager.LOGGER.error("[EventManager] Exception during onStartServer for event '{}' on {}",
                    eventId, name, t);
            if (player != null) {
                this.emergencyStopPlayer(server, player, eventId, instance);
            } else {
                this.stopPlayerEventByUuid(server, uuid, eventId);
            }
            return false;
        }

        if (this.persistentState != null) {
            this.persistentState.markDirty();
        }

        if (server != null && player != null) {
            EventSyncPayload payload = new EventSyncPayload(eventId, event.getCategory().name(), durationTicks, true,
                    false, sequence);
            if (ServerPlayNetworking.canSend(player, payload.getId())) {
                ServerPlayNetworking.send(player, payload);
            }
        }

        String name = player != null ? player.getName().getString() : uuid.toString();
        EventManager.LOGGER.info("[EventManager] Started event '{}' for {} ({} ticks, seq={})", eventId, name,
                durationTicks, sequence);
        return true;
    }

    public boolean stopGlobalEvent(MinecraftServer server, Identifier eventId) {
        ActiveEventInstance instance = this.activeGlobalEvents.remove(eventId);
        if (instance != null) {
            this.categoryTracker.releaseGlobal(instance.event.getDefinition());
            try {
                instance.event.onStopServer(server, null, instance.context);
            } catch (Throwable t) {
                EventManager.LOGGER.error("[EventManager] Exception during onStopServer for global event '{}'", eventId,
                        t);
            } finally {
                if (server != null) {
                    instance.context.discardTrackedEntities(server);
                }
                instance.context.clear();
            }

            if (this.persistentState != null) {
                this.persistentState.markDirty();
            }

            if (server != null) {
                EventSyncPayload payload = new EventSyncPayload(eventId, instance.event.getCategory().name(), 0, false,
                        true, instance.getStartSequence());
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (ServerPlayNetworking.canSend(player, payload.getId())) {
                        ServerPlayNetworking.send(player, payload);
                    }
                }
            }

            EventManager.LOGGER.info("[EventManager] Global event '{}' terminated", eventId);
            return true;
        }
        return false;
    }

    public boolean stopPlayerEvent(MinecraftServer server, ServerPlayerEntity player, Identifier eventId) {
        return this.stopPlayerEventByUuid(server, player.getUuid(), eventId);
    }

    public boolean stopPlayerEventByUuid(@Nullable MinecraftServer server, UUID playerUuid, Identifier eventId) {
        Map<Identifier, ActiveEventInstance> events = this.activePlayerEvents.get(playerUuid);
        if (events != null) {
            ActiveEventInstance instance = events.remove(eventId);
            if (instance != null) {
                this.categoryTracker.releasePlayer(playerUuid, instance.event.getDefinition());
                ServerPlayerEntity player = server != null ? server.getPlayerManager().getPlayer(playerUuid) : null;
                try {
                    instance.event.onStopServer(server, player, instance.context);
                } catch (Throwable t) {
                    EventManager.LOGGER.error("[EventManager] Exception during onStopServer for event '{}' on {}",
                            eventId, playerUuid, t);
                } finally {
                    if (server != null) {
                        instance.context.discardTrackedEntities(server);
                    }
                    instance.context.clear();
                }

                if (events.isEmpty()) {
                    this.activePlayerEvents.remove(playerUuid);
                }

                if (this.persistentState != null) {
                    this.persistentState.markDirty();
                }

                if (server != null && player != null) {
                    EventSyncPayload payload = new EventSyncPayload(eventId, instance.event.getCategory().name(), 0,
                            false, false, instance.getStartSequence());
                    if (ServerPlayNetworking.canSend(player, payload.getId())) {
                        ServerPlayNetworking.send(player, payload);
                    }
                }

                EventManager.LOGGER.info("[EventManager] Player event '{}' terminated for {}", eventId, playerUuid);
                return true;
            }
        }
        return false;
    }

    private void emergencyStopGlobal(MinecraftServer server, Identifier eventId, ActiveEventInstance instance) {
        this.activeGlobalEvents.remove(eventId);
        this.categoryTracker.releaseGlobal(instance.event.getDefinition());
        try {
            instance.event.onStopServer(server, null, instance.context);
        } catch (Throwable ignored) {
        } finally {
            if (server != null) {
                instance.context.discardTrackedEntities(server);
            }
            instance.context.clear();
        }
    }

    private void emergencyStopPlayer(MinecraftServer server, ServerPlayerEntity player, Identifier eventId,
            ActiveEventInstance instance) {
        UUID uuid = player.getUuid();
        Map<Identifier, ActiveEventInstance> events = this.activePlayerEvents.get(uuid);
        if (events != null) {
            events.remove(eventId);
            if (events.isEmpty()) {
                this.activePlayerEvents.remove(uuid);
            }
        }
        this.categoryTracker.releasePlayer(uuid, instance.event.getDefinition());
        try {
            instance.event.onStopServer(server, player, instance.context);
        } catch (Throwable ignored) {
        } finally {
            if (server != null) {
                instance.context.discardTrackedEntities(server);
            }
            instance.context.clear();
        }
    }

    public void onServerStopping(MinecraftServer server) {
        if (this.persistentState != null) {
            this.persistentState.markDirty();
        }

        for (ActiveEventInstance instance : this.activeGlobalEvents.values()) {
            try {
                instance.event.onStopServer(server, null, instance.context);
            } catch (Throwable t) {
                EventManager.LOGGER.error("[EventManager] Exception stopping global event on shutdown", t);
            } finally {
                instance.context.discardTrackedEntities(server);
            }
        }

        for (Map.Entry<UUID, Map<Identifier, ActiveEventInstance>> entry : this.activePlayerEvents.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            for (ActiveEventInstance instance : entry.getValue().values()) {
                try {
                    instance.event.onStopServer(server, player, instance.context);
                } catch (Throwable t) {
                    EventManager.LOGGER.error("[EventManager] Exception stopping player event on shutdown", t);
                } finally {
                    instance.context.discardTrackedEntities(server);
                }
            }
        }

        EventManager.LOGGER
                .info("[EventManager] Preserved active events state into persistent storage before shutdown.");
    }

    public void resetRuntimeState() {
        this.registry.clear();
        this.activeGlobalEvents.clear();
        this.activePlayerEvents.clear();
        this.dormantPlayerEvents.clear();
        this.globalRemovalQueue.clear();
        this.playerRemovalQueue.clear();
        this.categoryTracker.clearAll();
        this.persistentState = null;
        this.fallbackStartSequence = 1;
    }

    public void syncAllToPlayer(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();

        for (Map.Entry<Identifier, ActiveEventInstance> entry : this.activeGlobalEvents.entrySet()) {
            ActiveEventInstance inst = entry.getValue();
            EventSyncPayload payload = new EventSyncPayload(entry.getKey(), inst.event.getCategory().name(),
                    inst.remainingTicks, true, true, inst.getStartSequence());
            if (ServerPlayNetworking.canSend(player, payload.getId())) {
                ServerPlayNetworking.send(player, payload);
            }
            if (server != null) {
                inst.event.onSyncToPlayer(server, player, null, inst.context);
            }
        }

        Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.get(player.getUuid());
        if (pEvents != null) {
            for (Map.Entry<Identifier, ActiveEventInstance> entry : pEvents.entrySet()) {
                ActiveEventInstance inst = entry.getValue();
                EventSyncPayload payload = new EventSyncPayload(entry.getKey(), inst.event.getCategory().name(),
                        inst.remainingTicks, true, false, inst.getStartSequence());
                if (ServerPlayNetworking.canSend(player, payload.getId())) {
                    ServerPlayNetworking.send(player, payload);
                }
                if (server != null) {
                    inst.event.onSyncToPlayer(server, player, player, inst.context);
                }
            }
        }

        if (server != null) {
            for (Map.Entry<UUID, Map<Identifier, ActiveEventInstance>> pEntry : this.activePlayerEvents.entrySet()) {
                if (pEntry.getKey().equals(player.getUuid())) {
                    continue;
                }
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pEntry.getKey());
                for (ActiveEventInstance inst : pEntry.getValue().values()) {
                    inst.event.onSyncToPlayer(server, player, owner, inst.context);
                }
            }
        }
    }

    public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        ChaosDirector.getInstance().onPlayerJoin(player.getUuid());
        if (this.persistentState != null) {
            this.persistentState.markPlayerSeen(player.getUuid());
        }

        Map<Identifier, ActiveEventInstance> dormant = this.dormantPlayerEvents.remove(player.getUuid());
        if (dormant != null && !dormant.isEmpty()) {
            for (Map.Entry<Identifier, ActiveEventInstance> entry : dormant.entrySet()) {
                ActiveEventInstance inst = entry.getValue();
                if (!this.categoryTracker.canStartPlayer(player.getUuid(), inst.event.getDefinition())) {
                    EventManager.LOGGER.info(
                            "[EventManager] Dormant event '{}' for {} discarded due to active category conflict",
                            entry.getKey(), player.getName().getString());
                    inst.context.clear();
                    continue;
                }

                this.activePlayerEvents.computeIfAbsent(player.getUuid(), k -> new ConcurrentHashMap<>())
                        .put(entry.getKey(), inst);
                this.categoryTracker.acquirePlayer(player.getUuid(), inst.event.getDefinition());

                try {
                    inst.event.onResumeServer(server, player, inst.context);
                    EventManager.LOGGER.info("[EventManager] Resumed dormant event '{}' for {}",
                            inst.event.getId(), player.getName().getString());
                } catch (Throwable t) {
                    EventManager.LOGGER.error("[EventManager] Exception resuming event '{}' on player join",
                            inst.event.getId(), t);
                }
            }
        }

        this.syncAllToPlayer(player);
    }

    public void onPlayerDisconnect(MinecraftServer server, UUID playerUuid) {
        this.onPlayerDisconnect(server, playerUuid, null);
    }

    public void onPlayerDisconnect(MinecraftServer server, ServerPlayerEntity player) {
        if (player != null) {
            this.onPlayerDisconnect(server, player.getUuid(), player);
        }
    }

    public void onPlayerDisconnect(MinecraftServer server, UUID playerUuid, @Nullable ServerPlayerEntity player) {
        this.dormantPlayerEvents.remove(playerUuid);
        Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.remove(playerUuid);
        if (pEvents != null) {
            for (ActiveEventInstance instance : pEvents.values()) {
                this.categoryTracker.releasePlayer(playerUuid, instance.event.getDefinition());
                try {
                    instance.event.onStopServer(server, player, instance.context);
                } catch (Throwable t) {
                    EventManager.LOGGER.error("[EventManager] Exception stopping event '{}' on player disconnect",
                            instance.event.getId(), t);
                } finally {
                    if (server != null) {
                        instance.context.discardTrackedEntities(server);
                    }
                    instance.context.clear();
                }
            }
        }
        this.categoryTracker.clearPlayer(playerUuid);
        if (this.persistentState != null) {
            this.persistentState.markDirty();
        }
    }

    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        UUID uuid = newPlayer.getUuid();
        Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.get(uuid);
        if (pEvents != null) {
            for (ActiveEventInstance inst : pEvents.values()) {
                try {
                    inst.event.onPlayerRespawn(server, oldPlayer, newPlayer, inst.context);
                } catch (Throwable t) {
                    EventManager.LOGGER.error("[EventManager] Exception during onPlayerRespawn for event '{}'",
                            inst.event.getId(), t);
                }
            }
            this.syncAllToPlayer(newPlayer);
        }
    }

    public void tick(MinecraftServer server) {
        ChaosDirector.getInstance().tick(server);

        this.globalRemovalQueue.clear();
        for (Map.Entry<Identifier, ActiveEventInstance> entry : this.activeGlobalEvents.entrySet()) {
            ActiveEventInstance instance = entry.getValue();
            try {
                instance.event.onTickServer(server, null, instance.context, instance.remainingTicks);
            } catch (Throwable t) {
                EventManager.LOGGER.error("[EventManager] Fault in global event tick: '{}'. Emergency stop triggered.",
                        entry.getKey(), t);
                this.globalRemovalQueue.add(entry.getKey());
                continue;
            }

            DurationPolicy policy = instance.event.getDefinition().durationPolicy();
            boolean terminate = instance.context.isStopRequested();

            if (policy == DurationPolicy.INSTANT) {
                terminate = true;
            } else if (policy == DurationPolicy.TIMED) {
                if (--instance.remainingTicks <= 0) {
                    terminate = true;
                }
            }

            if (terminate) {
                this.globalRemovalQueue.add(entry.getKey());
            }
        }

        for (int i = 0; i < this.globalRemovalQueue.size(); i++) {
            this.stopGlobalEvent(server, this.globalRemovalQueue.get(i));
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.get(player.getUuid());
            if (pEvents == null || pEvents.isEmpty()) {
                continue;
            }

            this.playerRemovalQueue.clear();
            for (Map.Entry<Identifier, ActiveEventInstance> entry : pEvents.entrySet()) {
                ActiveEventInstance instance = entry.getValue();
                try {
                    instance.event.onTickServer(server, player, instance.context, instance.remainingTicks);
                } catch (Throwable t) {
                    EventManager.LOGGER.error(
                            "[EventManager] Fault in player event tick: '{}' on {}. Emergency stop triggered.",
                            entry.getKey(), player.getName().getString(), t);
                    this.playerRemovalQueue.add(entry.getKey());
                    continue;
                }

                DurationPolicy policy = instance.event.getDefinition().durationPolicy();
                boolean terminate = instance.context.isStopRequested();

                if (policy == DurationPolicy.INSTANT) {
                    terminate = true;
                } else if (policy == DurationPolicy.TIMED) {
                    if (--instance.remainingTicks <= 0) {
                        terminate = true;
                    }
                }

                if (terminate) {
                    this.playerRemovalQueue.add(entry.getKey());
                }
            }

            for (int i = 0; i < this.playerRemovalQueue.size(); i++) {
                this.stopPlayerEvent(server, player, this.playerRemovalQueue.get(i));
            }
        }

        if (server.getTicks() % 100 == 0 && this.persistentState != null
                && (!this.activeGlobalEvents.isEmpty() || !this.activePlayerEvents.isEmpty()
                        || !this.dormantPlayerEvents.isEmpty())) {
            this.persistentState.markDirty();
        }
    }

    public static final class ActiveEventInstance {
        private final ChaosEvent event;
        private final EventContext context;
        private final int startSequence;
        int remainingTicks;

        public ActiveEventInstance(ChaosEvent event, int remainingTicks, int startSequence) {
            this.event = event;
            this.context = new EventContext();
            this.remainingTicks = remainingTicks;
            this.startSequence = startSequence;
        }

        public ChaosEvent getEvent() {
            return this.event;
        }

        public EventContext getContext() {
            return this.context;
        }

        public int getRemainingTicks() {
            return this.remainingTicks;
        }

        public int getStartSequence() {
            return this.startSequence;
        }
    }
}
