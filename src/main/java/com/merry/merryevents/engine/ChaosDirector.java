package com.merry.merryevents.engine;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.config.EventConfigEntry;
import com.merry.merryevents.config.MerryConfig;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Autonomous director governing global and per-player chaos event ignition.
 * Executes roulette wheel selection weighted by configuration tickets with
 * dynamic
 * repetition penalties (0.5x / 0.25x) and isolated player history streams.
 */
public final class ChaosDirector {
    private static final double REPEATED_CATEGORY_WEIGHT_MULTIPLIER = 0.5;
    private static final double REPEATED_EVENT_WEIGHT_MULTIPLIER = 0.5;
    private static final ChaosDirector INSTANCE = new ChaosDirector();

    private final Object2IntOpenHashMap<UUID> playerCountdowns = new Object2IntOpenHashMap<>();
    private final Map<UUID, PlayerHistory> playerHistories = new HashMap<>();

    @Nullable
    private Identifier lastGlobalEventId = null;
    @Nullable
    private EventCategory lastGlobalCategory = null;

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

    public Object2IntOpenHashMap<UUID> getPlayerCountdowns() {
        return this.playerCountdowns;
    }

    public @Nullable Identifier getLastGlobalEventId() {
        return this.lastGlobalEventId;
    }

    public @Nullable EventCategory getLastGlobalCategory() {
        return this.lastGlobalCategory;
    }

    public Map<UUID, PlayerHistory> getPlayerHistories() {
        return Collections.unmodifiableMap(this.playerHistories);
    }

    public void setGlobalCountdown(int ticks) {
        this.ticksUntilNextGlobalEvent = ticks;
    }

    public void resetState() {
        this.playerCountdowns.clear();
        this.playerHistories.clear();
        this.lastGlobalEventId = null;
        this.lastGlobalCategory = null;
        this.ticksUntilNextGlobalEvent = 0;
        MerryEvents.LOGGER.info("[ChaosDirector] Reset director state upon server shutdown.");
    }

    public void initializeGlobalCountdown() {
        MerryConfig config = ConfigManager.get();
        int minSeconds = config.getMinGlobalIntervalSeconds();
        int maxSeconds = Math.max(minSeconds, config.getMaxGlobalIntervalSeconds());
        this.ticksUntilNextGlobalEvent = (minSeconds + this.random.nextInt(maxSeconds - minSeconds + 1)) * 20;
    }

    public void initializePlayerCountdown(UUID playerUuid) {
        MerryConfig config = ConfigManager.get();
        int minSeconds = config.getMinPersonalIntervalSeconds();
        int maxSeconds = Math.max(minSeconds, config.getMaxPersonalIntervalSeconds());
        int ticks = (minSeconds + this.random.nextInt(maxSeconds - minSeconds + 1)) * 20;
        this.playerCountdowns.put(playerUuid, ticks);
    }

    public void restorePlayerCountdowns(Map<UUID, Integer> savedCountdowns) {
        this.playerCountdowns.clear();
        for (Map.Entry<UUID, Integer> entry : savedCountdowns.entrySet()) {
            this.playerCountdowns.put(entry.getKey(), entry.getValue().intValue());
        }
    }

    public void restoreHistories(
            @Nullable Identifier lastGlobalId,
            @Nullable EventCategory lastGlobalCat,
            Map<UUID, PlayerHistory> histories) {
        this.lastGlobalEventId = lastGlobalId;
        this.lastGlobalCategory = lastGlobalCat;
        this.playerHistories.clear();
        this.playerHistories.putAll(histories);
    }

    public void onPlayerJoin(UUID playerUuid) {
        if (!this.playerCountdowns.containsKey(playerUuid)) {
            this.initializePlayerCountdown(playerUuid);
        }
    }

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

    public void applyFastRetryGlobal() {
        MerryConfig config = ConfigManager.get();
        int minSec = config.getMinGlobalIntervalSeconds();
        int maxSec = Math.max(minSec, config.getMaxGlobalIntervalSeconds());
        int normalTicks = (minSec + this.random.nextInt(maxSec - minSec + 1)) * 20;
        int divisor = Math.max(1, config.getRetryDivisor());
        this.ticksUntilNextGlobalEvent = Math.max(20, normalTicks / divisor);
        MerryEvents.LOGGER.debug("[ChaosDirector] Global event conflict encountered; fast retry in {} ticks (~{}s)",
                this.ticksUntilNextGlobalEvent, this.ticksUntilNextGlobalEvent / 20);
    }

    public void applyFastRetryPlayer(UUID playerUuid) {
        MerryConfig config = ConfigManager.get();
        int minSec = config.getMinPersonalIntervalSeconds();
        int maxSec = Math.max(minSec, config.getMaxPersonalIntervalSeconds());
        int normalTicks = (minSec + this.random.nextInt(maxSec - minSec + 1)) * 20;
        int divisor = Math.max(1, config.getRetryDivisor());
        int retryTicks = Math.max(20, normalTicks / divisor);
        this.playerCountdowns.put(playerUuid, retryTicks);
        MerryEvents.LOGGER.debug("[ChaosDirector] Personal event conflict for {}; fast retry in {} ticks (~{}s)",
                playerUuid, retryTicks, retryTicks / 20);
    }

    public boolean dispatchNextGlobalEvent(MinecraftServer server) {
        EventManager eventManager = EventManager.getInstance();
        MerryConfig config = ConfigManager.get();
        CandidatePool candidatePool = this.collectCandidates(server, null);
        if (candidatePool.events().isEmpty() || candidatePool.totalWeight() <= 0.0) {
            return false;
        }

        double roll = this.random.nextDouble() * candidatePool.totalWeight();
        double accumulated = 0.0;
        ChaosEvent chosenEvent = candidatePool.events().get(candidatePool.events().size() - 1).event();

        for (WeightedEvent candidate : candidatePool.events()) {
            accumulated += candidate.effectiveWeight();
            if (roll <= accumulated) {
                chosenEvent = candidate.event();
                break;
            }
        }

        EventConfigEntry chosenEntry = config.getEvents().get(chosenEvent.getId().toString());
        int customTicks = chosenEvent.getCustomDurationTicks(server);
        int durationTicks;
        if (customTicks > 0) {
            durationTicks = customTicks;
        } else {
            int minSec = chosenEntry != null ? chosenEntry.minDurationSeconds() : 30;
            int maxSec = chosenEntry != null ? chosenEntry.maxDurationSeconds() : 60;
            durationTicks = (minSec + this.random.nextInt(Math.max(1, maxSec - minSec + 1))) * 20;
        }

        boolean started = eventManager.startGlobalEvent(server, chosenEvent.getId(), durationTicks);
        if (started) {
            this.lastGlobalEventId = chosenEvent.getId();
            this.lastGlobalCategory = chosenEvent.getCategory();
            MerryEvents.LOGGER.info("[ChaosDirector] Ignited global event '{}' via weighted selection for {} ticks",
                    chosenEvent.getId(), durationTicks);
            return true;
        }

        return false;
    }

    public boolean dispatchNextPersonalEvent(MinecraftServer server, ServerPlayerEntity targetPlayer) {
        EventManager eventManager = EventManager.getInstance();
        MerryConfig config = ConfigManager.get();
        UUID playerUuid = targetPlayer.getUuid();
        CandidatePool candidatePool = this.collectCandidates(server, targetPlayer);
        if (candidatePool.events().isEmpty() || candidatePool.totalWeight() <= 0.0) {
            return false;
        }

        double roll = this.random.nextDouble() * candidatePool.totalWeight();
        double accumulated = 0.0;
        ChaosEvent chosenEvent = candidatePool.events().get(candidatePool.events().size() - 1).event();

        for (WeightedEvent candidate : candidatePool.events()) {
            accumulated += candidate.effectiveWeight();
            if (roll <= accumulated) {
                chosenEvent = candidate.event();
                break;
            }
        }

        EventConfigEntry chosenEntry = config.getEvents().get(chosenEvent.getId().toString());
        int customTicks = chosenEvent.getCustomDurationTicks(server);
        int durationTicks;
        if (customTicks > 0) {
            durationTicks = customTicks;
        } else {
            int minSec = chosenEntry != null ? chosenEntry.minDurationSeconds() : 30;
            int maxSec = chosenEntry != null ? chosenEntry.maxDurationSeconds() : 60;
            durationTicks = (minSec + this.random.nextInt(Math.max(1, maxSec - minSec + 1))) * 20;
        }

        boolean started = eventManager.startPlayerEvent(server, targetPlayer, chosenEvent.getId(), durationTicks);
        if (started) {
            this.playerHistories.put(playerUuid, new PlayerHistory(chosenEvent.getId(), chosenEvent.getCategory()));
            MerryEvents.LOGGER.info(
                    "[ChaosDirector] Ignited personal event '{}' on {} via weighted selection for {} ticks",
                    chosenEvent.getId(), targetPlayer.getName().getString(), durationTicks);
            return true;
        }

        return false;
    }

    public List<EventWeight> getGlobalEventWeights(MinecraftServer server) {
        return this.createWeightReport(this.collectCandidates(server, null));
    }

    public List<EventWeight> getPersonalEventWeights(MinecraftServer server, ServerPlayerEntity player) {
        return this.createWeightReport(this.collectCandidates(server, player));
    }

    private CandidatePool collectCandidates(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer) {
        EventManager eventManager = EventManager.getInstance();
        MerryConfig config = ConfigManager.get();
        UUID playerUuid = targetPlayer != null ? targetPlayer.getUuid() : null;
        PlayerHistory history = playerUuid != null ? this.playerHistories.get(playerUuid) : null;
        boolean global = targetPlayer == null;

        List<WeightedEvent> candidates = new ArrayList<>();
        double totalWeight = 0.0;

        for (ChaosEvent event : eventManager.getRegistry().values()) {
            if (event.isGlobal() != global || event.getDefinition().isWip()) {
                continue;
            }

            EventConfigEntry entry = config.getEvents().get(event.getId().toString());
            if (entry != null && !entry.enabled()) {
                continue;
            }

            boolean hasCategoryCapacity = global
                    ? eventManager.hasCategoryCapacityForGlobalEvent(event.getDefinition())
                    : eventManager.hasCategoryCapacityForPlayerEvent(playerUuid, event.getDefinition());
            if (!hasCategoryCapacity || !event.canStart(server, targetPlayer)) {
                continue;
            }

            int baseWeight = entry != null ? entry.weight() : event.getDefinition().defaultWeight();
            EventCategory lastCategory = global ? this.lastGlobalCategory
                    : history != null ? history.lastCategory() : null;
            Identifier lastEventId = global ? this.lastGlobalEventId
                    : history != null ? history.lastEventId() : null;
            double categoryMultiplier = lastCategory != null && event.getCategories().contains(lastCategory)
                    ? ChaosDirector.REPEATED_CATEGORY_WEIGHT_MULTIPLIER
                    : 1.0;
            double eventMultiplier = lastEventId != null && event.getId().equals(lastEventId)
                    ? ChaosDirector.REPEATED_EVENT_WEIGHT_MULTIPLIER
                    : 1.0;
            double effectiveWeight = baseWeight * categoryMultiplier * eventMultiplier;

            candidates.add(new WeightedEvent(event, baseWeight, effectiveWeight));
            totalWeight += effectiveWeight;
        }

        return new CandidatePool(candidates, totalWeight);
    }

    private List<EventWeight> createWeightReport(CandidatePool candidatePool) {
        if (candidatePool.totalWeight() <= 0.0) {
            return List.of();
        }

        List<EventWeight> report = new ArrayList<>(candidatePool.events().size());
        for (WeightedEvent candidate : candidatePool.events()) {
            report.add(new EventWeight(candidate.event(), candidate.baseWeight(), candidate.effectiveWeight(),
                    candidate.effectiveWeight() / candidatePool.totalWeight()));
        }
        return List.copyOf(report);
    }

    private record WeightedEvent(ChaosEvent event, int baseWeight, double effectiveWeight) {
    }

    private record CandidatePool(List<WeightedEvent> events, double totalWeight) {
    }

    public record EventWeight(ChaosEvent event, int baseWeight, double effectiveWeight, double probability) {
    }

    public record PlayerHistory(@Nullable Identifier lastEventId, @Nullable EventCategory lastCategory) {
    }
}
