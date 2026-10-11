package com.merry.merryevents.engine;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-level persistence layer saving active events and director state across
 * server restarts.
 */
public final class EventPersistentState extends PersistentState {
    public static final String KEY = "merryevents_state";

    public static final PersistentState.Type<EventPersistentState> TYPE = new PersistentState.Type<>(
            EventPersistentState::new,
            EventPersistentState::readNbt,
            null);

    final Map<Identifier, SavedEventData> globalEvents = new HashMap<>();
    final Map<UUID, Map<Identifier, SavedEventData>> playerEvents = new HashMap<>();
    final Deque<Identifier> directorDeck = new ArrayDeque<>();
    int directorCountdown = -1;

    public EventPersistentState() {
    }

    /**
     * Deserializes active chaos events and director deck from NBT storage.
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
            Identifier id = Identifier.of(tag.getString("id"));
            int remainingTicks = tag.getInt("remainingTicks");
            state.globalEvents.put(id, new SavedEventData(id, remainingTicks));
        }

        NbtList playerList = nbt.getList("playerEvents", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < playerList.size(); i++) {
            NbtCompound playerTag = playerList.getCompound(i);
            UUID uuid = playerTag.getUuid("uuid");
            NbtList pEventsList = playerTag.getList("events", NbtElement.COMPOUND_TYPE);
            Map<Identifier, SavedEventData> map = new HashMap<>();

            for (int j = 0; j < pEventsList.size(); j++) {
                NbtCompound tag = pEventsList.getCompound(j);
                Identifier id = Identifier.of(tag.getString("id"));
                int remainingTicks = tag.getInt("remainingTicks");
                map.put(id, new SavedEventData(id, remainingTicks));
            }
            state.playerEvents.put(uuid, map);
        }

        if (nbt.contains("directorCountdown", NbtElement.INT_TYPE)) {
            state.directorCountdown = nbt.getInt("directorCountdown");
        }

        NbtList deckList = nbt.getList("directorDeck", NbtElement.STRING_TYPE);
        for (int i = 0; i < deckList.size(); i++) {
            state.directorDeck.add(Identifier.of(deckList.getString(i)));
        }

        return state;
    }

    /**
     * Serializes active events and director state into target NBT storage.
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
            globalList.add(tag);
        }
        nbt.put("globalEvents", globalList);

        NbtList playerList = new NbtList();
        for (Map.Entry<UUID, Map<Identifier, EventManager.ActiveEventInstance>> entry : manager.getActivePlayerEvents()
                .entrySet()) {
            NbtCompound playerTag = new NbtCompound();
            playerTag.putUuid("uuid", entry.getKey());
            NbtList pEventsList = new NbtList();

            for (Map.Entry<Identifier, EventManager.ActiveEventInstance> pEntry : entry.getValue().entrySet()) {
                NbtCompound tag = new NbtCompound();
                tag.putString("id", pEntry.getKey().toString());
                tag.putInt("remainingTicks", pEntry.getValue().getRemainingTicks());
                pEventsList.add(tag);
            }
            playerTag.put("events", pEventsList);
            playerList.add(playerTag);
        }
        nbt.put("playerEvents", playerList);

        ChaosDirector director = ChaosDirector.getInstance();
        nbt.putInt("directorCountdown", director.getTicksUntilNextEvent());

        NbtList deckList = new NbtList();
        for (Identifier id : director.getDeck()) {
            deckList.add(NbtString.of(id.toString()));
        }
        nbt.put("directorDeck", deckList);

        return nbt;
    }

    public record SavedEventData(Identifier id, int remainingTicks) {
    }
}