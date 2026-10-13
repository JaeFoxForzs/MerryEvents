package com.merry.merryevents.event.physics;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific kinetic impulse event belonging to EventCategory.PHYSICS.
 * Stochastically catapults the targeted player with randomized vertical heights,
 * omnidirectional velocity vectors, and randomized cooldown intervals.
 */
public final class CatapultEvent implements ChaosEvent {

    private static final String TIMER_KEY = "catapult_timer";

    private static final int MIN_INTERVAL_SECONDS = 20;
    private static final int MAX_INTERVAL_SECONDS = 50;

    private static final double MIN_LAUNCH_VELOCITY_Y = 1.15; // ~10 blocks height
    private static final double MAX_LAUNCH_VELOCITY_Y = 2.15; // ~26 blocks height
    private static final double MIN_HORIZONTAL_SPEED = 0.35;
    private static final double MAX_HORIZONTAL_SPEED = 1.25;

    @Override
    public Identifier getId() {
        return EventIds.CATAPULT;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.PHYSICS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public int getCustomDurationTicks(MinecraftServer server) {
        return 120 * 20;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        int initialDelay = 40 + targetPlayer.getRandom().nextInt(41);
        context.set(CatapultEvent.TIMER_KEY, initialDelay);

        MerryEvents.LOGGER.info("[CatapultEvent] Catapult mechanism armed for {}",
                targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        int timer = context.getInt(CatapultEvent.TIMER_KEY, 100) - 1;
        if (timer <= 0) {
            CatapultEvent.launchPlayer(targetPlayer);
            int nextInterval = CatapultEvent.randomInterval(targetPlayer.getRandom());
            context.set(CatapultEvent.TIMER_KEY, nextInterval);
        } else {
            context.set(CatapultEvent.TIMER_KEY, timer);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[CatapultEvent] Catapult mechanism disarmed for {}",
                    targetPlayer.getName().getString());
        }
    }

    private static int randomInterval(Random random) {
        int seconds = CatapultEvent.MIN_INTERVAL_SECONDS
                + random.nextInt(CatapultEvent.MAX_INTERVAL_SECONDS - CatapultEvent.MIN_INTERVAL_SECONDS + 1);
        return seconds * 20;
    }

    private static void launchPlayer(ServerPlayerEntity player) {
        if (player.hasVehicle()) {
            player.stopRiding();
        }

        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }

        player.setPosition(player.getX(), player.getY() + 0.08, player.getZ());
        player.setOnGround(false);
        player.fallDistance = 0.0F;

        Random random = player.getRandom();

        double velocityY = CatapultEvent.MIN_LAUNCH_VELOCITY_Y
                + random.nextDouble() * (CatapultEvent.MAX_LAUNCH_VELOCITY_Y - CatapultEvent.MIN_LAUNCH_VELOCITY_Y);

        double angle = random.nextDouble() * Math.PI * 2.0;
        double horizSpeed = CatapultEvent.MIN_HORIZONTAL_SPEED
                + random.nextDouble() * (CatapultEvent.MAX_HORIZONTAL_SPEED - CatapultEvent.MIN_HORIZONTAL_SPEED);

        double velocityX = Math.cos(angle) * horizSpeed;
        double velocityZ = Math.sin(angle) * horizSpeed;

        player.setVelocity(velocityX, velocityY, velocityZ);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.4F, 0.6F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.PLAYERS, 1.6F, 0.9F);

        world.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY(), player.getZ(),
                24, 0.35, 0.1, 0.35, 0.15);
        world.spawnParticles(ParticleTypes.POOF,
                player.getX(), player.getY(), player.getZ(),
                12, 0.25, 0.1, 0.25, 0.05);

        MerryEvents.LOGGER.debug("[CatapultEvent] Catapulted {} (vy={:.2f}, vx={:.2f}, vz={:.2f})",
                player.getName().getString(), velocityY, velocityX, velocityZ);
    }
}