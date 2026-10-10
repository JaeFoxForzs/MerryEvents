package com.merry.merryevents.engine;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-level persistence layer for active chaos events.
 * Manages serialization and restoration of active global and player event
 * instances.
 */
public final class EventPersistentState extends PersistentState {
    public static final String KEY = "merryevents_state";

    public static final PersistentState.Type<EventPersistentState> TYPE = new PersistentState.Type<>(
            EventPersistentState::new,
            EventPersistentState::readNbt,
            null);

    final Map<Identifier, SavedEventData> globalEvents = new HashMap<>();
    final Map<UUID, Map<Identifier, SavedEventData>> playerEvents = new HashMap<>();

    public EventPersistentState() {
    }

    /**
     * Deserializes stored chaos events from world NBT storage.
     *
     * @param nbt        Root compound tag containing saved event data
     * @param registries Dynamic registry lookup context
     * @return Populated persistent state instance
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

        return state;
    }

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

        return nbt;
    }

    public record SavedEventData(Identifier id, int remainingTicks) {
    }
}