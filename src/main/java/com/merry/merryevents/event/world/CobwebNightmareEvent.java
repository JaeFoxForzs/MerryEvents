package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Global block transmutation event belonging to EventCategory.WORLD.
 * Intercepts all block breaking actions across the server and substitutes
 * destroyed
 * blocks with cobwebs to severely restrict mobility.
 */
public final class CobwebNightmareEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.COBWEB_NIGHTMARE;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WORLD;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    /**
     * Intercepts block break results to place a cobweb at the destruction site.
     * Evaluates global event state and avoids recursive placement loops if breaking
     * an existing cobweb.
     *
     * @param world  Server world where destruction occurred
     * @param player Breaking player
     * @param pos    Destroyed block position
     * @param state  State of the block just prior to destruction
     */
    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (state.isOf(Blocks.COBWEB) || !EventManager.getInstance().isGlobalEventActive(EventIds.COBWEB_NIGHTMARE)) {
            return;
        }

        world.setBlockState(pos, Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[CobwebNightmareEvent] Spider infestation initiated globally.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[CobwebNightmareEvent] Spider infestation concluded.");
    }
}