package com.merry.merryevents;

import com.merry.merryevents.api.EventContext;
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
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
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
                (handler, sender, server) -> EventManager.getInstance().onPlayerJoin(server, handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> EventManager.getInstance().onPlayerDisconnect(server, handler.getPlayer()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> EventManager.getInstance()
                .onPlayerRespawn(newPlayer.getServer(), oldPlayer, newPlayer));

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
                .register((player, origin, destination) -> EventManager.getInstance().syncAllToPlayer(player));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> EventManager.getInstance().onServerStopping(server));

        CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> MerryEventsCommand.register(dispatcher));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!entity.getWorld().isClient()
                    && EventManager.getInstance().isGlobalEventActive(ExplodingMobsEvent.ID)
                    && entity instanceof LivingEntity livingEntity
                    && (livingEntity instanceof Monster
                            || livingEntity.getType().getSpawnGroup() == SpawnGroup.MONSTER)) {

                if (damageSource.isOf(DamageTypes.EXPLOSION) || damageSource.isOf(DamageTypes.PLAYER_EXPLOSION)) {
                    return;
                }

                float maxHp = livingEntity.getMaxHealth();
                double attackDamage = 0.0;
                var attackAttr = livingEntity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                if (attackAttr != null) {
                    attackDamage = attackAttr.getValue();
                }

                float power = (float) MathHelper.clamp(1.0 + (maxHp * 0.04) + (attackDamage * 0.1), 0.8, 4.5);

                livingEntity.getWorld().createExplosion(
                        null,
                        livingEntity.getX(),
                        livingEntity.getY(),
                        livingEntity.getZ(),
                        power,
                        World.ExplosionSourceType.MOB);
                LOGGER.debug("[MerryEvents] Hostile entity {} (HP={}, Attack={}) detonated with power {}",
                        livingEntity.getName().getString(), maxHp, attackDamage, power);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld
                    && EventManager.getInstance().isPlayerEventActive(player.getUuid(), DiamondRushEvent.ID)) {
                if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK)) {
                    EventContext context = EventManager.getInstance().getPlayerEventContext(player.getUuid(),
                            DiamondRushEvent.ID);
                    boolean isFirst = context != null && context.setFlag(DiamondRushEvent.FIRST_DISCOVERY_KEY);

                    if (isFirst || serverWorld.getRandom().nextFloat() < 0.20F) {
                        ItemEntity drop = new ItemEntity(
                                serverWorld,
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5,
                                new ItemStack(Items.DIAMOND));
                        serverWorld.spawnEntity(drop);

                        serverWorld.spawnParticles(
                                ParticleTypes.ELECTRIC_SPARK,
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5,
                                18,
                                0.35,
                                0.35,
                                0.35,
                                0.05);
                        serverWorld.playSound(
                                null,
                                pos,
                                SoundEvents.ENTITY_PLAYER_LEVELUP,
                                SoundCategory.PLAYERS,
                                0.75F,
                                1.6F);

                        LOGGER.info("[MerryEvents] Diamond drop triggered for player {} at pos {}",
                                player.getName().getString(), pos);
                    }
                }
            }
        });

        LOGGER.info("[MerryEvents] Chaos Engine core initialization complete.");
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}