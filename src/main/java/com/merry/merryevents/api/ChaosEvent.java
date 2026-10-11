package com.merry.merryevents.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Core contract defining a deterministic chaos event lifecycle.
 * Encapsulates initialization, server tick routines, teardown, and respawn
 * persistence.
 */
public interface ChaosEvent {
    /**
     * Resolves the unique namespaced identifier of the chaos event.
     *
     * @return Unique event identifier
     */
    Identifier getId();

    /**
     * Resolves the architectural conflict resolution category.
     *
     * @return Category used for concurrency checks
     */
    EventCategory getCategory();

    /**
     * Queries whether the event targets the entire server or a specific player
     * instance.
     *
     * @return True if globally broadcast, false if bound to individual players
     */
    boolean isGlobal();

    /**
     * Computes dynamic event duration in server ticks based on active world
     * environment.
     *
     * @param server Active MinecraftServer instance
     * @return Duration in ticks, or -1 to defer to configuration parameters
     */
    default int getCustomDurationTicks(MinecraftServer server) {
        return -1;
    }

    /**
     * Executes server-side ignition logic and sets up contextual state trackers.
     *
     * @param server       Authoritative server instance
     * @param targetPlayer Targeted player for personal events, or null for global
     *                     events
     * @param context      Isolated event execution state container
     */
    void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context);

    /**
     * Executes per-tick server logic and updates contextual state machines.
     *
     * @param server         Authoritative server instance
     * @param targetPlayer   Targeted player for personal events, or null for global
     *                       events
     * @param context        Isolated event execution state container
     * @param remainingTicks Number of ticks remaining before automatic termination
     */
    void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks);

    /**
     * Executes authoritative teardown routines and cleans up tracking state.
     *
     * @param server       Authoritative server instance
     * @param targetPlayer Targeted player for personal events, or null for global
     *                     events
     * @param context      Isolated event execution state container
     */
    void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context);

    /**
     * Re-applies personal event modifiers and synchronizes state upon player death
     * and respawn.
     *
     * @param server    Authoritative server instance
     * @param oldPlayer Dead player instance prior to respawn
     * @param newPlayer Fresh player instance newly placed in the world
     * @param context   Isolated event execution state container
     */
    default void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer,
            EventContext context) {
    }

    /**
     * Resolves the localization translation key for this event.
     *
     * @return Namespaced translation key
     */
    default String getTranslationKey() {
        return "event." + getId().getNamespace() + "." + getId().getPath();
    }

    /**
     * Resolves the translatable display text representation.
     *
     * @return Localized display text
     */
    default Text getDisplayName() {
        return Text.translatable(getTranslationKey());
    }
}