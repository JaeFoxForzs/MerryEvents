package com.merry.merryevents.event.inventory;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class DiamondRushEvent implements ChaosEvent {
    public static final String FIRST_DISCOVERY_KEY = "first_discovery_claimed";

    @Override
    public Identifier getId() {
        return EventIds.DIAMOND_RUSH;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.INVENTORY;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Intercepts block break results to process transmutation mechanics locally.
     *
     * @param world  Server world where block destruction occurred
     * @param player Breaking player
     * @param pos    Destroyed block position
     * @param state  State of broken block
     */
    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!EventManager.getInstance().isPlayerEventActive(player.getUuid(), EventIds.DIAMOND_RUSH)) {
            return;
        }

        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK)) {
            EventContext context = EventManager.getInstance().getPlayerEventContext(player.getUuid(), EventIds.DIAMOND_RUSH);
            boolean isFirst = context != null && context.setFlag(FIRST_DISCOVERY_KEY);

            if (isFirst || world.getRandom().nextFloat() < 0.20F) {
                ItemEntity drop = new ItemEntity(
                        world,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        new ItemStack(Items.DIAMOND));
                world.spawnEntity(drop);

                world.spawnParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        pos.getX() + 0.5,
                        pos.getY() + 0.5,
                        pos.getZ() + 0.5,
                        18,
                        0.35,
                        0.35,
                        0.35,
                        0.05);

                world.playSound(
                        null,
                        pos,
                        SoundEvents.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.PLAYERS,
                        0.75F,
                        1.6F);

                MerryEvents.LOGGER.info("[DiamondRushEvent] Diamond drop triggered for player {} at pos {}",
                        player.getName().getString(), pos);
            }
        }
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[DiamondRushEvent] Diamond transmutation granted to player {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[DiamondRushEvent] Diamond transmutation revoked for player {}",
                    targetPlayer.getName().getString());
        }
    }
}