package com.merry.merryevents.event.weather;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.jetbrains.annotations.Nullable;

public final class AcidRainEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("acid_rain");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WEATHER;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER
                .info("[AcidRainEvent] Atmospheric corruption initiated. Acid rain active on all dimensions.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isCreative() || player.isSpectator() || player.isInvulnerable()) {
                continue;
            }

            ServerWorld world = player.getServerWorld();
            BlockPos pos = player.getBlockPos();

            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()),
                    ChunkSectionPos.getSectionCoord(pos.getZ()))) {
                continue;
            }

            if (world.hasRain(pos)) {
                player.damage(world.getDamageSources().magic(), 1.0F);
                MerryEvents.LOGGER.debug("[AcidRainEvent] Corrosive burn applied to {}", player.getName().getString());
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[AcidRainEvent] Atmospheric conditions normalized.");
    }
}