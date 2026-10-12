// File: src/main/java/com/merry/merryevents/event/mobs/RandomPetEvent.java

package com.merry.merryevents.event.mobs;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.util.MobSpawnUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Player-specific companion event belonging to EventCategory.MOBS.
 * Spawns a loyal tamed wolf or cat endowed with perpetual speed enhancement,
 * persisting until the companion or player meets their demise.
 */
public final class RandomPetEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("random_pet");
    private static final String PET_UUID_KEY = "companion_pet_uuid";

    @Override
    public Identifier getId() {
        return RandomPetEvent.ID;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer == null) {
            return;
        }

        ServerWorld world = targetPlayer.getServerWorld();
        Random random = targetPlayer.getRandom();
        boolean isWolf = random.nextBoolean();

        TameableEntity pet;
        if (isWolf) {
            WolfEntity wolf = EntityType.WOLF.create(world);
            if (wolf == null) {
                return;
            }
            wolf.setOwner(targetPlayer);
            wolf.setCustomName(Text.translatable("event.merryevents.random_pet.wolf_name",
                    targetPlayer.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD));
            wolf.setCustomNameVisible(true);
            pet = wolf;
        } else {
            CatEntity cat = EntityType.CAT.create(world);
            if (cat == null) {
                return;
            }
            cat.setOwner(targetPlayer);
            cat.setCustomName(Text.translatable("event.merryevents.random_pet.cat_name",
                    targetPlayer.getName().getString()).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
            cat.setCustomNameVisible(true);
            pet = cat;
        }

        Vec3d spawnPos = MobSpawnUtil.findRandomGroundPosition(world, pet, targetPlayer, 1.5, 4.0, 16);
        if (spawnPos == null) {
            spawnPos = targetPlayer.getPos();
        }

        pet.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, random.nextFloat() * 360.0F, 0.0F);
        pet.setPersistent();
        pet.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, StatusEffectInstance.INFINITE, 1, false, true, true));

        if (!world.spawnEntity(pet)) {
            MerryEvents.LOGGER.warn("[RandomPetEvent] Failed to spawn companion pet for {}",
                    targetPlayer.getName().getString());
            return;
        }

        context.trackEntity(pet.getUuid());
        context.set(RandomPetEvent.PET_UUID_KEY, pet.getUuid());

        world.spawnParticles(ParticleTypes.HEART,
                spawnPos.x, spawnPos.y + 0.6, spawnPos.z,
                8, 0.3, 0.3, 0.3, 0.02);
        targetPlayer.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.6F);

        MerryEvents.LOGGER.info("[RandomPetEvent] Granted speedy {} to {}",
                pet.getType().getName().getString(), targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
        if (targetPlayer == null || remainingTicks % 10 != 0) {
            return;
        }

        UUID petUuid = context.getOrDefault(RandomPetEvent.PET_UUID_KEY, null);
        if (petUuid == null) {
            EventManager.getInstance().stopPlayerEvent(server, targetPlayer, RandomPetEvent.ID);
            return;
        }

        Entity pet = targetPlayer.getServerWorld().getEntity(petUuid);
        if (pet == null || !pet.isAlive() || !targetPlayer.isAlive()) {
            MerryEvents.LOGGER.info("[RandomPetEvent] Companion pet perished. Terminating event for {}",
                    targetPlayer.getName().getString());
            EventManager.getInstance().stopPlayerEvent(server, targetPlayer, RandomPetEvent.ID);
            return;
        }

        if (pet instanceof LivingEntity living && !living.hasStatusEffect(StatusEffects.SPEED)) {
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, StatusEffectInstance.INFINITE, 1, false, true, true));
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[RandomPetEvent] Pet contract concluded for {}",
                    targetPlayer.getName().getString());
        }
    }
}