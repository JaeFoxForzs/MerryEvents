package com.merry.merryevents.engine;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.EventCategory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-level persistence layer saving active events, execution contexts,
 * sequence counters, scheduler history, and dual director state across server
 * restarts.
 * Enforces a 14-day time-to-live policy for disconnected player countdowns.
 */
public final class EventPersistentState extends PersistentState {
    public static final String KEY = "merryevents_state";
    public static final long PLAYER_TTL_MILLIS = 14L * 24L * 60L * 60L * 1000L; // 14 real-world days

    public static final PersistentState.Type<EventPersistentState> TYPE = new PersistentState.Type<>(
            EventPersistentState::new,
            EventPersistentState::readNbt,
            null);

    public final Map<Identifier, SavedEventData> globalEvents = new HashMap<>();
    public final Map<UUID, Map<Identifier, SavedEventData>> playerEvents = new HashMap<>();
    public final Deque<Identifier> globalDeck = new ArrayDeque<>();
    public final Deque<Identifier> personalDeck = new ArrayDeque<>();
    public final Map<UUID, Integer> playerCountdowns = new HashMap<>();
    public final Map<UUID, Long> playerLastSeen = new HashMap<>();
    public final Map<UUID, ChaosDirector.PlayerHistory> playerHistories = new HashMap<>();

    @Nullable
    public Identifier lastGlobalEventId = null;
    @Nullable
    public String lastGlobalCategory = null;

    public int globalCountdown = -1;
    public int nextStartSequence = 1;

    public EventPersistentState() {
    }

    /**
     * Records an authoritative login timestamp for the target player profile.
     *
     * @param playerUuid Target player unique identifier
     */
    public void markPlayerSeen(UUID playerUuid) {
        if (playerUuid != null) {
            this.playerLastSeen.put(playerUuid, System.currentTimeMillis());
            this.markDirty();
        }
    }

    /**
     * Resolves and increments the authoritative event ignition sequence counter.
     *
     * @return Monotonically increasing sequence identifier
     */
    public int incrementStartSequence() {
        int seq = this.nextStartSequence++;
        this.markDirty();
        return seq;
    }

    /**
     * Deserializes active chaos events, director decks, history, and sequence
     * states from NBT storage.
     * Safely ignores malformed or missing identifiers without interrupting world
     * loading.
     *
     * @param nbt        Source NbtCompound
     * @param registries Registry wrapper lookup
     * @return Deserialized state instance
     */
    public static EventPersistentState readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        EventPersistentState state = new EventPersistentState();

        NbtList globalList = nbt.getList("globalEvents", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < globalList.size(); i++) {
            NbtCompound tag = globalList.getCompound(i);
            String rawId = tag.getString("id");
            Identifier id = Identifier.tryParse(rawId);
            if (id == null) {
                MerryEvents.LOGGER.warn("[EventPersistentState] Skipping malformed global event id: '{}'", rawId);
                continue;
            }

            int remainingTicks = tag.getInt("remainingTicks");
            NbtCompound contextTag = tag.getCompound("context");
            state.globalEvents.put(id, new SavedEventData(id, remainingTicks, contextTag));
        }

        NbtList playerList = nbt.getList("playerEvents", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < playerList.size(); i++) {
            NbtCompound playerTag = playerList.getCompound(i);
            if (!playerTag.containsUuid("uuid")) {
                continue;
            }
            UUID uuid = playerTag.getUuid("uuid");
            NbtList pEventsList = playerTag.getList("events", NbtElement.COMPOUND_TYPE);
            Map<Identifier, SavedEventData> map = new HashMap<>();

            for (int j = 0; j < pEventsList.size(); j++) {
                NbtCompound tag = pEventsList.getCompound(j);
                String rawId = tag.getString("id");
                Identifier id = Identifier.tryParse(rawId);
                if (id == null) {
                    MerryEvents.LOGGER.warn("[EventPersistentState] Skipping malformed player event id: '{}'", rawId);
                    continue;
                }

                int remainingTicks = tag.getInt("remainingTicks");
                NbtCompound contextTag = tag.getCompound("context");
                map.put(id, new SavedEventData(id, remainingTicks, contextTag));
            }
            state.playerEvents.put(uuid, map);
        }

        if (nbt.contains("globalCountdown", NbtElement.INT_TYPE)) {
            state.globalCountdown = nbt.getInt("globalCountdown");
        } else if (nbt.contains("directorCountdown", NbtElement.INT_TYPE)) {
            state.globalCountdown = nbt.getInt("directorCountdown");
        }

        if (nbt.contains("nextStartSequence", NbtElement.INT_TYPE)) {
            state.nextStartSequence = Math.max(1, nbt.getInt("nextStartSequence"));
        }

        if (nbt.contains("lastGlobalEventId", NbtElement.STRING_TYPE)) {
            state.lastGlobalEventId = Identifier.tryParse(nbt.getString("lastGlobalEventId"));
        }
        if (nbt.contains("lastGlobalCategory", NbtElement.STRING_TYPE)) {
            state.lastGlobalCategory = nbt.getString("lastGlobalCategory");
        }

        NbtList globalDeckList = nbt.getList("globalDeck", NbtElement.STRING_TYPE);
        for (int i = 0; i < globalDeckList.size(); i++) {
            String rawId = globalDeckList.getString(i);
            Identifier id = Identifier.tryParse(rawId);
            if (id != null) {
                state.globalDeck.add(id);
            }
        }

        NbtList personalDeckList = nbt.getList("personalDeck", NbtElement.STRING_TYPE);
        for (int i = 0; i < personalDeckList.size(); i++) {
            String rawId = personalDeckList.getString(i);
            Identifier id = Identifier.tryParse(rawId);
            if (id != null) {
                state.personalDeck.add(id);
            }
        }

        NbtList countdownsList = nbt.getList("playerCountdowns", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < countdownsList.size(); i++) {
            NbtCompound tag = countdownsList.getCompound(i);
            if (tag.containsUuid("uuid")) {
                state.playerCountdowns.put(tag.getUuid("uuid"), tag.getInt("countdown"));
            }
        }

        NbtList lastSeenList = nbt.getList("playerLastSeen", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < lastSeenList.size(); i++) {
            NbtCompound tag = lastSeenList.getCompound(i);
            if (tag.containsUuid("uuid")) {
                state.playerLastSeen.put(tag.getUuid("uuid"), tag.getLong("lastSeen"));
            }
        }

        NbtList historiesList = nbt.getList("playerHistories", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < historiesList.size(); i++) {
            NbtCompound tag = historiesList.getCompound(i);
            if (tag.containsUuid("uuid")) {
                UUID uuid = tag.getUuid("uuid");
                Identifier eventId = tag.contains("eventId", NbtElement.STRING_TYPE)
                        ? Identifier.tryParse(tag.getString("eventId"))
                        : null;
                EventCategory cat = null;
                if (tag.contains("category", NbtElement.STRING_TYPE)) {
                    try {
                        cat = EventCategory.valueOf(tag.getString("category"));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                state.playerHistories.put(uuid, new ChaosDirector.PlayerHistory(eventId, cat));
            }
        }

        return state;
    }

    /**
     * Serializes active events, context states, sequence indices, director history,
     * and decks into NBT storage.
     * Prunes player entries older than 14 days before writing.
     *
     * @param nbt        Destination NbtCompound
     * @param registries Registry wrapper lookup
     * @return Populated NbtCompound
     */
    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        EventManager manager = EventManager.getInstance();

        NbtList globalList = new NbtList();
        for (Map.Entry<Identifier, EventManager.ActiveEventInstance> entry : manager.getActiveGlobalEvents()
                .entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putString("id", entry.getKey().toString());
            tag.putInt("remainingTicks", entry.getValue().getRemainingTicks());
            tag.put("context", entry.getValue().getContext().toNbt());
            globalList.add(tag);
        }
        nbt.put("globalEvents", globalList);

        Map<UUID, Map<Identifier, EventManager.ActiveEventInstance>> allPlayerEvents = new HashMap<>(
                manager.getActivePlayerEvents());
        for (Map.Entry<UUID, Map<Identifier, EventManager.ActiveEventInstance>> dormantEntry : manager
                .getDormantPlayerEvents().entrySet()) {
            allPlayerEvents.computeIfAbsent(dormantEntry.getKey(), k -> new HashMap<>())
                    .putAll(dormantEntry.getValue());
        }

        NbtList playerList = new NbtList();
        for (Map.Entry<UUID, Map<Identifier, EventManager.ActiveEventInstance>> entry : allPlayerEvents.entrySet()) {
            NbtCompound playerTag = new NbtCompound();
            playerTag.putUuid("uuid", entry.getKey());
            NbtList pEventsList = new NbtList();

            for (Map.Entry<Identifier, EventManager.ActiveEventInstance> pEntry : entry.getValue().entrySet()) {
                NbtCompound tag = new NbtCompound();
                tag.putString("id", pEntry.getKey().toString());
                tag.putInt("remainingTicks", pEntry.getValue().getRemainingTicks());
                tag.put("context", pEntry.getValue().getContext().toNbt());
                pEventsList.add(tag);
            }
            playerTag.put("events", pEventsList);
            playerList.add(playerTag);
        }
        nbt.put("playerEvents", playerList);

        ChaosDirector director = ChaosDirector.getInstance();
        nbt.putInt("globalCountdown", director.getTicksUntilNextGlobalEvent());
        nbt.putInt("nextStartSequence", this.nextStartSequence);

        if (director.getLastGlobalEventId() != null) {
            nbt.putString("lastGlobalEventId", director.getLastGlobalEventId().toString());
        }
        if (director.getLastGlobalCategory() != null) {
            nbt.putString("lastGlobalCategory", director.getLastGlobalCategory().name());
        }

        NbtList globalDeckList = new NbtList();
        for (Identifier id : director.getGlobalDeck()) {
            globalDeckList.add(NbtString.of(id.toString()));
        }
        nbt.put("globalDeck", globalDeckList);

        NbtList personalDeckList = new NbtList();
        for (Identifier id : director.getPersonalDeck()) {
            personalDeckList.add(NbtString.of(id.toString()));
        }
        nbt.put("personalDeck", personalDeckList);

        long now = System.currentTimeMillis();
        NbtList countdownsList = new NbtList();
        NbtList lastSeenList = new NbtList();

        for (var entry : director.getPlayerCountdowns().object2IntEntrySet()) {
            UUID uuid = entry.getKey();
            Long lastSeen = this.playerLastSeen.get(uuid);

            if (lastSeen != null && (now - lastSeen) > EventPersistentState.PLAYER_TTL_MILLIS) {
                continue; // Pruned by TTL eviction policy
            }

            NbtCompound tag = new NbtCompound();
            tag.putUuid("uuid", uuid);
            tag.putInt("countdown", entry.getIntValue());
            countdownsList.add(tag);

            if (lastSeen != null) {
                NbtCompound seenTag = new NbtCompound();
                seenTag.putUuid("uuid", uuid);
                seenTag.putLong("lastSeen", lastSeen);
                lastSeenList.add(seenTag);
            }
        }

        nbt.put("playerCountdowns", countdownsList);
        nbt.put("playerLastSeen", lastSeenList);

        NbtList historiesList = new NbtList();
        for (Map.Entry<UUID, ChaosDirector.PlayerHistory> entry : director.getPlayerHistories().entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putUuid("uuid", entry.getKey());
            if (entry.getValue().lastEventId() != null) {
                tag.putString("eventId", entry.getValue().lastEventId().toString());
            }
            if (entry.getValue().lastCategory() != null) {
                tag.putString("category", entry.getValue().lastCategory().name());
            }
            historiesList.add(tag);
        }
        nbt.put("playerHistories", historiesList);

        return nbt;
    }

    public record SavedEventData(Identifier id, int remainingTicks, NbtCompound contextNbt) {
    }
}