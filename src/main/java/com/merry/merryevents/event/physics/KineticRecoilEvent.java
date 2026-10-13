package com.merry.merryevents.event.physics;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific kinetic inversion event belonging to EventCategory.PHYSICS.
 * Repels attacking players backwards with impulse intensity proportional to
 * dealt damage.
 */
public final class KineticRecoilEvent implements ChaosEvent {

    private static final double MAX_IMPULSE_STRENGTH = 3.2;
    private static final double MIN_IMPULSE_STRENGTH = 0.6;
    private static final double DAMAGE_SCALING_FACTOR = 0.28;

    @Override
    public Identifier getId() {
        return EventIds.KINETIC_RECOIL;
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
     * Evaluates whether kinetic recoil is actively modifying attacks for the target
     * player.
     *
     * @param player Server player entity
     * @return True if kinetic recoil event is active
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), EventIds.KINETIC_RECOIL);
    }

    /**
     * Computes recoil vectors and delivers authoritative velocity impulses to the
     * attacking player.
     *
     * @param player       Attacking server player entity
     * @param target       Attacked entity
     * @param damageAmount Effective damage value dealt
     */
    public static void onAttackEntity(ServerPlayerEntity player, Entity target, float damageAmount) {
        double strength = MathHelper.clamp(damageAmount * KineticRecoilEvent.DAMAGE_SCALING_FACTOR,
                KineticRecoilEvent.MIN_IMPULSE_STRENGTH, KineticRecoilEvent.MAX_IMPULSE_STRENGTH);

        Vec3d look = player.getRotationVector();
        double recoilX = -look.x * strength;
        double recoilZ = -look.z * strength;
        double recoilY = Math.min(0.75, 0.22 + strength * 0.12);

        player.setVelocity(recoilX, recoilY, recoilZ);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8F, 1.8F);
        world.spawnParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.2, 0.2, 0.2, 0.0);

        MerryEvents.LOGGER.debug("[KineticRecoilEvent] Dispatched recoil (strength: {}, dmg: {}) to {}",
                strength, damageAmount, player.getName().getString());
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[KineticRecoilEvent] Recoil active for {}", targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[KineticRecoilEvent] Normalized for {}", targetPlayer.getName().getString());
        }
    }
}