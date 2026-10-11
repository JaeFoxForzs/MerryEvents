package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific architectural hazard event belonging to EventCategory.WORLD.
 * Unpredictably collapses a quota of 64 to 512 terrain blocks within a
 * randomized
 * radius around the player, distributing structural failures smoothly across
 * the lifetime.
 */
public final class FloorHoleEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("floor_hole");
    private static final String REMAINING_BLOCKS_KEY = "remaining_blocks";
    private static final String RADIUS_KEY = "horizontal_radius";
    private static final int MIN_BLOCKS = 64;
    private static final int MAX_BLOCKS = 512;
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 6;

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WORLD;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer == null) {
            return;
        }

        Random random = targetPlayer.getRandom();
        int totalBlocks = MIN_BLOCKS + random.nextInt(MAX_BLOCKS - MIN_BLOCKS + 1);
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);

        context.set(REMAINING_BLOCKS_KEY, totalBlocks);
        context.set(RADIUS_KEY, radius);

        MerryEvents.LOGGER.info(
                "[FloorHoleEvent] Initialized structural sinkhole decay for {} (Quota: {} blocks, Radius: {} blocks)",
                targetPlayer.getName().getString(), totalBlocks, radius);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks <= 0) {
            return;
        }

        int blocksRemaining = context.getInt(REMAINING_BLOCKS_KEY, 0);
        if (blocksRemaining <= 0) {
            return;
        }

        Random random = targetPlayer.getRandom();
        double expectedRate = (double) blocksRemaining / (double) remainingTicks;
        int toBreakCount = (int) expectedRate;
        double fractionalRate = expectedRate - (double) toBreakCount;

        if (random.nextDouble() < fractionalRate) {
            toBreakCount++;
        }

        if (toBreakCount <= 0) {
            return;
        }

        int radius = context.getInt(RADIUS_KEY, 4);
        ServerWorld world = targetPlayer.getServerWorld();
        BlockPos playerPos = targetPlayer.getBlockPos();
        BlockPos.Mutable searchPos = new BlockPos.Mutable();
        int successfullyBroken = 0;

        for (int i = 0; i < toBreakCount && blocksRemaining > 0; i++) {
            boolean found = false;

            for (int attempt = 0; attempt < 8; attempt++) {
                int dx = random.nextInt(radius * 2 + 1) - radius;
                int dz = random.nextInt(radius * 2 + 1) - radius;
                int dy = -random.nextInt(4);

                searchPos.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);

                if (!world.isChunkLoaded(searchPos.getX() >> 4, searchPos.getZ() >> 4)) {
                    continue;
                }

                BlockState state = world.getBlockState(searchPos);
                float hardness = state.getHardness(world, searchPos);

                if (!state.isAir() && hardness >= 0.0F) {
                    world.breakBlock(searchPos, false);
                    successfullyBroken++;
                    blocksRemaining--;
                    found = true;
                    break;
                }
            }

            if (!found && blocksRemaining > 0) {
                BlockPos steppingPos = targetPlayer.getSteppingPos();
                BlockState state = world.getBlockState(steppingPos);
                if (!state.isAir() && state.getHardness(world, steppingPos) >= 0.0F) {
                    world.breakBlock(steppingPos, false);
                    successfullyBroken++;
                    blocksRemaining--;
                }
            }
        }

        if (successfullyBroken > 0) {
            context.set(REMAINING_BLOCKS_KEY, blocksRemaining);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[FloorHoleEvent] Sinkhole decay concluded for {}",
                    targetPlayer.getName().getString());
        }
    }
}