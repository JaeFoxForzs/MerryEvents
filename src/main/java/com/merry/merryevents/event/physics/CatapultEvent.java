// File: src/main/java/com/merry/merryevents/event/physics/CatapultEvent.java

package com.merry.merryevents.event.physics;

import com.merry.merryevents.MerryEvents;
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
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific kinetic impulse event belonging to EventCategory.PHYSICS.
 * Periodically catapults the targeted player approximately 15 blocks into the air
 * accompanied by compressed air bursts and cloud dispersal particles.
 */
public final class CatapultEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("catapult");

    private static final String TIMER_KEY = "catapult_timer";
    private static final int LAUNCH_INTERVAL_TICKS = 60 * 20; // 60 seconds interval
    private static final double LAUNCH_VELOCITY_Y = 1.65;     // Reaches ~15.2 blocks in vanilla gravity

    @Override
    public Identifier getId() {
        return CatapultEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.PHYSICS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Sets the default duration to 2 minutes (120 seconds), fitting the 1–4 minutes range.
     */
    @Override
    public int getCustomDurationTicks(MinecraftServer server) {
        return 120 * 20;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer == null) {
            return;
        }

        // Guaranteed launch shortly after start (2-3 seconds = 40-60 ticks)
        // This guarantees the player is always launched even during short test commands
        int initialDelay = 40 + targetPlayer.getRandom().nextInt(21);
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

        int timer = context.getInt(CatapultEvent.TIMER_KEY, CatapultEvent.LAUNCH_INTERVAL_TICKS) - 1;
        if (timer <= 0) {
            CatapultEvent.launchPlayer(targetPlayer);
            context.set(CatapultEvent.TIMER_KEY, CatapultEvent.LAUNCH_INTERVAL_TICKS);
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

    private static void launchPlayer(ServerPlayerEntity player) {
        // 1. Dismount if riding a boat, horse, minecart, etc.
        if (player.hasVehicle()) {
            player.stopRiding();
        }

        // 2. Disable creative flying if active so client physics don't discard the velocity packet
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }

        // 3. Lift slightly off the ground to break block friction and collision sticking
        player.setPosition(player.getX(), player.getY() + 0.08, player.getZ());
        player.setOnGround(false);
        player.fallDistance = 0.0F;

        // 4. Set velocity and dispatch synchronization packet directly to client
        Vec3d current = player.getVelocity();
        player.setVelocity(current.x * 0.2, CatapultEvent.LAUNCH_VELOCITY_Y, current.z * 0.2);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        // 5. Visuals and acoustics
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

        MerryEvents.LOGGER.debug("[CatapultEvent] Catapulted {} 15 blocks into the air",
                player.getName().getString());
    }
}