package com.merry.merryevents.engine;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.config.EventConfigEntry;
import com.merry.merryevents.config.MerryConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Autonomous scheduler running dual stochastic shuffle bags for global events
 * and independent per-player asynchronous event loops with fast-retry backoffs.
 */
public final class ChaosDirector {
    private static final ChaosDirector INSTANCE = new ChaosDirector();

    private final Deque<Identifier> globalDeck = new ArrayDeque<>();
    private final Deque<Identifier> personalDeck = new ArrayDeque<>();
    private final Object2IntOpenHashMap<UUID> playerCountdowns = new Object2IntOpenHashMap<>();
    private int ticksUntilNextGlobalEvent = 0;
    private final Random random = Random.create();

    private ChaosDirector() {
        this.playerCountdowns.defaultReturnValue(-1);
    }

    public static ChaosDirector getInstance() {
        return ChaosDirector.INSTANCE;
    }

    public int getTicksUntilNextGlobalEvent() {
        return this.ticksUntilNextGlobalEvent;
    }

    public Deque<Identifier> getGlobalDeck() {
        return this.globalDeck;
    }

    public Deque<Identifier> getPersonalDeck() {
        return this.personalDeck;
    }

    public Object2IntOpenHashMap<UUID> getPlayerCountdowns() {
        return this.playerCountdowns;
    }

    public void setGlobalCountdown(int ticks) {
        this.ticksUntilNextGlobalEvent = ticks;
    }

    /**
     * Initializes a random global tick countdown bounded by configured minimum and
     * maximum limits.
     */
    public void initializeGlobalCountdown() {
        MerryConfig config = ConfigManager.get();
        int minSeconds = config.getMinGlobalIntervalSeconds();
        int maxSeconds = Math.max(minSeconds, config.getMaxGlobalIntervalSeconds());
        this.ticksUntilNextGlobalEvent = (minSeconds + this.random.nextInt(maxSeconds - minSeconds + 1)) * 20;
    }

    /**
     * Initializes an asynchronous personal tick countdown for an individual player
     * profile.
     *
     * @param playerUuid Target player unique identifier
     */
    public void initializePlayerCountdown(UUID playerUuid) {
        MerryConfig config = ConfigManager.get();
        int minSeconds = config.getMinPersonalIntervalSeconds();
        int maxSeconds = Math.max(minSeconds, config.getMaxPersonalIntervalSeconds());
        int ticks = (minSeconds + this.random.nextInt(maxSeconds - minSeconds + 1)) * 20;
        this.playerCountdowns.put(playerUuid, ticks);
    }

    /**
     * Restores preserved player countdown values from world persistent storage.
     *
     * @param savedCountdowns Map of player UUIDs to remaining countdown ticks
     */
    public void restorePlayerCountdowns(Map<UUID, Integer> savedCountdowns) {
        this.playerCountdowns.clear();
        for (Map.Entry<UUID, Integer> entry : savedCountdowns.entrySet()) {
            this.playerCountdowns.put(entry.getKey(), entry.getValue().intValue());
        }
    }

    /**
     * Synchronizes personal countdown allocation upon player world entry.
     *
     * @param playerUuid Joining player unique identifier
     */
    public void onPlayerJoin(UUID playerUuid) {
        if (!this.playerCountdowns.containsKey(playerUuid)) {
            this.initializePlayerCountdown(playerUuid);
        }
    }

    /**
     * Ticks director schedulers across global and per-player dispatch pipelines
     * with zero tick allocations.
     *
     * @param server Active MinecraftServer instance
     */
    public void tick(MinecraftServer server) {
        if (!ConfigManager.get().isAutoDirectorEnabled()) {
            return;
        }

        this.tickGlobal(server);
        this.tickPersonal(server);
    }

    private void tickGlobal(MinecraftServer server) {
        if (this.ticksUntilNextGlobalEvent <= 0) {
            this.initializeGlobalCountdown();
            return;
        }

        if (--this.ticksUntilNextGlobalEvent <= 0) {
            boolean triggered = this.dispatchNextGlobalEvent(server);
            if (triggered) {
                this.initializeGlobalCountdown();
            } else {
                this.applyFastRetryGlobal();
            }
        }
    }

    private void tickPersonal(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        int playerCount = players.size();
        if (playerCount == 0) {
            return;
        }

        for (int i = 0; i < playerCount; i++) {
            ServerPlayerEntity player = players.get(i);
            UUID uuid = player.getUuid();
            int remaining = this.playerCountdowns.getInt(uuid);

            if (remaining <= 0) {
                if (remaining == -1) {
                    this.initializePlayerCountdown(uuid);
                    continue;
                }

                boolean triggered = this.dispatchNextPersonalEvent(server, player);
                if (triggered) {
                    this.initializePlayerCountdown(uuid);
                } else {
                    this.applyFastRetryPlayer(uuid);
                }
            } else {
                this.playerCountdowns.put(uuid, remaining - 1);
            }
        }
    }

    /**
     * Applies an accelerated retry countdown for global events when category
     * collisions prevent execution.
     */
    public void applyFastRetryGlobal() {
        MerryConfig config = ConfigManager.get();
        int minSec = config.getMinGlobalIntervalSeconds();
        int maxSec = Math.max(minSec, config.getMaxGlobalIntervalSeconds());
        int normalTicks = (minSec + this.random.nextInt(maxSec - minSec + 1)) * 20;
        int divisor = Math.max(1, config.getRetryDivisor());
        this.ticksUntilNextGlobalEvent = Math.max(20, normalTicks / divisor);
        MerryEvents.LOGGER.info("[ChaosDirector] Global event conflict encountered; fast retry in {} ticks (~{}s)",
                this.ticksUntilNextGlobalEvent, this.ticksUntilNextGlobalEvent / 20);
    }

    /**
     * Applies an accelerated retry countdown for a specific player when category
     * collisions prevent execution.
     *
     * @param playerUuid Target player unique identifier
     */
    public void applyFastRetryPlayer(UUID playerUuid) {
        MerryConfig config = ConfigManager.get();
        int minSec = config.getMinPersonalIntervalSeconds();
        int maxSec = Math.max(minSec, config.getMaxPersonalIntervalSeconds());
        int normalTicks = (minSec + this.random.nextInt(maxSec - minSec + 1)) * 20;
        int divisor = Math.max(1, config.getRetryDivisor());
        int retryTicks = Math.max(20, normalTicks / divisor);
        this.playerCountdowns.put(playerUuid, retryTicks);
        MerryEvents.LOGGER.info("[ChaosDirector] Personal event conflict for {}; fast retry in {} ticks (~{}s)",
                playerUuid, retryTicks, retryTicks / 20);
    }

    /**
     * Pulls the next eligible global event from the shuffle deck and ignites it
     * natively.
     *
     * @param server Active MinecraftServer instance
     * @return True if a global event successfully ignited
     */
    public boolean dispatchNextGlobalEvent(MinecraftServer server) {
        if (this.globalDeck.isEmpty()) {
            this.refillGlobalDeck();
        }

        if (this.dispatchFromGlobalDeck(server)) {
            return true;
        }

        if (!this.globalDeck.isEmpty()) {
            return false;
        }

        this.refillGlobalDeck();
        return this.dispatchFromGlobalDeck(server);
    }

    private boolean dispatchFromGlobalDeck(MinecraftServer server) {
        int attempts = this.globalDeck.size();
        for (int i = 0; i < attempts; i++) {
            Identifier candidateId = this.globalDeck.pollFirst();
            if (candidateId == null) {
                break;
            }

            ChaosEvent event = EventManager.getInstance().getEvent(candidateId);
            EventConfigEntry entry = ConfigManager.get().getEvents().get(candidateId.toString());

            if (event == null || !event.isGlobal() || (entry != null && !entry.enabled())) {
                continue;
            }

            if (EventManager.getInstance().isCategoryOccupiedGlobally(event.getCategory())) {
                this.globalDeck.addLast(candidateId);
                continue;
            }

            int customTicks = event.getCustomDurationTicks(server);
            int durationTicks;
            if (customTicks > 0) {
                durationTicks = customTicks;
            } else {
                int minSec = entry != null ? entry.minDurationSeconds() : 30;
                int maxSec = entry != null ? entry.maxDurationSeconds() : 60;
                durationTicks = (minSec + this.random.nextInt(Math.max(1, maxSec - minSec + 1))) * 20;
            }

            boolean started = EventManager.getInstance().startGlobalEvent(server, candidateId, durationTicks);
            if (started) {
                MerryEvents.LOGGER.info("[ChaosDirector] Silently ignited global event '{}' for {} ticks",
                        candidateId, durationTicks);
                return true;
            }
        }

        return false;
    }

    /**
     * Pulls the next eligible personal event from the shuffle deck and ignites it
     * on the target player.
     *
     * @param server       Active MinecraftServer instance
     * @param targetPlayer Targeted server player entity
     * @return True if a personal event successfully ignited
     */
    public boolean dispatchNextPersonalEvent(MinecraftServer server, ServerPlayerEntity targetPlayer) {
        if (this.personalDeck.isEmpty()) {
            this.refillPersonalDeck();
        }

        if (this.dispatchFromPersonalDeck(server, targetPlayer)) {
            return true;
        }

        if (!this.personalDeck.isEmpty()) {
            return false;
        }

        this.refillPersonalDeck();
        return this.dispatchFromPersonalDeck(server, targetPlayer);
    }

    private boolean dispatchFromPersonalDeck(MinecraftServer server, ServerPlayerEntity targetPlayer) {
        int attempts = this.personalDeck.size();
        for (int i = 0; i < attempts; i++) {
            Identifier candidateId = this.personalDeck.pollFirst();
            if (candidateId == null) {
                break;
            }

            ChaosEvent event = EventManager.getInstance().getEvent(candidateId);
            EventConfigEntry entry = ConfigManager.get().getEvents().get(candidateId.toString());

            if (event == null || event.isGlobal() || (entry != null && !entry.enabled())) {
                continue;
            }

            if (EventManager.getInstance().isCategoryOccupiedForPlayer(targetPlayer.getUuid(), event.getCategory())) {
                this.personalDeck.addLast(candidateId);
                continue;
            }

            int customTicks = event.getCustomDurationTicks(server);
            int durationTicks;
            if (customTicks > 0) {
                durationTicks = customTicks;
            } else {
                int minSec = entry != null ? entry.minDurationSeconds() : 30;
                int maxSec = entry != null ? entry.maxDurationSeconds() : 60;
                durationTicks = (minSec + this.random.nextInt(Math.max(1, maxSec - minSec + 1))) * 20;
            }

            boolean started = EventManager.getInstance().startPlayerEvent(server, targetPlayer, candidateId,
                    durationTicks);
            if (started) {
                MerryEvents.LOGGER.info("[ChaosDirector] Silently ignited personal event '{}' on {} for {} ticks",
                        candidateId, targetPlayer.getName().getString(), durationTicks);
                return true;
            }
        }

        return false;
    }

    /**
     * Replenishes both global and personal shuffle bags based on configured event
     * weights.
     */
    public void refillDecks() {
        this.refillGlobalDeck();
        this.refillPersonalDeck();
    }

    /**
     * Replenishes and shuffles global event tickets based on configured weights.
     */
    public void refillGlobalDeck() {
        this.globalDeck.clear();
        MerryConfig config = ConfigManager.get();
        List<Identifier> pool = new ArrayList<>();

        for (Map.Entry<Identifier, ChaosEvent> entry : EventManager.getInstance().getRegistry().entrySet()) {
            if (!entry.getValue().isGlobal()) {
                continue;
            }
            Identifier id = entry.getKey();
            EventConfigEntry cfg = config.getEvents().get(id.toString());
            if (cfg == null || cfg.enabled()) {
                int weight = cfg != null ? cfg.weight() : 1;
                for (int w = 0; w < weight; w++) {
                    pool.add(id);
                }
            }
        }

        Collections.shuffle(pool);
        this.globalDeck.addAll(pool);
        MerryEvents.LOGGER.info("[ChaosDirector] Global shuffle bag replenished with {} event tickets.",
                this.globalDeck.size());
    }

    /**
     * Replenishes and shuffles personal event tickets based on configured weights.
     */
    public void refillPersonalDeck() {
        this.personalDeck.clear();
        MerryConfig config = ConfigManager.get();
        List<Identifier> pool = new ArrayList<>();

        for (Map.Entry<Identifier, ChaosEvent> entry : EventManager.getInstance().getRegistry().entrySet()) {
            if (entry.getValue().isGlobal()) {
                continue;
            }
            Identifier id = entry.getKey();
            EventConfigEntry cfg = config.getEvents().get(id.toString());
            if (cfg == null || cfg.enabled()) {
                int weight = cfg != null ? cfg.weight() : 1;
                for (int w = 0; w < weight; w++) {
                    pool.add(id);
                }
            }
        }

        Collections.shuffle(pool);
        this.personalDeck.addAll(pool);
        MerryEvents.LOGGER.info("[ChaosDirector] Personal shuffle bag replenished with {} event tickets.",
                this.personalDeck.size());
    }
}