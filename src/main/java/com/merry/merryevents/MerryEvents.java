package com.merry.merryevents;

import com.merry.merryevents.command.MerryEventsCommand;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.engine.ChaosDirector;
import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.engine.EventPersistentState;
import com.merry.merryevents.event.inventory.DiamondRushEvent;
import com.merry.merryevents.event.mobs.ExplodingMobsEvent;
import com.merry.merryevents.event.world.CobwebNightmareEvent;
import com.merry.merryevents.item.ChaosMemoItem;
import com.merry.merryevents.network.EventSyncPayload;
import com.merry.merryevents.network.ForbiddenBlocksPayload;
import com.merry.merryevents.network.OrbitalStrikeSpawnPayload;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Main mod initializer establishing server-side chaos engine infrastructure,
 * custom data components, network payloads, and global event hooks.
 */
public class MerryEvents implements ModInitializer {
        public static final String MOD_ID = "merryevents";
        public static final Logger LOGGER = LoggerFactory.getLogger(MerryEvents.MOD_ID);

        public static final Identifier LOBOTOMY_SOUND_ID = MerryEvents.id("lobotomy");
        public static SoundEvent LOBOTOMY_SOUND;
        public static ComponentType<List<Identifier>> FORBIDDEN_BLOCKS_COMPONENT;
        public static ComponentType<String> MEMO_OWNER_COMPONENT;
        public static ChaosMemoItem CHAOS_MEMO;

        @Override
        public void onInitialize() {
                MerryEvents.LOGGER.info("[MerryEvents] Initializing 1.21.1 Chaos Engine...");

                MerryEvents.LOBOTOMY_SOUND = Registry.register(
                                Registries.SOUND_EVENT,
                                MerryEvents.LOBOTOMY_SOUND_ID,
                                SoundEvent.of(MerryEvents.LOBOTOMY_SOUND_ID));

                MerryEvents.FORBIDDEN_BLOCKS_COMPONENT = Registry.register(
                                Registries.DATA_COMPONENT_TYPE,
                                Identifier.of(MerryEvents.MOD_ID, "forbidden_blocks"),
                                ComponentType.<List<Identifier>>builder()
                                                .codec(Identifier.CODEC.listOf())
                                                .packetCodec(Identifier.PACKET_CODEC.collect(PacketCodecs.toList()))
                                                .build());

                MerryEvents.MEMO_OWNER_COMPONENT = Registry.register(
                                Registries.DATA_COMPONENT_TYPE,
                                Identifier.of(MerryEvents.MOD_ID, "memo_owner"),
                                ComponentType.<String>builder()
                                                .codec(Codec.STRING)
                                                .packetCodec(PacketCodecs.STRING)
                                                .build());

                MerryEvents.CHAOS_MEMO = Registry.register(
                                Registries.ITEM,
                                Identifier.of(MerryEvents.MOD_ID, ChaosMemoItem.ID),
                                new ChaosMemoItem(new Item.Settings().maxCount(1).fireproof()));

                ConfigManager.load();
                EventManager.getInstance().registerDefaultEvents();

                PayloadTypeRegistry.playS2C().register(EventSyncPayload.ID, EventSyncPayload.CODEC);
                PayloadTypeRegistry.playS2C().register(OrbitalStrikeSpawnPayload.ID, OrbitalStrikeSpawnPayload.CODEC);
                PayloadTypeRegistry.playS2C().register(ForbiddenBlocksPayload.ID, ForbiddenBlocksPayload.CODEC);

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

                ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
                        EventManager.getInstance().resetRuntimeState();
                        ChaosDirector.getInstance().resetState();
                });

                CommandRegistrationCallback.EVENT
                                .register((dispatcher, registryAccess, environment) -> MerryEventsCommand
                                                .register(dispatcher));

                ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
                        if (entity instanceof LivingEntity living) {
                                ExplodingMobsEvent.onEntityDeath(living, damageSource);
                        }
                });

                PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
                        if (world instanceof ServerWorld serverWorld
                                        && player instanceof ServerPlayerEntity serverPlayer) {
                                DiamondRushEvent.onBlockBreak(serverWorld, serverPlayer, pos, state);
                                CobwebNightmareEvent.onBlockBreak(serverWorld, serverPlayer, pos, state);
                        }
                });

                MerryEvents.LOGGER.info("[MerryEvents] Chaos Engine core initialization complete.");
        }

        public static Identifier id(String path) {
                return Identifier.of(MerryEvents.MOD_ID, path);
        }
}