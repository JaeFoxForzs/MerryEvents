package com.merry.merryevents.network;

import com.merry.merryevents.MerryEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server-to-client payload syncing active forbidden block event status and
 * target player name.
 *
 * @param targetPlayerName Username of the player currently suffering from the
 *                         restriction
 * @param active           True if the event is actively inflicting damage on
 *                         the target
 */
public record ForbiddenBlocksPayload(
        String targetPlayerName,
        boolean active) implements CustomPayload {

    public static final CustomPayload.Id<ForbiddenBlocksPayload> ID = new CustomPayload.Id<>(
            MerryEvents.id("forbidden_blocks"));

    public static final PacketCodec<RegistryByteBuf, ForbiddenBlocksPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ForbiddenBlocksPayload::targetPlayerName,
            PacketCodecs.BOOL, ForbiddenBlocksPayload::active,
            ForbiddenBlocksPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}