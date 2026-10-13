package com.merry.merryevents.event.physics;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Global gravitational anomaly belonging to EventCategory.PHYSICS.
 * Applies Jump Boost paired with Slow Falling to simulate lunar kinematics.
 */
public final class MoonGravityEvent implements ChaosEvent {

    private static final String AMPLIFIER_KEY = "jump_amplifier";
    private static final String MULTIPLIER_KEY = "jump_multiplier";

    @Override
    public Identifier getId() {
        return EventIds.MOON_GRAVITY;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.PHYSICS;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        int multiplier = 2 + server.getOverworld().getRandom().nextInt(3);
        int amplifier = switch (multiplier) {
            case 2 -> 1;
            case 3 -> 3;
            case 4 -> 4;
            default -> 1;
        };

        context.set(MULTIPLIER_KEY, multiplier);
        context.set(AMPLIFIER_KEY, amplifier);

        MerryEvents.LOGGER.info(
                "[MoonGravityEvent] Gravitational anomaly active. Jump multiplier: {}x (Jump Boost level {}).",
                multiplier, amplifier + 1);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

        int amplifier = context.getOrDefault(AMPLIFIER_KEY, 1);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            StatusEffectInstance currentJump = player.getStatusEffect(StatusEffects.JUMP_BOOST);
            if (currentJump == null || currentJump.getDuration() <= 20) {
                player.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, amplifier, false, false, true));
            }

            StatusEffectInstance currentSlowFall = player.getStatusEffect(StatusEffects.SLOW_FALLING);
            if (currentSlowFall == null || currentSlowFall.getDuration() <= 20) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false, true));
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.removeStatusEffect(StatusEffects.JUMP_BOOST);
            player.removeStatusEffect(StatusEffects.SLOW_FALLING);
        }
        MerryEvents.LOGGER.info("[MoonGravityEvent] Planetary gravity returned to baseline.");
    }
}