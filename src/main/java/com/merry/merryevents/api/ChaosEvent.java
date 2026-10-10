package com.merry.merryevents.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public interface ChaosEvent {
    Identifier getId();

    EventCategory getCategory();

    boolean isGlobal();

    void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context);

    void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context, int remainingTicks);

    void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context);

    default void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, EventContext context) {
    }
}