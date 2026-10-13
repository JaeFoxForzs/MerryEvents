package com.merry.merryevents.event.status;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.util.SpatialTargeting;
import com.merry.merryevents.util.WeatherUtil;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TntBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
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
 * Enshrouds the player in non-lethal flames that ignite intersecting entities
 * and blocks.
 * Automatically extinguishes and suspends ignition while submerged or in rain
 * without cancelling the event.
 * Intensifies damage, duration, and range by 1.5x when active in Nether
 * dimensions.
 */
public final class FireSkinEvent implements ChaosEvent {
    private static final String WAS_DOUSED_KEY = "was_doused";

    @Override
    public Identifier getId() {
        return EventIds.FIRE_SKIN;
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
     * Evaluates whether fire skin is currently registered and active for the target
     * player.
     *
     * @param player Target server player entity
     * @return True if fire skin event is active
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), EventIds.FIRE_SKIN);
    }

    /**
     * Evaluates whether thermal combustion is actively burning on the player (not
     * extinguished by water or rain).
     *
     * @param player Target server player entity
     * @return True if combustion aura is physically active
     */
    public static boolean isCombustionActive(ServerPlayerEntity player) {
        return FireSkinEvent.isPlayerActive(player) && !FireSkinEvent.isPlayerDoused(player);
    }

    /**
     * Checks if the player is currently exposed to water, submersion, or
     * precipitation.
     *
     * @param player Target server player entity
     * @return True if player is quenched by liquid or rainfall
     */
    public static boolean isPlayerDoused(ServerPlayerEntity player) {
        if (player.isTouchingWater() || player.isSubmergedIn(FluidTags.WATER)) {
            return true;
        }
        FluidState fluid = player.getWorld().getFluidState(player.getBlockPos());
        if (!fluid.isEmpty() && fluid.isIn(FluidTags.WATER)) {
            return true;
        }
        return WeatherUtil.isExposedToRain(player.getServerWorld(), player.getBlockPos(), player);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            context.set(FireSkinEvent.WAS_DOUSED_KEY, false);
            if (!FireSkinEvent.isPlayerDoused(targetPlayer)) {
                targetPlayer.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 80, 0, false, false, true));
                targetPlayer.setOnFireFor(5.0F);
            }
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

        ServerWorld world = targetPlayer.getServerWorld();
        boolean doused = FireSkinEvent.isPlayerDoused(targetPlayer);
        boolean wasDoused = context.getBoolean(FireSkinEvent.WAS_DOUSED_KEY, false);

        if (doused) {
            if (!wasDoused) {
                targetPlayer.extinguish();
                world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8F, 1.5F);
                world.spawnParticles(ParticleTypes.POOF,
                        targetPlayer.getX(), targetPlayer.getY() + 0.5, targetPlayer.getZ(),
                        8, 0.3, 0.3, 0.3, 0.05);
                context.set(FireSkinEvent.WAS_DOUSED_KEY, true);
            }

            if (remainingTicks % 10 == 0) {
                world.spawnParticles(ParticleTypes.SMOKE,
                        targetPlayer.getX(), targetPlayer.getY() + 0.8, targetPlayer.getZ(),
                        2, 0.2, 0.3, 0.2, 0.01);
            }
            return;
        }

        if (wasDoused) {
            world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            context.set(FireSkinEvent.WAS_DOUSED_KEY, false);
        }

        boolean isNether = world.getDimension().ultrawarm();
        float multiplier = isNether ? 1.5F : 1.0F;

        StatusEffectInstance res = targetPlayer.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        if (res == null || res.getDuration() <= 20) {
            targetPlayer.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 80, 0, false, false, true));
        }
        targetPlayer.setOnFireFor(4.0F * multiplier);

        if (remainingTicks % 2 == 0) {
            int flameCount = isNether ? 5 : 3;
            world.spawnParticles(ParticleTypes.FLAME,
                    targetPlayer.getX(), targetPlayer.getY() + 0.8, targetPlayer.getZ(),
                    flameCount, 0.25 * multiplier, 0.4 * multiplier, 0.25 * multiplier, 0.03);
            world.spawnParticles(ParticleTypes.SMALL_FLAME,
                    targetPlayer.getX(), targetPlayer.getY() + 0.2, targetPlayer.getZ(),
                    isNether ? 4 : 2, 0.2, 0.1, 0.2, 0.02);
            if (isNether && remainingTicks % 4 == 0) {
                world.spawnParticles(ParticleTypes.LAVA,
                        targetPlayer.getX(), targetPlayer.getY() + 0.5, targetPlayer.getZ(),
                        1, 0.2, 0.2, 0.2, 0.0);
            }
        }

        FireSkinEvent.igniteTouchingEntities(world, targetPlayer, multiplier);
        FireSkinEvent.igniteTouchingBlocks(world, targetPlayer, multiplier);
    }

    private static void igniteTouchingEntities(ServerWorld world, ServerPlayerEntity player, float multiplier) {
        double expandXZ = 0.35 * multiplier;
        Box entityBox = player.getBoundingBox().expand(expandXZ, 0.15, expandXZ);
        List<LivingEntity> touching = world.getEntitiesByClass(LivingEntity.class, entityBox,
                living -> living.isAlive() && living != player && !living.isFireImmune());

        float damage = 1.0F * multiplier;
        float fireSeconds = 5.0F * multiplier;

        for (int i = 0; i < touching.size(); i++) {
            LivingEntity victim = touching.get(i);
            if (victim instanceof ServerPlayerEntity otherPlayer && SpatialTargeting.isHazardImmune(otherPlayer)) {
                continue;
            }
            victim.setOnFireFor(fireSeconds);
            victim.damage(world.getDamageSources().inFire(), damage);
            world.spawnParticles(ParticleTypes.LAVA,
                    victim.getX(), victim.getY() + victim.getHeight() * 0.5, victim.getZ(),
                    2, 0.2, 0.2, 0.2, 0.0);
        }
    }

    private static void igniteTouchingBlocks(ServerWorld world, ServerPlayerEntity player, float multiplier) {
        double expandXZ = 0.12 * multiplier;
        Box touchBox = player.getBoundingBox().expand(expandXZ, 0.05, expandXZ);

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

                    if (state.getBlock() instanceof AbstractFireBlock) {
                        continue;
                    }

                    if (state.isOf(Blocks.TNT)) {
                        TntBlock.primeTnt(world, touchedPos);
                        world.removeBlock(touchedPos, false);
                        continue;
                    }

                    if (state.contains(Properties.LIT) && !state.get(Properties.LIT)) {
                        world.setBlockState(touchedPos, state.with(Properties.LIT, true), Block.NOTIFY_ALL);
                        world.playSound(null, touchedPos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS,
                                1.0F, 1.0F);
                        world.spawnParticles(ParticleTypes.FLAME, touchedPos.getX() + 0.5, touchedPos.getY() + 0.5,
                                touchedPos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.02);
                        continue;
                    }

                    if (state.isOf(Blocks.COBWEB)) {
                        world.setBlockState(touchedPos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                        world.playSound(null, touchedPos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.6F,
                                1.2F);
                        world.spawnParticles(ParticleTypes.FLAME, touchedPos.getX() + 0.5, touchedPos.getY() + 0.5,
                                touchedPos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.04);
                        continue;
                    }

                    if (!state.isAir()) {
                        for (Direction dir : Direction.values()) {
                            airPos.set(
                                    touchedPos.getX() + dir.getOffsetX(),
                                    touchedPos.getY() + dir.getOffsetY(),
                                    touchedPos.getZ() + dir.getOffsetZ());

                            if (!touchBox.intersects(airPos.getX(), airPos.getY(), airPos.getZ(),
                                    airPos.getX() + 1.0, airPos.getY() + 1.0, airPos.getZ() + 1.0)) {
                                continue;
                            }

                            BlockState airState = world.getBlockState(airPos);

                            if (airState.getBlock() instanceof AbstractFireBlock
                                    || !airState.getFluidState().isEmpty()) {
                                continue;
                            }

                            if (airState.isAir() || airState.isReplaceable()) {
                                if (AbstractFireBlock.canPlaceAt(world, airPos, dir)) {
                                    BlockState fireState = AbstractFireBlock.getState(world, airPos);
                                    world.setBlockState(airPos, fireState, Block.NOTIFY_ALL);
                                    world.playSound(null, airPos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS,
                                            0.5F, 1.3F);
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
        if (!FireSkinEvent.isPlayerDoused(newPlayer)) {
            newPlayer.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 80, 0, false, false, true));
            newPlayer.setOnFireFor(5.0F);
        }
    }
}