package com.merry.merryevents.engine;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.config.EventConfigEntry;
import com.merry.merryevents.config.MerryConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Autonomous scheduler running the Stochastic Shuffle Bag algorithm to trigger
 * chaos events.
 */
public final class ChaosDirector {
    private static final ChaosDirector INSTANCE = new ChaosDirector();

    private final Deque<Identifier> deck = new ArrayDeque<>();
    private int ticksUntilNextEvent = 0;
    private final Random random = Random.create();

    private ChaosDirector() {
    }

    public static ChaosDirector getInstance() {
        return INSTANCE;
    }

    public int getTicksUntilNextEvent() {
        return ticksUntilNextEvent;
    }

    public Deque<Identifier> getDeck() {
        return deck;
    }

    public void setCountdown(int ticks) {
        this.ticksUntilNextEvent = ticks;
    }

    /**
     * Initializes random tick countdown between configured minimum and maximum
     * bounds.
     */
    public void initializeCountdown() {
        MerryConfig config = ConfigManager.get();
        int minTicks = config.getMinIntervalSeconds() * 20;
        int maxTicks = config.getMaxIntervalSeconds() * 20;
        int delta = Math.max(1, maxTicks - minTicks);
        this.ticksUntilNextEvent = minTicks + random.nextInt(delta);
    }

    /**
     * Ticks director scheduler and fires next event when countdown expires.
     *
     * @param server Active MinecraftServer instance
     */
    public void tick(MinecraftServer server) {
        if (!ConfigManager.get().isAutoDirectorEnabled()) {
            return;
        }

        if (ticksUntilNextEvent <= 0) {
            initializeCountdown();
            return;
        }

        if (--ticksUntilNextEvent <= 0) {
            dispatchNextEvent(server);
            initializeCountdown();
        }
    }

    /**
     * Pulls the next eligible event from the shuffle deck and ignites it natively.
     *
     * @param server Active server instance
     * @return True if an event successfully ignited
     */
    public boolean dispatchNextEvent(MinecraftServer server) {
        if (deck.isEmpty()) {
            refillDeck();
        }

        int attempts = deck.size();
        for (int i = 0; i < attempts; i++) {
            Identifier candidateId = deck.pollFirst();
            if (candidateId == null) {
                break;
            }

            ChaosEvent event = EventManager.getInstance().getEvent(candidateId);
            EventConfigEntry entry = ConfigManager.get().getEvents().get(candidateId.toString());

            if (event == null || (entry != null && !entry.enabled())) {
                continue;
            }

            int categoryMask = event.getCategory().getMask();
            if ((EventManager.getInstance().getGlobalCategoryMask() & categoryMask) != 0) {
                deck.addLast(candidateId);
                continue;
            }

            int customTicks = event.getCustomDurationTicks(server);
            int durationTicks;
            if (customTicks > 0) {
                durationTicks = customTicks;
            } else {
                int minSec = entry != null ? entry.minDurationSeconds() : 30;
                int maxSec = entry != null ? entry.maxDurationSeconds() : 60;
                durationTicks = (minSec + random.nextInt(Math.max(1, maxSec - minSec + 1))) * 20;
            }

            if (event.isGlobal()) {
                boolean started = EventManager.getInstance().startGlobalEvent(server, candidateId, durationTicks);
                if (started) {
                    MerryEvents.LOGGER.info("[ChaosDirector] Silently ignited global event '{}' for {} ticks",
                            candidateId, durationTicks);
                    return true;
                }
            } else {
                List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
                if (!players.isEmpty()) {
                    ServerPlayerEntity target = players.get(random.nextInt(players.size()));
                    boolean started = EventManager.getInstance().startPlayerEvent(server, target, candidateId,
                            durationTicks);
                    if (started) {
                        MerryEvents.LOGGER.info("[ChaosDirector] Silently ignited player event '{}' on {} for {} ticks",
                                candidateId, target.getName().getString(), durationTicks);
                        return true;
                    }
                }
            }

            if (EventManager.getInstance().isCategoryOccupiedGlobally(event.getCategory())) {
                deck.addLast(candidateId);
                continue;
            }
        }

        return false;
    }

    /**
     * Replenishes and shuffles deck tickets based on configured event weights.
     */
    public void refillDeck() {
        deck.clear();
        MerryConfig config = ConfigManager.get();
        List<Identifier> pool = new ArrayList<>();

        for (Identifier id : EventManager.getInstance().getRegisteredEventIds()) {
            EventConfigEntry entry = config.getEvents().get(id.toString());
            if (entry == null || entry.enabled()) {
                int weight = entry != null ? entry.weight() : 1;
                for (int w = 0; w < weight; w++) {
                    pool.add(id);
                }
            }
        }

        Collections.shuffle(pool);
        deck.addAll(pool);
        MerryEvents.LOGGER.info("[ChaosDirector] Shuffle bag replenished with {} event tickets.", deck.size());
    }
}