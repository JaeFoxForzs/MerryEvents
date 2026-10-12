// File: src/main/java/com/merry/merryevents/event/status/FireSkinEvent.java

package com.merry.merryevents.event.status;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.util.SpatialTargeting;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TntBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Player-specific thermal aura event belonging to EventCategory.STATUS.
 * Enshrouds the player in perpetual non-lethal flames that ignite all intersecting
 * entities, colliding wall surfaces, ceilings, floors, and combustible blocks upon contact.
 */
public final class FireSkinEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("fire_skin");

    @Override
    public Identifier getId() {
        return FireSkinEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.STATUS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Evaluates whether fire skin is currently active for the target player.
     *
     * @param player Target server player entity
     * @return True if fire skin thermal contact is active
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), FireSkinEvent.ID);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 80, 0, false, false, true));
            targetPlayer.setOnFireFor(5.0F);
            targetPlayer.playSoundToPlayer(SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2F, 0.8F);
            MerryEvents.LOGGER.info("[FireSkinEvent] Thermal ignition aura activated for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        // 1. Maintain self fire resistance and visible player flame aura
        StatusEffectInstance res = targetPlayer.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        if (res == null || res.getDuration() <= 20) {
            targetPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 80, 0, false, false, true));
        }
        targetPlayer.setOnFireFor(4.0F);

        ServerWorld world = targetPlayer.getServerWorld();

        // 2. Ambient flame and spark particles around the player
        if (remainingTicks % 2 == 0) {
            world.spawnParticles(ParticleTypes.FLAME,
                    targetPlayer.getX(), targetPlayer.getY() + 0.8, targetPlayer.getZ(),
                    3, 0.25, 0.4, 0.25, 0.03);
            world.spawnParticles(ParticleTypes.SMALL_FLAME,
                    targetPlayer.getX(), targetPlayer.getY() + 0.2, targetPlayer.getZ(),
                    2, 0.2, 0.1, 0.2, 0.02);
        }

        // 3. Process thermal touch interactions with living entities and colliding blocks
        igniteTouchingEntities(world, targetPlayer);
        igniteTouchingBlocks(world, targetPlayer);
    }

    /**
     * Ignites and burns all living entities intersecting or brushing against the player's body.
     */
    private static void igniteTouchingEntities(ServerWorld world, ServerPlayerEntity player) {
        Box entityBox = player.getBoundingBox().expand(0.35, 0.15, 0.35);
        List<LivingEntity> touching = world.getEntitiesByClass(LivingEntity.class, entityBox,
                living -> living.isAlive() && living != player && !living.isFireImmune());

        for (int i = 0; i < touching.size(); i++) {
            LivingEntity victim = touching.get(i);
            if (victim instanceof ServerPlayerEntity otherPlayer && SpatialTargeting.isHazardImmune(otherPlayer)) {
                continue;
            }
            victim.setOnFireFor(5.0F);
            victim.damage(world.getDamageSources().inFire(), 1.0F);
            world.spawnParticles(ParticleTypes.LAVA,
                    victim.getX(), victim.getY() + victim.getHeight() * 0.5, victim.getZ(),
                    2, 0.2, 0.2, 0.2, 0.0);
        }
    }

    /**
     * Evaluates all solid blocks, walls, ceilings, and obstacles the player is physically
     * contacting, igniting the touched faces immediately.
     */
    private static void igniteTouchingBlocks(ServerWorld world, ServerPlayerEntity player) {
        // Expand bounding box slightly (12cm horizontal, 5cm vertical) to capture walls and surfaces in direct contact
        Box touchBox = player.getBoundingBox().expand(0.12, 0.05, 0.12);

        int minX = MathHelper.floor(touchBox.minX);
        int maxX = MathHelper.floor(touchBox.maxX);
        int minY = MathHelper.floor(touchBox.minY);
        int maxY = MathHelper.floor(touchBox.maxY);
        int minZ = MathHelper.floor(touchBox.minZ);
        int maxZ = MathHelper.floor(touchBox.maxZ);

        BlockPos.Mutable touchedPos = new BlockPos.Mutable();
        BlockPos.Mutable airPos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    touchedPos.set(x, y, z);
                    BlockState state = world.getBlockState(touchedPos);

                    // Skip already burning fire blocks
                    if (state.getBlock() instanceof AbstractFireBlock) {
                        continue;
                    }

                    // Interactive touch reactions: TNT primes on contact
                    if (state.isOf(Blocks.TNT)) {
                        TntBlock.primeTnt(world, touchedPos);
                        world.removeBlock(touchedPos, false);
                        continue;
                    }

                    // Unlit campfires and candles light up on contact
                    if (state.contains(Properties.LIT) && !state.get(Properties.LIT)) {
                        world.setBlockState(touchedPos, state.with(Properties.LIT, true), Block.NOTIFY_ALL);
                        world.playSound(null, touchedPos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        world.spawnParticles(ParticleTypes.FLAME, touchedPos.getX() + 0.5, touchedPos.getY() + 0.5, touchedPos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.02);
                        continue;
                    }

                    // Combustible cobwebs burn away instantly into fire
                    if (state.isOf(Blocks.COBWEB)) {
                        world.setBlockState(touchedPos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                        world.playSound(null, touchedPos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.6F, 1.2F);
                        world.spawnParticles(ParticleTypes.FLAME, touchedPos.getX() + 0.5, touchedPos.getY() + 0.5, touchedPos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.04);
                        continue;
                    }

                    // If the touched block is solid (wall, ceiling, ground, tree, pillar):
                    if (!state.isAir()) {
                        for (Direction dir : Direction.values()) {
                            airPos.set(
                                    touchedPos.getX() + dir.getOffsetX(),
                                    touchedPos.getY() + dir.getOffsetY(),
                                    touchedPos.getZ() + dir.getOffsetZ()
                            );

                            // Only ignite the side/face that intersects the player's contact volume (prevents leaking through walls)
                            if (!touchBox.intersects(airPos.getX(), airPos.getY(), airPos.getZ(),
                                    airPos.getX() + 1.0, airPos.getY() + 1.0, airPos.getZ() + 1.0)) {
                                continue;
                            }

                            BlockState airState = world.getBlockState(airPos);

                            // Do not ignite underwater or inside already burning fires
                            if (airState.getBlock() instanceof AbstractFireBlock || !airState.getFluidState().isEmpty()) {
                                continue;
                            }

                            if (airState.isAir() || airState.isReplaceable()) {
                                if (AbstractFireBlock.canPlaceAt(world, airPos, dir)) {
                                    BlockState fireState = AbstractFireBlock.getState(world, airPos);
                                    world.setBlockState(airPos, fireState, Block.NOTIFY_ALL);
                                    world.playSound(null, airPos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.5F, 1.3F);
                                    world.spawnParticles(ParticleTypes.FLAME,
                                            airPos.getX() + 0.5, airPos.getY() + 0.5, airPos.getZ() + 0.5,
                                            4, 0.2, 0.2, 0.2, 0.02);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            targetPlayer.extinguish();
            targetPlayer.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8F, 1.2F);
            MerryEvents.LOGGER.info("[FireSkinEvent] Thermal ignition aura extinguished for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer,
                                EventContext context) {
        newPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 80, 0, false, false, true));
    }
}