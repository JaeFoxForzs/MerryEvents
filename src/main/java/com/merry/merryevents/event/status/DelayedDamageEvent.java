package com.merry.merryevents.event.status;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
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
 * Personal status disruption event belonging to EventCategory.STATUS.
 * Completely suppresses immediate health reduction, buffering post-mitigation
 * damage into delayed batch releases with progressive aura particles.
 */
public final class DelayedDamageEvent implements ChaosEvent {

    private static final String BUFFER_RATIO_KEY = "buffer_ratio";
    private static final String DELAY_TICKS_KEY = "delay_ticks";
    private static final String ACCUMULATED_DAMAGE_KEY = "accumulated_damage";
    private static final String TIMER_KEY = "countdown_timer";

    private static final ThreadLocal<Boolean> IS_FLUSHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public Identifier getId() {
        return EventIds.DELAYED_DAMAGE;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.STATUS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Queries whether delayed damage batch release is currently in progress on this
     * thread.
     *
     * @return True if currently flushing delayed damage
     */
    public static boolean isFlushing() {
        return DelayedDamageEvent.IS_FLUSHING.get();
    }

    /**
     * Evaluates whether the delayed damage event is actively running for the target
     * player.
     *
     * @param player Server player instance to query
     * @return True if event is currently active on the target player
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), EventIds.DELAYED_DAMAGE);
    }

    /**
     * Buffers effective post-mitigation damage into the active event context and
     * triggers audio feedback.
     *
     * @param player          Target damaged player
     * @param effectiveDamage Mitigated damage intensity
     */
    public static void bufferDamage(ServerPlayerEntity player, float effectiveDamage) {
        EventContext context = EventManager.getInstance().getPlayerEventContext(player.getUuid(),
                EventIds.DELAYED_DAMAGE);
        if (context == null) {
            return;
        }

        float ratio = context.getOrDefault(DelayedDamageEvent.BUFFER_RATIO_KEY, 0.75F);
        float bufferedDamage = effectiveDamage * ratio;

        float currentAccumulated = context.getOrDefault(DelayedDamageEvent.ACCUMULATED_DAMAGE_KEY, 0.0F);
        context.set(DelayedDamageEvent.ACCUMULATED_DAMAGE_KEY, currentAccumulated + bufferedDamage);

        player.playSoundToPlayer(SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 0.8F, 1.8F);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer == null) {
            return;
        }

        Random random = targetPlayer.getRandom();
        int percent = 50 + random.nextInt(51);
        float bufferRatio = percent / 100.0F;

        int delaySec = 4 + random.nextInt(7);
        int delayTicks = delaySec * 20;

        context.set(DelayedDamageEvent.BUFFER_RATIO_KEY, bufferRatio);
        context.set(DelayedDamageEvent.DELAY_TICKS_KEY, delayTicks);
        context.set(DelayedDamageEvent.ACCUMULATED_DAMAGE_KEY, 0.0F);
        context.set(DelayedDamageEvent.TIMER_KEY, delayTicks);

        MerryEvents.LOGGER.info(
                "[DelayedDamageEvent] Activated for {} (Buffered ratio: {}%, Batch cadence: {}s)",
                targetPlayer.getName().getString(), percent, delaySec);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        float accumulated = context.getOrDefault(DelayedDamageEvent.ACCUMULATED_DAMAGE_KEY, 0.0F);
        ServerWorld world = targetPlayer.getServerWorld();

        if (accumulated > 0.0F) {
            if (remainingTicks % 5 == 0) {
                int count = Math.min(10, 1 + (int) (accumulated / 2.0F));
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                        targetPlayer.getX(), targetPlayer.getY() + targetPlayer.getHeight() * 0.5, targetPlayer.getZ(),
                        count, 0.35, 0.4, 0.35, 0.02);
            }
        } else if (remainingTicks % 16 == 0) {
            world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    targetPlayer.getX(), targetPlayer.getY() + targetPlayer.getHeight() * 0.6, targetPlayer.getZ(),
                    1, 0.25, 0.3, 0.25, 0.01);
        }

        int timer = context.getInt(DelayedDamageEvent.TIMER_KEY, 80) - 1;
        if (timer <= 0) {
            DelayedDamageEvent.flushDamage(server, targetPlayer, context);
            context.set(DelayedDamageEvent.TIMER_KEY, context.getInt(DelayedDamageEvent.DELAY_TICKS_KEY, 80));
        } else {
            context.set(DelayedDamageEvent.TIMER_KEY, timer);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null && targetPlayer.isAlive()) {
            DelayedDamageEvent.flushDamage(server, targetPlayer, context);
            MerryEvents.LOGGER.info("[DelayedDamageEvent] Final damage flushed and normalized for {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer,
            EventContext context) {
        context.set(DelayedDamageEvent.ACCUMULATED_DAMAGE_KEY, 0.0F);
        context.set(DelayedDamageEvent.TIMER_KEY, context.getInt(DelayedDamageEvent.DELAY_TICKS_KEY, 80));
    }

    private static void flushDamage(MinecraftServer server, ServerPlayerEntity player, EventContext context) {
        float accumulated = context.getOrDefault(DelayedDamageEvent.ACCUMULATED_DAMAGE_KEY, 0.0F);
        if (accumulated <= 0.0F || !player.isAlive()) {
            return;
        }

        context.set(DelayedDamageEvent.ACCUMULATED_DAMAGE_KEY, 0.0F);

        try {
            DelayedDamageEvent.IS_FLUSHING.set(Boolean.TRUE);
            player.timeUntilRegen = 0;
            player.damage(player.getServerWorld().getDamageSources().generic(), accumulated);
        } finally {
            DelayedDamageEvent.IS_FLUSHING.set(Boolean.FALSE);
        }

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.PLAYERS, 1.4F, 0.7F);
        world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                player.getX(), player.getY() + 1.0, player.getZ(),
                Math.min(25, Math.max(6, (int) accumulated)),
                0.3, 0.5, 0.3, 0.1);

        MerryEvents.LOGGER.debug("[DelayedDamageEvent] Flushed {} buffered damage onto {}",
                accumulated, player.getName().getString());
    }
}