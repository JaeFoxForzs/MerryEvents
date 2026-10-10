package com.merry.merryevents.engine;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.event.inventory.DiamondRushEvent;
import com.merry.merryevents.event.mobs.ExplodingMobsEvent;
import com.merry.merryevents.event.mobs.PersonalKillerEvent;
import com.merry.merryevents.event.physics.MoonGravityEvent;
import com.merry.merryevents.event.sounds.ParanoiaSoundsEvent;
import com.merry.merryevents.event.status.DenseDarknessEvent;
import com.merry.merryevents.event.status.FragilityEvent;
import com.merry.merryevents.event.visual.GhostGuiEvent;
import com.merry.merryevents.event.weather.AcidRainEvent;
import com.merry.merryevents.event.weather.AnvilRainEvent;
import com.merry.merryevents.event.world.FalseWaterEvent;
import com.merry.merryevents.network.EventSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative lifecycle and conflict resolution engine for chaos events.
 * Enforces single source of truth (SSOT) state without independent redundant
 * category bitmasks.
 */
public final class EventManager {
    private static final EventManager INSTANCE = new EventManager();

    private final Map<Identifier, ChaosEvent> registry = new ConcurrentHashMap<>();
    private final Map<Identifier, ActiveEventInstance> activeGlobalEvents = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Identifier, ActiveEventInstance>> activePlayerEvents = new ConcurrentHashMap<>();

    private final List<Identifier> globalRemovalQueue = new ArrayList<>();
    private final List<Identifier> playerRemovalQueue = new ArrayList<>();

    private EventPersistentState persistentState;

    private EventManager() {
        register(new AcidRainEvent());
        register(new MoonGravityEvent());
        register(new FalseWaterEvent());
        register(new ExplodingMobsEvent());
        register(new FragilityEvent());
        register(new DiamondRushEvent());
        register(new GhostGuiEvent());
        register(new ParanoiaSoundsEvent());
        register(new PersonalKillerEvent());
        register(new AnvilRainEvent());
        register(new DenseDarknessEvent());
    }

    public static EventManager getInstance() {
        return INSTANCE;
    }

    public void register(ChaosEvent event) {
        registry.put(event.getId(), event);
        MerryEvents.LOGGER.info("[EventManager] Registered chaos event '{}' in category {}", event.getId(),
                event.getCategory());
    }

    public @Nullable ChaosEvent getEvent(Identifier id) {
        return registry.get(id);
    }

    public java.util.Set<Identifier> getRegisteredEventIds() {
        return registry.keySet();
    }

    public java.util.Set<Identifier> getActiveGlobalEventIds() {
        return activeGlobalEvents.keySet();
    }

    public Map<Identifier, ActiveEventInstance> getActiveGlobalEvents() {
        return Collections.unmodifiableMap(activeGlobalEvents);
    }

    public Map<UUID, Map<Identifier, ActiveEventInstance>> getActivePlayerEvents() {
        return Collections.unmodifiableMap(activePlayerEvents);
    }

    public int getGlobalCategoryMask() {
        int mask = 0;
        for (ActiveEventInstance instance : activeGlobalEvents.values()) {
            mask |= instance.event.getCategory().getMask();
        }
        return mask;
    }

    public int getPlayerCategoryMask(UUID uuid) {
        Map<Identifier, ActiveEventInstance> map = activePlayerEvents.get(uuid);
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
        return (getGlobalCategoryMask() & category.getMask()) != 0;
    }

    public boolean isCategoryOccupiedForPlayer(UUID uuid, EventCategory category) {
        return (getPlayerCategoryMask(uuid) & category.getMask()) != 0;
    }

    public boolean isGlobalEventActive(Identifier eventId) {
        return activeGlobalEvents.containsKey(eventId);
    }

    public boolean isPlayerEventActive(UUID playerUuid, Identifier eventId) {
        Map<Identifier, ActiveEventInstance> map = activePlayerEvents.get(playerUuid);
        return map != null && map.containsKey(eventId);
    }

    public @Nullable EventContext getPlayerEventContext(UUID playerUuid, Identifier eventId) {
        Map<Identifier, ActiveEventInstance> map = activePlayerEvents.get(playerUuid);
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
        ConfigManager.syncRegisteredEvents(registry);

        activeGlobalEvents.clear();

        for (Map.Entry<Identifier, EventPersistentState.SavedEventData> entry : state.globalEvents.entrySet()) {
            ChaosEvent event = registry.get(entry.getKey());
            if (event != null && event.isGlobal()) {
                ActiveEventInstance instance = new ActiveEventInstance(event, entry.getValue().remainingTicks());
                activeGlobalEvents.put(entry.getKey(), instance);
                instance.event.onStartServer(server, null, instance.context);
                MerryEvents.LOGGER.info("[EventManager] Resumed global event '{}' ({} ticks remaining)", entry.getKey(),
                        instance.remainingTicks);
            }
        }

        activePlayerEvents.clear();

        for (Map.Entry<UUID, Map<Identifier, EventPersistentState.SavedEventData>> pEntry : state.playerEvents
                .entrySet()) {
            UUID uuid = pEntry.getKey();
            Map<Identifier, ActiveEventInstance> map = new ConcurrentHashMap<>();

            for (Map.Entry<Identifier, EventPersistentState.SavedEventData> eEntry : pEntry.getValue().entrySet()) {
                ChaosEvent event = registry.get(eEntry.getKey());
                if (event != null && !event.isGlobal()) {
                    ActiveEventInstance instance = new ActiveEventInstance(event, eEntry.getValue().remainingTicks());
                    map.put(eEntry.getKey(), instance);
                }
            }

            if (!map.isEmpty()) {
                activePlayerEvents.put(uuid, map);
            }
        }

        ChaosDirector director = ChaosDirector.getInstance();
        if (state.directorCountdown > 0) {
            director.setCountdown(state.directorCountdown);
        } else {
            director.initializeCountdown();
        }

        if (!state.directorDeck.isEmpty()) {
            director.getDeck().clear();
            director.getDeck().addAll(state.directorDeck);
        } else {
            director.refillDeck();
        }

        state.markDirty();
    }

    public boolean startGlobalEvent(MinecraftServer server, Identifier eventId, int durationTicks) {
        ChaosEvent event = registry.get(eventId);
        if (event == null || !event.isGlobal()) {
            MerryEvents.LOGGER.warn("[EventManager] Invalid global event target: {}", eventId);
            return false;
        }

        if (isCategoryOccupiedGlobally(event.getCategory())) {
            MerryEvents.LOGGER.warn("[EventManager] Conflict: category {} already occupied globally",
                    event.getCategory());
            return false;
        }

        ActiveEventInstance instance = new ActiveEventInstance(event, durationTicks);
        activeGlobalEvents.put(eventId, instance);
        event.onStartServer(server, null, instance.context);

        if (persistentState != null) {
            persistentState.markDirty();
        }

        EventSyncPayload payload = new EventSyncPayload(eventId, event.getCategory().name(), durationTicks, true, true);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }

        MerryEvents.LOGGER.info("[EventManager] Started global event '{}' ({} ticks)", eventId, durationTicks);
        return true;
    }

    public boolean startPlayerEvent(MinecraftServer server, ServerPlayerEntity player, Identifier eventId,
            int durationTicks) {
        ChaosEvent event = registry.get(eventId);
        if (event == null || event.isGlobal()) {
            MerryEvents.LOGGER.warn("[EventManager] Invalid player event target: {}", eventId);
            return false;
        }

        UUID uuid = player.getUuid();
        if (isCategoryOccupiedForPlayer(uuid, event.getCategory())) {
            MerryEvents.LOGGER.warn("[EventManager] Conflict: category {} active for {}", event.getCategory(),
                    player.getName().getString());
            return false;
        }

        ActiveEventInstance instance = new ActiveEventInstance(event, durationTicks);
        activePlayerEvents.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(eventId, instance);
        event.onStartServer(server, player, instance.context);

        if (persistentState != null) {
            persistentState.markDirty();
        }

        EventSyncPayload payload = new EventSyncPayload(eventId, event.getCategory().name(), durationTicks, true,
                false);
        ServerPlayNetworking.send(player, payload);

        MerryEvents.LOGGER.info("[EventManager] Started event '{}' for {} ({} ticks)", eventId,
                player.getName().getString(), durationTicks);
        return true;
    }

    public void stopGlobalEvent(MinecraftServer server, Identifier eventId) {
        ActiveEventInstance instance = activeGlobalEvents.remove(eventId);
        if (instance != null) {
            instance.event.onStopServer(server, null, instance.context);
            instance.context.discardTrackedEntities(server);
            instance.context.clear();

            if (persistentState != null) {
                persistentState.markDirty();
            }

            EventSyncPayload payload = new EventSyncPayload(eventId, instance.event.getCategory().name(), 0, false,
                    true);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, payload);
            }

            MerryEvents.LOGGER.info("[EventManager] Global event '{}' terminated", eventId);
        }
    }

    public void stopPlayerEvent(MinecraftServer server, ServerPlayerEntity player, Identifier eventId) {
        UUID uuid = player.getUuid();
        Map<Identifier, ActiveEventInstance> events = activePlayerEvents.get(uuid);
        if (events != null) {
            ActiveEventInstance instance = events.remove(eventId);
            if (instance != null) {
                instance.event.onStopServer(server, player, instance.context);
                instance.context.discardTrackedEntities(server);
                instance.context.clear();

                if (events.isEmpty()) {
                    activePlayerEvents.remove(uuid);
                }

                if (persistentState != null) {
                    persistentState.markDirty();
                }

                EventSyncPayload payload = new EventSyncPayload(eventId, instance.event.getCategory().name(), 0, false,
                        false);
                ServerPlayNetworking.send(player, payload);

                MerryEvents.LOGGER.info("[EventManager] Player event '{}' terminated for {}", eventId,
                        player.getName().getString());
            }
        }
    }

    public void onServerStopping(MinecraftServer server) {
        if (persistentState != null) {
            persistentState.markDirty();
        }

        for (ActiveEventInstance instance : activeGlobalEvents.values()) {
            instance.event.onStopServer(server, null, instance.context);
            instance.context.discardTrackedEntities(server);
        }

        for (Map.Entry<UUID, Map<Identifier, ActiveEventInstance>> entry : activePlayerEvents.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            for (ActiveEventInstance instance : entry.getValue().values()) {
                instance.event.onStopServer(server, player, instance.context);
                instance.context.discardTrackedEntities(server);
            }
        }

        MerryEvents.LOGGER
                .info("[EventManager] Preserved active events state into persistent storage before shutdown.");
    }

    public void syncAllToPlayer(ServerPlayerEntity player) {
        for (Map.Entry<Identifier, ActiveEventInstance> entry : activeGlobalEvents.entrySet()) {
            ActiveEventInstance inst = entry.getValue();
            ServerPlayNetworking.send(player, new EventSyncPayload(entry.getKey(), inst.event.getCategory().name(),
                    inst.remainingTicks, true, true));
        }

        Map<Identifier, ActiveEventInstance> pEvents = activePlayerEvents.get(player.getUuid());
        if (pEvents != null) {
            for (Map.Entry<Identifier, ActiveEventInstance> entry : pEvents.entrySet()) {
                ActiveEventInstance inst = entry.getValue();
                ServerPlayNetworking.send(player, new EventSyncPayload(entry.getKey(), inst.event.getCategory().name(),
                        inst.remainingTicks, true, false));
            }
        }
    }

    public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        Map<Identifier, ActiveEventInstance> pEvents = activePlayerEvents.get(player.getUuid());
        if (pEvents != null) {
            for (ActiveEventInstance inst : pEvents.values()) {
                inst.event.onStartServer(server, player, inst.context);
            }
        }
        syncAllToPlayer(player);
    }

    public void onPlayerDisconnect(MinecraftServer server, ServerPlayerEntity player) {
        Map<Identifier, ActiveEventInstance> pEvents = activePlayerEvents.get(player.getUuid());
        if (pEvents != null) {
            for (ActiveEventInstance instance : pEvents.values()) {
                instance.event.onStopServer(server, player, instance.context);
                instance.context.discardTrackedEntities(server);
            }
        }
        if (persistentState != null) {
            persistentState.markDirty();
        }
    }

    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        UUID uuid = newPlayer.getUuid();
        Map<Identifier, ActiveEventInstance> pEvents = activePlayerEvents.get(uuid);
        if (pEvents != null) {
            for (ActiveEventInstance inst : pEvents.values()) {
                inst.event.onPlayerRespawn(server, oldPlayer, newPlayer, inst.context);
            }
            syncAllToPlayer(newPlayer);
        }
    }

    public void tick(MinecraftServer server) {
        ChaosDirector.getInstance().tick(server);

        globalRemovalQueue.clear();
        for (Map.Entry<Identifier, ActiveEventInstance> entry : activeGlobalEvents.entrySet()) {
            ActiveEventInstance instance = entry.getValue();
            instance.event.onTickServer(server, null, instance.context, instance.remainingTicks);

            if (--instance.remainingTicks <= 0) {
                globalRemovalQueue.add(entry.getKey());
            }
        }

        for (int i = 0; i < globalRemovalQueue.size(); i++) {
            stopGlobalEvent(server, globalRemovalQueue.get(i));
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Map<Identifier, ActiveEventInstance> pEvents = activePlayerEvents.get(player.getUuid());
            if (pEvents == null || pEvents.isEmpty()) {
                continue;
            }

            playerRemovalQueue.clear();
            for (Map.Entry<Identifier, ActiveEventInstance> entry : pEvents.entrySet()) {
                ActiveEventInstance instance = entry.getValue();
                instance.event.onTickServer(server, player, instance.context, instance.remainingTicks);

                if (--instance.remainingTicks <= 0) {
                    playerRemovalQueue.add(entry.getKey());
                }
            }

            for (int i = 0; i < playerRemovalQueue.size(); i++) {
                stopPlayerEvent(server, player, playerRemovalQueue.get(i));
            }
        }

        if (server.getTicks() % 100 == 0 && persistentState != null
                && (!activeGlobalEvents.isEmpty() || !activePlayerEvents.isEmpty())) {
            persistentState.markDirty();
        }
    }

    public static final class ActiveEventInstance {
        private final ChaosEvent event;
        private final EventContext context;
        int remainingTicks;

        ActiveEventInstance(ChaosEvent event, int remainingTicks) {
            this.event = event;
            this.context = new EventContext();
            this.remainingTicks = remainingTicks;
        }

        /**
         * Resolves the underlying chaos event definition.
         *
         * @return ChaosEvent instance
         */
        public ChaosEvent getEvent() {
            return event;
        }

        /**
         * Resolves the isolated lifecycle context of this running instance.
         *
         * @return EventContext instance
         */
        public EventContext getContext() {
            return context;
        }

        /**
         * Resolves remaining lifetime in server ticks.
         *
         * @return Remaining ticks count
         */
        public int getRemainingTicks() {
            return remainingTicks;
        }
    }
}