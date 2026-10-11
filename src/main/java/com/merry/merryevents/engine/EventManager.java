package com.merry.merryevents.engine;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.event.inventory.*;
import com.merry.merryevents.event.mobs.*;
import com.merry.merryevents.event.physics.*;
import com.merry.merryevents.event.sounds.*;
import com.merry.merryevents.event.status.*;
import com.merry.merryevents.event.ui.*;
import com.merry.merryevents.event.vision.*;
import com.merry.merryevents.event.visual.*;
import com.merry.merryevents.event.weather.*;
import com.merry.merryevents.event.world.*;
import com.merry.merryevents.event.camera.*;
import com.merry.merryevents.network.*;
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
        this.register(new AcidRainEvent());
        this.register(new MoonGravityEvent());
        this.register(new FalseWaterEvent());
        this.register(new LavaWaterSwapEvent());
        this.register(new ExplodingMobsEvent());
        this.register(new FragilityEvent());
        this.register(new DiamondRushEvent());
        this.register(new GhostGuiEvent());
        this.register(new ParanoiaSoundsEvent());
        this.register(new PersonalKillerEvent());
        this.register(new AnvilRainEvent());
        this.register(new DenseDarknessEvent());
        this.register(new RustRainEvent());
        this.register(new FoodRainEvent());
        this.register(new FireStormEvent());
        this.register(new GhastFireStormEvent());
        this.register(new StiffNeckEvent());
        this.register(new SolarVerdictEvent());
        this.register(new OrbitalStrikeEvent());
        this.register(new CobwebNightmareEvent());
        this.register(new DontStepOnEvent());
        this.register(new FloorHoleEvent());
        this.register(new CrumblingFloorEvent());
        this.register(new WanderingBlocksEvent());
        this.register(new FlatMobsEvent());
    }

    public static EventManager getInstance() {
        return EventManager.INSTANCE;
    }

    public void setGlobalRemainingTicks(Identifier eventId, int ticks) {
        ActiveEventInstance instance = this.activeGlobalEvents.get(eventId);
        if (instance != null) {
            instance.remainingTicks = ticks;
        }
    }

    public void register(ChaosEvent event) {
        this.registry.put(event.getId(), event);
        MerryEvents.LOGGER.info("[EventManager] Registered chaos event '{}' in category {}", event.getId(),
                event.getCategory());
    }

    public Map<Identifier, ChaosEvent> getRegistry() {
        return Collections.unmodifiableMap(this.registry);
    }

    public @Nullable ChaosEvent getEvent(Identifier id) {
        return this.registry.get(id);
    }

    public java.util.Set<Identifier> getRegisteredEventIds() {
        return this.registry.keySet();
    }

    public java.util.Set<Identifier> getActiveGlobalEventIds() {
        return this.activeGlobalEvents.keySet();
    }

    public Map<Identifier, ActiveEventInstance> getActiveGlobalEvents() {
        return Collections.unmodifiableMap(this.activeGlobalEvents);
    }

    public Map<UUID, Map<Identifier, ActiveEventInstance>> getActivePlayerEvents() {
        return Collections.unmodifiableMap(this.activePlayerEvents);
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
        return (this.getGlobalCategoryMask() & category.getMask()) != 0;
    }

    public boolean isCategoryOccupiedForPlayer(UUID uuid, EventCategory category) {
        return (this.getPlayerCategoryMask(uuid) & category.getMask()) != 0;
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

        this.activeGlobalEvents.clear();

        for (Map.Entry<Identifier, EventPersistentState.SavedEventData> entry : state.globalEvents.entrySet()) {
            ChaosEvent event = this.registry.get(entry.getKey());
            if (event != null && event.isGlobal()) {
                ActiveEventInstance instance = new ActiveEventInstance(event, entry.getValue().remainingTicks());
                this.activeGlobalEvents.put(entry.getKey(), instance);
                instance.event.onStartServer(server, null, instance.context);
                MerryEvents.LOGGER.info("[EventManager] Resumed global event '{}' ({} ticks remaining)", entry.getKey(),
                        instance.remainingTicks);
            }
        }

        this.activePlayerEvents.clear();

        for (Map.Entry<UUID, Map<Identifier, EventPersistentState.SavedEventData>> pEntry : state.playerEvents
                .entrySet()) {
            UUID uuid = pEntry.getKey();
            Map<Identifier, ActiveEventInstance> map = new ConcurrentHashMap<>();

            for (Map.Entry<Identifier, EventPersistentState.SavedEventData> eEntry : pEntry.getValue().entrySet()) {
                ChaosEvent event = this.registry.get(eEntry.getKey());
                if (event != null && !event.isGlobal()) {
                    ActiveEventInstance instance = new ActiveEventInstance(event, eEntry.getValue().remainingTicks());
                    map.put(eEntry.getKey(), instance);
                }
            }

            if (!map.isEmpty()) {
                this.activePlayerEvents.put(uuid, map);
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
        ChaosEvent event = this.registry.get(eventId);
        if (event == null || !event.isGlobal()) {
            MerryEvents.LOGGER.warn("[EventManager] Invalid global event target: {}", eventId);
            return false;
        }

        if (this.isCategoryOccupiedGlobally(event.getCategory())) {
            MerryEvents.LOGGER.warn("[EventManager] Conflict: category {} already occupied globally",
                    event.getCategory());
            return false;
        }

        ActiveEventInstance instance = new ActiveEventInstance(event, durationTicks);
        this.activeGlobalEvents.put(eventId, instance);
        event.onStartServer(server, null, instance.context);

        if (this.persistentState != null) {
            this.persistentState.markDirty();
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
        ChaosEvent event = this.registry.get(eventId);
        if (event == null || event.isGlobal()) {
            MerryEvents.LOGGER.warn("[EventManager] Invalid player event target: {}", eventId);
            return false;
        }

        UUID uuid = player.getUuid();
        if (this.isCategoryOccupiedForPlayer(uuid, event.getCategory())) {
            MerryEvents.LOGGER.warn("[EventManager] Conflict: category {} active for {}", event.getCategory(),
                    player.getName().getString());
            return false;
        }

        ActiveEventInstance instance = new ActiveEventInstance(event, durationTicks);
        this.activePlayerEvents.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(eventId, instance);
        event.onStartServer(server, player, instance.context);

        if (this.persistentState != null) {
            this.persistentState.markDirty();
        }

        EventSyncPayload payload = new EventSyncPayload(eventId, event.getCategory().name(), durationTicks, true,
                false);
        ServerPlayNetworking.send(player, payload);

        MerryEvents.LOGGER.info("[EventManager] Started event '{}' for {} ({} ticks)", eventId,
                player.getName().getString(), durationTicks);
        return true;
    }

    public boolean stopGlobalEvent(MinecraftServer server, Identifier eventId) {
        ActiveEventInstance instance = this.activeGlobalEvents.remove(eventId);
        if (instance != null) {
            instance.event.onStopServer(server, null, instance.context);
            instance.context.discardTrackedEntities(server);
            instance.context.clear();

            if (this.persistentState != null) {
                this.persistentState.markDirty();
            }

            EventSyncPayload payload = new EventSyncPayload(eventId, instance.event.getCategory().name(), 0, false,
                    true);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, payload);
            }

            MerryEvents.LOGGER.info("[EventManager] Global event '{}' terminated", eventId);
            return true;
        }
        return false;
    }

    public boolean stopPlayerEvent(MinecraftServer server, ServerPlayerEntity player, Identifier eventId) {
        UUID uuid = player.getUuid();
        Map<Identifier, ActiveEventInstance> events = this.activePlayerEvents.get(uuid);
        if (events != null) {
            ActiveEventInstance instance = events.remove(eventId);
            if (instance != null) {
                instance.event.onStopServer(server, player, instance.context);
                instance.context.discardTrackedEntities(server);
                instance.context.clear();

                if (events.isEmpty()) {
                    this.activePlayerEvents.remove(uuid);
                }

                if (this.persistentState != null) {
                    this.persistentState.markDirty();
                }

                EventSyncPayload payload = new EventSyncPayload(eventId, instance.event.getCategory().name(), 0, false,
                        false);
                ServerPlayNetworking.send(player, payload);

                MerryEvents.LOGGER.info("[EventManager] Player event '{}' terminated for {}", eventId,
                        player.getName().getString());
                return true;
            }
        }
        return false;
    }

    public void onServerStopping(MinecraftServer server) {
        if (this.persistentState != null) {
            this.persistentState.markDirty();
        }

        for (ActiveEventInstance instance : this.activeGlobalEvents.values()) {
            instance.event.onStopServer(server, null, instance.context);
            instance.context.discardTrackedEntities(server);
        }

        for (Map.Entry<UUID, Map<Identifier, ActiveEventInstance>> entry : this.activePlayerEvents.entrySet()) {
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
        for (Map.Entry<Identifier, ActiveEventInstance> entry : this.activeGlobalEvents.entrySet()) {
            ActiveEventInstance inst = entry.getValue();
            ServerPlayNetworking.send(player, new EventSyncPayload(entry.getKey(), inst.event.getCategory().name(),
                    inst.remainingTicks, true, true));
        }

        Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.get(player.getUuid());
        if (pEvents != null) {
            for (Map.Entry<Identifier, ActiveEventInstance> entry : pEvents.entrySet()) {
                ActiveEventInstance inst = entry.getValue();
                ServerPlayNetworking.send(player, new EventSyncPayload(entry.getKey(), inst.event.getCategory().name(),
                        inst.remainingTicks, true, false));
            }
        }

        MinecraftServer server = player.getServer();
        if (server != null) {
            for (Map.Entry<UUID, Map<Identifier, ActiveEventInstance>> pEntry : this.activePlayerEvents.entrySet()) {
                if (pEntry.getValue().containsKey(DontStepOnEvent.ID)) {
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(pEntry.getKey());
                    if (target != null) {
                        ServerPlayNetworking.send(player,
                                new ForbiddenBlocksPayload(target.getGameProfile().getName(), true));
                    }
                }
            }
        }
    }

    public void onPlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.get(player.getUuid());
        if (pEvents != null) {
            for (ActiveEventInstance inst : pEvents.values()) {
                inst.event.onStartServer(server, player, inst.context);
            }
        }
        this.syncAllToPlayer(player);
    }

    public void onPlayerDisconnect(MinecraftServer server, ServerPlayerEntity player) {
        Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.get(player.getUuid());
        if (pEvents != null) {
            for (ActiveEventInstance instance : pEvents.values()) {
                instance.event.onStopServer(server, player, instance.context);
                instance.context.discardTrackedEntities(server);
            }
        }
        if (this.persistentState != null) {
            this.persistentState.markDirty();
        }
    }

    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        UUID uuid = newPlayer.getUuid();
        Map<Identifier, ActiveEventInstance> pEvents = this.activePlayerEvents.get(uuid);
        if (pEvents != null) {
            for (ActiveEventInstance inst : pEvents.values()) {
                inst.event.onPlayerRespawn(server, oldPlayer, newPlayer, inst.context);
            }
            this.syncAllToPlayer(newPlayer);
        }
    }

    public void tick(MinecraftServer server) {
        ChaosDirector.getInstance().tick(server);

        this.globalRemovalQueue.clear();
        for (Map.Entry<Identifier, ActiveEventInstance> entry : this.activeGlobalEvents.entrySet()) {
            ActiveEventInstance instance = entry.getValue();
            instance.event.onTickServer(server, null, instance.context, instance.remainingTicks);

            if (--instance.remainingTicks <= 0) {
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
                instance.event.onTickServer(server, player, instance.context, instance.remainingTicks);

                if (--instance.remainingTicks <= 0) {
                    this.playerRemovalQueue.add(entry.getKey());
                }
            }

            for (int i = 0; i < this.playerRemovalQueue.size(); i++) {
                this.stopPlayerEvent(server, player, this.playerRemovalQueue.get(i));
            }
        }

        if (server.getTicks() % 100 == 0 && this.persistentState != null
                && (!this.activeGlobalEvents.isEmpty() || !this.activePlayerEvents.isEmpty())) {
            this.persistentState.markDirty();
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
            return this.event;
        }

        /**
         * Resolves the isolated lifecycle context of this running instance.
         *
         * @return EventContext instance
         */
        public EventContext getContext() {
            return this.context;
        }

        /**
         * Resolves remaining lifetime in server ticks.
         *
         * @return Remaining ticks count
         */
        public int getRemainingTicks() {
            return this.remainingTicks;
        }
    }
}