package com.merry.merryevents.event.mobs;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.util.MobSpawnUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific instantaneous zoological outbreak event belonging to EventCategory.MOBS.
 * Instantly spawns a menagerie of 15 peaceful cows, pigs, and chickens dispersed around
 * the target player with comic puff effects before concluding immediately.
 */
public final class ZooEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("zoo");

    private static final EntityType<?>[] ANIMAL_TYPES = {
            EntityType.COW,
            EntityType.PIG,
            EntityType.CHICKEN
    };

    @Override
    public Identifier getId() {
        return ZooEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.MOBS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public int getCustomDurationTicks(MinecraftServer server) {
        return 20; // Instantaneous 1-second execution window
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer == null) {
            return;
        }

        ServerWorld world = targetPlayer.getServerWorld();
        Random random = targetPlayer.getRandom();
        int totalToSpawn = 15;
        int successfullySpawned = 0;

        for (int i = 0; i < totalToSpawn; i++) {
            EntityType<?> chosenType = ANIMAL_TYPES[i % ANIMAL_TYPES.length];
            Entity animal = chosenType.create(world);

            if (animal instanceof MobEntity mob) {
                Vec3d pos = MobSpawnUtil.findRandomGroundPosition(world, mob, targetPlayer, 1.5, 6.0, 16);
                if (pos == null) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double dist = 1.5 + random.nextDouble() * 3.5;
                    pos = targetPlayer.getPos().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
                }

                mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, random.nextFloat() * 360.0F, 0.0F);

                if (world.spawnEntity(mob)) {
                    successfullySpawned++;
                    world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y + 0.5, pos.z, 8, 0.25, 0.25, 0.25, 0.05);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 0.5, pos.z, 3, 0.2, 0.2, 0.2, 0.02);
                }
            }
        }

        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.NEUTRAL, 1.2F, 1.4F);
        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.ENTITY_COW_AMBIENT, SoundCategory.NEUTRAL, 1.0F, 1.1F);
        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.ENTITY_PIG_AMBIENT, SoundCategory.NEUTRAL, 1.0F, 1.1F);

        MerryEvents.LOGGER.info("[ZooEvent] Dispatched instant zoological menagerie ({} animals) around {}",
                successfullySpawned, targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[ZooEvent] Zoological menagerie outbreak completed for {}",
                    targetPlayer.getName().getString());
        }
    }
}