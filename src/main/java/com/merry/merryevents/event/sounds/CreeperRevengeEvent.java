package com.merry.merryevents.event.sounds;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific phantom acoustic hazard event belonging to EventCategory.SOUNDS.
 * Dispatches authentic creeper priming audio packets strictly behind the target player's
 * view vector at randomized cadence intervals.
 */
public final class CreeperRevengeEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("creeper_revenge");

    private static final String NEXT_TRIGGER_TICK_KEY = "next_fuse_tick";
    private static final int MIN_INTERVAL_SECONDS = 15;
    private static final int MAX_INTERVAL_SECONDS = 35;
    private static final double SOUND_BEHIND_DISTANCE = 1.8;

    @Override
    public Identifier getId() {
        return CreeperRevengeEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.SOUNDS;
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

        scheduleNextTrigger(targetPlayer.getRandom(), context);
        MerryEvents.LOGGER.info("[CreeperRevengeEvent] Phantom creeper acoustic stalker attached to {}",
                targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        int countdown = context.getInt(NEXT_TRIGGER_TICK_KEY, 20) - 1;
        if (countdown <= 0) {
            dispatchPhantomFuseSound(targetPlayer);
            scheduleNextTrigger(targetPlayer.getRandom(), context);
        } else {
            context.set(NEXT_TRIGGER_TICK_KEY, countdown);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[CreeperRevengeEvent] Phantom creeper acoustics neutralized for {}",
                    targetPlayer.getName().getString());
        }
    }

    private static void scheduleNextTrigger(Random random, EventContext context) {
        int delaySeconds = MIN_INTERVAL_SECONDS + random.nextInt(MAX_INTERVAL_SECONDS - MIN_INTERVAL_SECONDS + 1);
        context.set(NEXT_TRIGGER_TICK_KEY, delaySeconds * 20);
    }

    private static void dispatchPhantomFuseSound(ServerPlayerEntity player) {
        Vec3d lookDirection = player.getRotationVector();
        double soundX = player.getX() - (lookDirection.x * SOUND_BEHIND_DISTANCE);
        double soundY = player.getY() + 0.1;
        double soundZ = player.getZ() - (lookDirection.z * SOUND_BEHIND_DISTANCE);

        net.minecraft.registry.entry.RegistryEntry<net.minecraft.sound.SoundEvent> soundEntry =
                Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_CREEPER_PRIMED);

        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                soundEntry,
                SoundCategory.HOSTILE,
                soundX,
                soundY,
                soundZ,
                1.0F,
                0.95F + player.getRandom().nextFloat() * 0.1F,
                player.getRandom().nextLong()
        ));

        MerryEvents.LOGGER.debug("[CreeperRevengeEvent] Projected phantom hiss directly behind {}",
                player.getName().getString());
    }
}