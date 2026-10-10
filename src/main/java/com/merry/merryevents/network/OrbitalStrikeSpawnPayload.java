package com.merry.merryevents.network;

import com.merry.merryevents.MerryEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record OrbitalStrikeSpawnPayload(
        BlockPos pos,
        int warmupTicks
) implements CustomPayload {
    public static final CustomPayload.Id<OrbitalStrikeSpawnPayload> ID = new CustomPayload.Id<>(MerryEvents.id("orbital_strike_spawn"));

    public static final PacketCodec<RegistryByteBuf, OrbitalStrikeSpawnPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, OrbitalStrikeSpawnPayload::pos,
            PacketCodecs.VAR_INT, OrbitalStrikeSpawnPayload::warmupTicks,
            OrbitalStrikeSpawnPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}