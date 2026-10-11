package com.merry.merryevents;

import com.merry.merryevents.command.MerryEventsCommand;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.engine.EventPersistentState;
import com.merry.merryevents.event.inventory.DiamondRushEvent;
import com.merry.merryevents.event.mobs.ExplodingMobsEvent;
import com.merry.merryevents.network.EventSyncPayload;
import com.merry.merryevents.network.OrbitalStrikeSpawnPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerryEvents implements ModInitializer {
        public static final String MOD_ID = "merryevents";
        public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

        @Override
        public void onInitialize() {
                LOGGER.info("[MerryEvents] Initializing 1.21.1 Chaos Engine...");

                ConfigManager.load();
                PayloadTypeRegistry.playS2C().register(EventSyncPayload.ID, EventSyncPayload.CODEC);
                PayloadTypeRegistry.playS2C().register(OrbitalStrikeSpawnPayload.ID, OrbitalStrikeSpawnPayload.CODEC);

                ServerWorldEvents.LOAD.register((server, world) -> {
                        if (world.getRegistryKey() == World.OVERWORLD) {
                                EventPersistentState state = world.getPersistentStateManager().getOrCreate(
                                                EventPersistentState.TYPE,
                                                EventPersistentState.KEY);
                                EventManager.getInstance().initPersistentState(server, state);
                        }
                });

                ServerTickEvents.END_SERVER_TICK.register(server -> EventManager.getInstance().tick(server));

                ServerPlayConnectionEvents.JOIN.register(
                                (handler, sender, server) -> EventManager.getInstance().onPlayerJoin(server,
                                                handler.getPlayer()));

                ServerPlayConnectionEvents.DISCONNECT.register(
                                (handler, server) -> EventManager.getInstance().onPlayerDisconnect(server,
                                                handler.getPlayer()));

                ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> EventManager.getInstance()
                                .onPlayerRespawn(newPlayer.getServer(), oldPlayer, newPlayer));

                ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
                                .register((player, origin, destination) -> EventManager.getInstance()
                                                .syncAllToPlayer(player));

                ServerLifecycleEvents.SERVER_STOPPING
                                .register(server -> EventManager.getInstance().onServerStopping(server));

                CommandRegistrationCallback.EVENT
                                .register((dispatcher, registryAccess, environment) -> MerryEventsCommand
                                                .register(dispatcher));

                ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
                        if (entity instanceof LivingEntity living) {
                                ExplodingMobsEvent.onEntityDeath(living, damageSource);
                        }
                });

                PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
                        if (world instanceof ServerWorld serverWorld) {
                                DiamondRushEvent.onBlockBreak(serverWorld,
                                                (net.minecraft.server.network.ServerPlayerEntity) player, pos, state);
                        }
                });

                LOGGER.info("[MerryEvents] Chaos Engine core initialization complete.");
        }

        public static Identifier id(String path) {
                return Identifier.of(MOD_ID, path);
        }
}