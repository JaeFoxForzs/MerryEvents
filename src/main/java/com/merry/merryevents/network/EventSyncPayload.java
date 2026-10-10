package com.merry.merryevents.network;

import com.merry.merryevents.MerryEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EventSyncPayload(
    Identifier eventId,
    String categoryName,
    int durationTicks,
    boolean active,
    boolean isGlobal
) implements CustomPayload {

    public static final CustomPayload.Id<EventSyncPayload> ID = new CustomPayload.Id<>(MerryEvents.id("event_sync"));

    public static final PacketCodec<RegistryByteBuf, EventSyncPayload> CODEC = PacketCodec.tuple(
        Identifier.PACKET_CODEC, EventSyncPayload::eventId,
        PacketCodecs.STRING, EventSyncPayload::categoryName,
        PacketCodecs.VAR_INT, EventSyncPayload::durationTicks,
        PacketCodecs.BOOL, EventSyncPayload::active,
        PacketCodecs.BOOL, EventSyncPayload::isGlobal,
        EventSyncPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}