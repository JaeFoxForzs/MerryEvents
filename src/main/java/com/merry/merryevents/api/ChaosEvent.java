package com.merry.merryevents.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public interface ChaosEvent {
    Identifier getId();

    EventCategory getCategory();

    boolean isGlobal();

    /**
     * Позволяет ивенту динамически рассчитать свою длительность в тиках
     * на основе состояния мира (время суток, погода и т.д.).
     *
     * @return Длительность в тиках, либо -1, если следует использовать значения из конфига.
     */
    default int getCustomDurationTicks(MinecraftServer server) {
        return -1;
    }

    void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context);

    void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context, int remainingTicks);

    void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context);

    default void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, EventContext context) {
    }

    default String getTranslationKey() {
        return "event." + getId().getNamespace() + "." + getId().getPath();
    }

    default Text getDisplayName() {
        return Text.translatable(getTranslationKey());
    }
}