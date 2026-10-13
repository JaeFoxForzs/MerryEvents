package com.merry.merryevents.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Core contract defining an authoritative chaos event lifecycle.
 * Encapsulates initialization, resume restoration, server tick routines,
 * pre-flight validation, graceful teardown, and cross-client synchronization
 * hooks.
 */
public interface ChaosEvent {
    /**
     * Resolves the immutable declaration model for this event.
     *
     * @return Event definition metadata
     */
    default EventDefinition getDefinition() {
        return EventDefinition.standard(
                this.getId(),
                this.getCategory(),
                this.isGlobal() ? EventScope.GLOBAL : EventScope.PLAYER,
                600,
                1);
    }

    /**
     * Resolves the unique namespaced identifier of the chaos event.
     *
     * @return Unique event identifier
     */
    default Identifier getId() {
        return this.getDefinition().id();
    }

    /**
     * Resolves the primary architectural conflict resolution category.
     *
     * @return Primary category used for backward compatibility
     */
    default EventCategory getCategory() {
        return this.getDefinition().categories().iterator().next();
    }

    /**
     * Resolves all conflict resolution categories assigned to this event.
     *
     * @return Set of occupied categories
     */
    default Set<EventCategory> getCategories() {
        return this.getDefinition().categories();
    }

    /**
     * Queries whether the event targets the entire server or a specific player
     * instance.
     *
     * @return True if globally broadcast, false if bound to individual players
     */
    default boolean isGlobal() {
        return this.getDefinition().scope() == EventScope.GLOBAL;
    }

    /**
     * Evaluates runtime preconditions before the scheduler attempts ignition.
     * If false, the scheduler bypasses this event without locking category slots.
     *
     * @param server       Authoritative MinecraftServer instance
     * @param targetPlayer Targeted player for personal events, or null for global
     *                     events
     * @return True if environmental conditions satisfy execution preconditions
     */
    default boolean canStart(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer) {
        return true;
    }

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
     * Executes authoritative ignition routines upon initial event start.
     * Invoked strictly once per unique event lifetime.
     *
     * @param server       Authoritative server instance
     * @param targetPlayer Targeted player for personal events, or null for global
     *                     events
     * @param context      Isolated event execution state container
     */
    /**
     * Executes authoritative ignition routines upon initial event start.
     *
     * @param server        Authoritative server instance
     * @param targetPlayer  Targeted player for personal events, or null for global
     *                      events
     * @param context       Isolated event execution state container
     * @param durationTicks Resolved event lifetime in server ticks
     */
    void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int durationTicks);

    /**
     * Restores execution state following server restarts or player reconnection.
     * Invoked instead of {@link #onStartServer} when an already active event is
     * resumed.
     *
     * @param server       Authoritative server instance
     * @param targetPlayer Targeted player for personal events, or null for global
     *                     events
     * @param context      Isolated event execution state container containing
     *                     deserialized data
     */
    default void onResumeServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context) {
    }

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
     * Broadcasts auxiliary event payloads to newly joined or dimension-swapped
     * players.
     *
     * @param server          Authoritative server instance
     * @param receivingPlayer The player receiving world state synchronization
     * @param eventOwner      Target player for personal events, or null for global
     *                        events
     * @param context         Isolated event execution state container
     */
    default void onSyncToPlayer(MinecraftServer server, ServerPlayerEntity receivingPlayer,
            @Nullable ServerPlayerEntity eventOwner, EventContext context) {
    }

    /**
     * Registers event-specific Fabric callbacks, game rules, or network listeners.
     */
    default void registerHooks() {
    }

    /**
     * Resolves the localization translation key for this event.
     *
     * @return Namespaced translation key
     */
    default String getTranslationKey() {
        return "event." + this.getId().getNamespace() + "." + this.getId().getPath();
    }

    /**
     * Resolves the translatable display text representation.
     *
     * @return Localized display text
     */
    default Text getDisplayName() {
        return Text.translatable(this.getTranslationKey());
    }
}