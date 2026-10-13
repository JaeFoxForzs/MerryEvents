package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific terrain transmutation event belonging to EventCategory.WORLD.
 * Periodically propagates radial transmutation waves converting ore veins into
 * alternative variants with zero server tick lag.
 */
public final class OreMutationEvent implements ChaosEvent {

    private static final String WAVE_STEP_KEY = "wave_step";
    private static final String TARGET_RADIUS_KEY = "target_radius";
    private static final String NEXT_PULSE_KEY = "next_pulse";

    private static final int MIN_INTERVAL_SECONDS = 10;
    private static final int MAX_INTERVAL_SECONDS = 20;
    private static final int MIN_RADIUS = 8;
    private static final int MAX_RADIUS = 18;
    private static final int TOTAL_WAVE_STEPS = 5;

    private static final Block[] STONE_ORES = {
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.COPPER_ORE,
            Blocks.GOLD_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.LAPIS_ORE,
            Blocks.EMERALD_ORE,
            Blocks.DIAMOND_ORE
    };

    private static final Block[] DEEPSLATE_ORES = {
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE
    };

    private static final Block[] NETHER_ORES = {
            Blocks.NETHER_GOLD_ORE,
            Blocks.NETHER_QUARTZ_ORE
    };

    @Override
    public Identifier getId() {
        return EventIds.ORE_MUTATION;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        OreMutationEvent.scheduleNextPulse(context, targetPlayer.getRandom());
        context.set(OreMutationEvent.WAVE_STEP_KEY, -1);

        MerryEvents.LOGGER.info("[OreMutationEvent] Geological instability active for {}",
                targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        int waveStep = context.getInt(OreMutationEvent.WAVE_STEP_KEY, -1);
        ServerWorld world = targetPlayer.getServerWorld();

        if (waveStep >= 0) {
            int radius = context.getInt(OreMutationEvent.TARGET_RADIUS_KEY, 12);
            OreMutationEvent.processWaveStep(world, targetPlayer, waveStep, radius);

            waveStep++;
            if (waveStep >= OreMutationEvent.TOTAL_WAVE_STEPS) {
                context.set(OreMutationEvent.WAVE_STEP_KEY, -1);
                OreMutationEvent.scheduleNextPulse(context, targetPlayer.getRandom());
            } else {
                context.set(OreMutationEvent.WAVE_STEP_KEY, waveStep);
            }
            return;
        }

        int countdown = context.getInt(OreMutationEvent.NEXT_PULSE_KEY, 100) - 1;
        if (countdown <= 0) {
            Random random = targetPlayer.getRandom();
            int targetRadius = OreMutationEvent.MIN_RADIUS + random.nextInt(OreMutationEvent.MAX_RADIUS - OreMutationEvent.MIN_RADIUS + 1);

            context.set(OreMutationEvent.TARGET_RADIUS_KEY, targetRadius);
            context.set(OreMutationEvent.WAVE_STEP_KEY, 0);

            world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.3F, 1.5F);
            world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1.5F, 0.8F);
        } else {
            context.set(OreMutationEvent.NEXT_PULSE_KEY, countdown);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[OreMutationEvent] Geological baseline restored for {}",
                    targetPlayer.getName().getString());
        }
    }

    private static void scheduleNextPulse(EventContext context, Random random) {
        int seconds = OreMutationEvent.MIN_INTERVAL_SECONDS + random.nextInt(OreMutationEvent.MAX_INTERVAL_SECONDS - OreMutationEvent.MIN_INTERVAL_SECONDS + 1);
        context.set(OreMutationEvent.NEXT_PULSE_KEY, seconds * 20);
    }

    private static void processWaveStep(ServerWorld world, ServerPlayerEntity player, int step, int totalRadius) {
        double innerRadius = (double) (totalRadius * step) / OreMutationEvent.TOTAL_WAVE_STEPS;
        double outerRadius = (double) (totalRadius * (step + 1)) / OreMutationEvent.TOTAL_WAVE_STEPS;
        double innerSq = innerRadius * innerRadius;
        double outerSq = outerRadius * outerRadius;

        BlockPos center = player.getBlockPos();
        int maxBound = MathHelper.ceil(outerRadius);

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        Random random = player.getRandom();

        for (int dx = -maxBound; dx <= maxBound; dx++) {
            for (int dz = -maxBound; dz <= maxBound; dz++) {
                int blockX = center.getX() + dx;
                int blockZ = center.getZ() + dz;

                int chunkX = ChunkSectionPos.getSectionCoord(blockX);
                int chunkZ = ChunkSectionPos.getSectionCoord(blockZ);
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                for (int dy = -maxBound; dy <= maxBound; dy++) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq < innerSq || distSq > outerSq) {
                        continue;
                    }

                    mutable.set(blockX, center.getY() + dy, blockZ);
                    BlockState current = world.getBlockState(mutable);
                    Block mutatedBlock = OreMutationEvent.getMutatedOre(current.getBlock(), random);

                    if (mutatedBlock != null) {
                        world.setBlockState(mutable, mutatedBlock.getDefaultState(), Block.NOTIFY_ALL);
                        world.spawnParticles(ParticleTypes.ENCHANT,
                                mutable.getX() + 0.5, mutable.getY() + 0.5, mutable.getZ() + 0.5,
                                5, 0.25, 0.25, 0.25, 0.2);
                    }
                }
            }
        }
    }

    private static @Nullable Block getMutatedOre(Block original, Random random) {
        if (random.nextFloat() < 0.01F && OreMutationEvent.isAnyOre(original)) {
            return Blocks.ANCIENT_DEBRIS;
        }

        for (int i = 0; i < OreMutationEvent.STONE_ORES.length; i++) {
            if (OreMutationEvent.STONE_ORES[i] == original) {
                int nextIndex = random.nextInt(OreMutationEvent.STONE_ORES.length - 1);
                return nextIndex >= i ? OreMutationEvent.STONE_ORES[nextIndex + 1] : OreMutationEvent.STONE_ORES[nextIndex];
            }
        }

        for (int i = 0; i < OreMutationEvent.DEEPSLATE_ORES.length; i++) {
            if (OreMutationEvent.DEEPSLATE_ORES[i] == original) {
                int nextIndex = random.nextInt(OreMutationEvent.DEEPSLATE_ORES.length - 1);
                return nextIndex >= i ? OreMutationEvent.DEEPSLATE_ORES[nextIndex + 1] : OreMutationEvent.DEEPSLATE_ORES[nextIndex];
            }
        }

        for (int i = 0; i < OreMutationEvent.NETHER_ORES.length; i++) {
            if (OreMutationEvent.NETHER_ORES[i] == original) {
                int nextIndex = random.nextInt(OreMutationEvent.NETHER_ORES.length - 1);
                return nextIndex >= i ? OreMutationEvent.NETHER_ORES[nextIndex + 1] : OreMutationEvent.NETHER_ORES[nextIndex];
            }
        }

        return null;
    }

    private static boolean isAnyOre(Block block) {
        for (Block stoneOre : OreMutationEvent.STONE_ORES) {
            if (stoneOre == block)
                return true;
        }
        for (Block deepslateOre : OreMutationEvent.DEEPSLATE_ORES) {
            if (deepslateOre == block)
                return true;
        }
        for (Block netherOre : OreMutationEvent.NETHER_ORES) {
            if (netherOre == block)
                return true;
        }
        return false;
    }
}