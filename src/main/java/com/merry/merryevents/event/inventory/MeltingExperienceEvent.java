// File: src/main/java/com/merry/merryevents/event/inventory/MeltingExperienceEvent.java

package com.merry.merryevents.event.inventory;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Player-specific metabolic degradation event belonging to EventCategory.INVENTORY.
 * Periodically consumes one experience level in exchange for potent positive combat status effects.
 */
public final class MeltingExperienceEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("melting_experience");

    private static final String TIMER_KEY = "melting_timer";
    private static final int MIN_INTERVAL_TICKS = 10 * 20; // 10 seconds
    private static final int MAX_INTERVAL_TICKS = 20 * 20; // 20 seconds
    private static final int MIN_BUFF_TICKS = 8 * 20;      // 8 seconds
    private static final int MAX_BUFF_TICKS = 15 * 20;     // 15 seconds

    private static final List<RegistryEntry<StatusEffect>> ELIGIBLE_BUFFS = List.of(
            StatusEffects.STRENGTH,
            StatusEffects.SPEED,
            StatusEffects.REGENERATION
    );

    @Override
    public Identifier getId() {
        return MeltingExperienceEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.INVENTORY;
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

        MeltingExperienceEvent.scheduleNextInterval(context, targetPlayer.getRandom());
        targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, 1.4F);

        MerryEvents.LOGGER.info("[MeltingExperienceEvent] Experience drain armed for {}",
                targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
        if (targetPlayer == null || !targetPlayer.isAlive()) {
            return;
        }

        int timer = context.getInt(MeltingExperienceEvent.TIMER_KEY, MeltingExperienceEvent.MIN_INTERVAL_TICKS) - 1;
        if (timer <= 0) {
            MeltingExperienceEvent.drainExperienceAndGrantBuff(targetPlayer);
            MeltingExperienceEvent.scheduleNextInterval(context, targetPlayer.getRandom());
        } else {
            context.set(MeltingExperienceEvent.TIMER_KEY, timer);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[MeltingExperienceEvent] Experience drain normalized for {}",
                    targetPlayer.getName().getString());
        }
    }

    private static void scheduleNextInterval(EventContext context, Random random) {
        int nextDelay = MeltingExperienceEvent.MIN_INTERVAL_TICKS
                + random.nextInt(MeltingExperienceEvent.MAX_INTERVAL_TICKS - MeltingExperienceEvent.MIN_INTERVAL_TICKS + 1);
        context.set(MeltingExperienceEvent.TIMER_KEY, nextDelay);
    }

    private static void drainExperienceAndGrantBuff(ServerPlayerEntity player) {
        Random random = player.getRandom();

        if (player.experienceLevel > 0) {
            player.addExperienceLevels(-1);
        } else {
            player.experienceProgress = 0.0F;
            player.totalExperience = 0;
        }

        RegistryEntry<StatusEffect> chosenBuff = ELIGIBLE_BUFFS.get(random.nextInt(ELIGIBLE_BUFFS.size()));
        int buffDuration = MeltingExperienceEvent.MIN_BUFF_TICKS
                + random.nextInt(MeltingExperienceEvent.MAX_BUFF_TICKS - MeltingExperienceEvent.MIN_BUFF_TICKS + 1);

        // Potency level II (amplifier = 1)
        player.addStatusEffect(new StatusEffectInstance(chosenBuff, buffDuration, 1, false, true, true));

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 0.5F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6F, 1.6F);

        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.3, 0.4, 0.3, 0.04);
        world.spawnParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.2, player.getZ(),
                16, 0.4, 0.5, 0.4, 0.1);

        MerryEvents.LOGGER.debug("[MeltingExperienceEvent] Drained 1 level from {} and granted {} for {} ticks",
                player.getName().getString(), chosenBuff.getIdAsString(), buffDuration);
    }
}