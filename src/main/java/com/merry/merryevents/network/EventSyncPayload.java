package com.merry.merryevents.network;

import com.merry.merryevents.MerryEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Bidirectional network payload synchronizing active chaos event lifecycle
 * states between server and clients.
 *
 * @param eventId       Unique identifier of the target event
 * @param categoryName  Architectural category identifier name
 * @param durationTicks Total remaining lifetime in server ticks
 * @param active        Activation flag signaling whether the event started or
 *                      terminated
 * @param isGlobal      True if the event applies globally, false if bound to
 *                      the receiving player
 * @param startSequence Incremental lifecycle sequence enforcing client-side
 *                      idempotency
 */
public record EventSyncPayload(
        Identifier eventId,
        String categoryName,
        int durationTicks,
        boolean active,
        boolean isGlobal,
        int startSequence) implements CustomPayload {

    public static final CustomPayload.Id<EventSyncPayload> ID = new CustomPayload.Id<>(MerryEvents.id("event_sync"));

    public static final PacketCodec<RegistryByteBuf, EventSyncPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, EventSyncPayload::eventId,
            PacketCodecs.STRING, EventSyncPayload::categoryName,
            PacketCodecs.VAR_INT, EventSyncPayload::durationTicks,
            PacketCodecs.BOOL, EventSyncPayload::active,
            PacketCodecs.BOOL, EventSyncPayload::isGlobal,
            PacketCodecs.VAR_INT, EventSyncPayload::startSequence,
            EventSyncPayload::new);

    /**
     * Backward-compatible constructor defaulting startSequence to zero.
     *
     * @param eventId       Target event identifier
     * @param categoryName  Architectural category name
     * @param durationTicks Remaining duration in ticks
     * @param active        Active state flag
     * @param isGlobal      Global distribution scope flag
     */
    public EventSyncPayload(Identifier eventId, String categoryName, int durationTicks, boolean active,
            boolean isGlobal) {
        this(eventId, categoryName, durationTicks, active, isGlobal, 0);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return EventSyncPayload.ID;
    }
}