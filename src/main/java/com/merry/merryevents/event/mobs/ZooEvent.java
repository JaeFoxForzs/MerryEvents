package com.merry.merryevents.event.mobs;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
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
 * Player-specific instantaneous zoological outbreak event belonging to
 * EventCategory.MOBS.
 * Spawns between 15 and 45 peaceful animals within a randomized radius around
 * the target player with comic puff effects before concluding immediately.
 */
public final class ZooEvent implements ChaosEvent {

    private static final EntityType<?>[] ANIMAL_TYPES = {
            EntityType.COW,
            EntityType.PIG,
            EntityType.CHICKEN,
            EntityType.SHEEP
    };

    private static final int MIN_ANIMALS = 15;
    private static final int MAX_ANIMALS = 45;
    private static final int INSTANT_EVENT_DURATION_TICKS = 20;
    private static final double BASE_MIN_RADIUS = 1.5D;
    private static final double MIN_RADIUS_VARIATION = 2.5D;
    private static final double BASE_OUTER_RADIUS = 5.0D;
    private static final double OUTER_RADIUS_VARIATION = 9.0D;
    private static final double FULL_CIRCLE_RADIANS = Math.PI * 2.0D;
    private static final double FULL_ROTATION_DEGREES = 360.0D;
    private static final double PARTICLE_HEIGHT_OFFSET = 0.5D;
    private static final double PARTICLE_SPREAD = 0.25D;
    private static final double PARTICLE_SPEED = 0.05D;
    private static final double HAPPY_VILLAGER_PARTICLE_SPREAD = 0.2D;
    private static final double HAPPY_VILLAGER_PARTICLE_SPEED = 0.02D;
    private static final int POOF_PARTICLE_COUNT = 8;
    private static final int HAPPY_VILLAGER_PARTICLE_COUNT = 3;
    private static final int SEARCH_RADIUS_LIMIT = 16;
    private static final float CHICKEN_EGG_SOUND_VOLUME = 1.2F;
    private static final float CHICKEN_EGG_SOUND_PITCH = 1.4F;
    private static final float AMBIENT_SOUND_VOLUME = 1.0F;
    private static final float AMBIENT_SOUND_PITCH = 1.1F;

    @Override
    public Identifier getId() {
        return EventIds.ZOO;
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
        return ZooEvent.INSTANT_EVENT_DURATION_TICKS; // Instantaneous 1-second execution window
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        ServerWorld world = targetPlayer.getServerWorld();
        Random random = targetPlayer.getRandom();

        int totalToSpawn = ZooEvent.MIN_ANIMALS + random.nextInt(ZooEvent.MAX_ANIMALS - ZooEvent.MIN_ANIMALS + 1);
        double minRadius = ZooEvent.BASE_MIN_RADIUS + random.nextDouble() * ZooEvent.MIN_RADIUS_VARIATION; // 1.5 to 4.0 blocks inner
                                                                                         // boundary
        double maxRadius = minRadius + ZooEvent.BASE_OUTER_RADIUS + random.nextDouble() * ZooEvent.OUTER_RADIUS_VARIATION; // 6.5 to 18.0
                                                                                                         // blocks outer
                                                                                                         // boundary

        int successfullySpawned = 0;

        for (int i = 0; i < totalToSpawn; i++) {
            EntityType<?> chosenType = ZooEvent.ANIMAL_TYPES[i % ZooEvent.ANIMAL_TYPES.length];
            Entity animal = chosenType.create(world);

            if (animal instanceof MobEntity mob) {
                Vec3d pos = MobSpawnUtil.findRandomGroundPosition(world, mob, targetPlayer, minRadius, maxRadius,
                        ZooEvent.SEARCH_RADIUS_LIMIT);
                if (pos == null) {
                    double angle = random.nextDouble() * ZooEvent.FULL_CIRCLE_RADIANS;
                    double dist = minRadius + random.nextDouble() * (maxRadius - minRadius);
                    pos = targetPlayer.getPos().add(Math.cos(angle) * dist, 0.0D, Math.sin(angle) * dist);
                }

                mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, random.nextFloat() * (float) ZooEvent.FULL_ROTATION_DEGREES,
                        0.0F);

                if (world.spawnEntity(mob)) {
                    successfullySpawned++;
                    world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y + ZooEvent.PARTICLE_HEIGHT_OFFSET, pos.z,
                            ZooEvent.POOF_PARTICLE_COUNT, ZooEvent.PARTICLE_SPREAD, ZooEvent.PARTICLE_SPREAD, ZooEvent.PARTICLE_SPREAD, ZooEvent.PARTICLE_SPEED);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + ZooEvent.PARTICLE_HEIGHT_OFFSET, pos.z,
                            ZooEvent.HAPPY_VILLAGER_PARTICLE_COUNT, ZooEvent.HAPPY_VILLAGER_PARTICLE_SPREAD,
                            ZooEvent.HAPPY_VILLAGER_PARTICLE_SPREAD, ZooEvent.HAPPY_VILLAGER_PARTICLE_SPREAD,
                            ZooEvent.HAPPY_VILLAGER_PARTICLE_SPEED);
                }
            }
        }

        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.NEUTRAL, ZooEvent.CHICKEN_EGG_SOUND_VOLUME,
                ZooEvent.CHICKEN_EGG_SOUND_PITCH);
        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.ENTITY_COW_AMBIENT, SoundCategory.NEUTRAL, ZooEvent.AMBIENT_SOUND_VOLUME,
                ZooEvent.AMBIENT_SOUND_PITCH);
        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.ENTITY_PIG_AMBIENT, SoundCategory.NEUTRAL, ZooEvent.AMBIENT_SOUND_VOLUME,
                ZooEvent.AMBIENT_SOUND_PITCH);
        world.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.NEUTRAL, ZooEvent.AMBIENT_SOUND_VOLUME,
                ZooEvent.AMBIENT_SOUND_PITCH);

        MerryEvents.LOGGER.info(
                "[ZooEvent] Dispatched zoological outbreak ({} animals, radius: {:.1f}-{:.1f}m) around {}",
                successfullySpawned, minRadius, maxRadius, targetPlayer.getName().getString());
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