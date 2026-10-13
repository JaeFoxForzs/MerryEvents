package com.merry.merryevents.event.mobs;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.util.MobSpawnUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Player-specific companion event belonging to EventCategory.MOBS.
 * Spawns a loyal tamed companion endowed with randomized beneficial buff
 * suites.
 * Follows the player through dimension portals, preserving health and assigned
 * buffs.
 */
public final class RandomPetEvent implements ChaosEvent {
    private static final String PET_UUID_KEY = "companion_pet_uuid";
    private static final String PET_IS_WOLF_KEY = "companion_is_wolf";
    private static final String PET_BUFFS_KEY = "companion_buffs";

    public enum PetBuff {
        SPEED(StatusEffects.SPEED, 1),
        RESISTANCE(StatusEffects.RESISTANCE, 0),
        STRENGTH(StatusEffects.STRENGTH, 0),
        REGENERATION(StatusEffects.REGENERATION, 0),
        FIRE_RESISTANCE(StatusEffects.FIRE_RESISTANCE, 0),
        JUMP_BOOST(StatusEffects.JUMP_BOOST, 1);

        final RegistryEntry<StatusEffect> effect;
        final int amplifier;

        PetBuff(RegistryEntry<StatusEffect> effect, int amplifier) {
            this.effect = effect;
            this.amplifier = amplifier;
        }
    }

    @Override
    public Identifier getId() {
        return EventIds.RANDOM_PET;
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

        Random random = targetPlayer.getRandom();
        boolean isWolf = random.nextBoolean();
        context.set(RandomPetEvent.PET_IS_WOLF_KEY, isWolf);

        List<PetBuff> selectedBuffs = RandomPetEvent.rollRandomBuffs(random);
        List<String> buffNames = new ArrayList<>(selectedBuffs.size());
        for (int i = 0; i < selectedBuffs.size(); i++) {
            buffNames.add(selectedBuffs.get(i).name());
        }
        context.setStringList(RandomPetEvent.PET_BUFFS_KEY, buffNames);

        TameableEntity pet = RandomPetEvent.spawnCompanion(targetPlayer.getServerWorld(), targetPlayer, isWolf, -1.0F,
                selectedBuffs);
        if (pet == null) {
            MerryEvents.LOGGER.warn("[RandomPetEvent] Failed to instantiate companion pet for {}",
                    targetPlayer.getName().getString());
            return;
        }

        context.trackEntity(pet.getUuid());
        context.set(RandomPetEvent.PET_UUID_KEY, pet.getUuid());
        targetPlayer.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.6F);

        MerryEvents.LOGGER.info("[RandomPetEvent] Granted {} to {} with buffs: {}",
                pet.getType().getName().getString(), targetPlayer.getName().getString(), buffNames);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks % 10 != 0) {
            return;
        }

        if (!targetPlayer.isAlive()) {
            EventManager.getInstance().stopPlayerEvent(server, targetPlayer, EventIds.RANDOM_PET);
            return;
        }

        UUID petUuid = context.getOrDefault(RandomPetEvent.PET_UUID_KEY, null);
        if (petUuid == null) {
            return;
        }

        Entity pet = targetPlayer.getServerWorld().getEntity(petUuid);
        if (pet != null) {
            if (!pet.isAlive()) {
                MerryEvents.LOGGER.info("[RandomPetEvent] Companion pet perished. Terminating event for {}",
                        targetPlayer.getName().getString());
                EventManager.getInstance().stopPlayerEvent(server, targetPlayer, EventIds.RANDOM_PET);
                return;
            }

            if (pet instanceof LivingEntity living) {
                List<PetBuff> buffs = RandomPetEvent.resolveBuffsFromContext(context);
                for (int i = 0; i < buffs.size(); i++) {
                    PetBuff buff = buffs.get(i);
                    if (!living.hasStatusEffect(buff.effect)) {
                        living.addStatusEffect(new StatusEffectInstance(
                                buff.effect, StatusEffectInstance.INFINITE, buff.amplifier, false, true, true));
                    }
                }
            }
        }
    }

    /**
     * Handles dimension migration when the owner travels through portals.
     *
     * @param server      Active server instance
     * @param player      Owner player instance
     * @param origin      Previous world dimension
     * @param destination Target world dimension
     * @param context     Event execution context
     */
    public static void onPlayerDimensionChange(MinecraftServer server, ServerPlayerEntity player,
            ServerWorld origin, ServerWorld destination, EventContext context) {
        UUID petUuid = context.getOrDefault(RandomPetEvent.PET_UUID_KEY, null);
        if (petUuid == null) {
            return;
        }

        float health = -1.0F;
        Entity oldPet = origin.getEntity(petUuid);
        if (oldPet != null) {
            if (oldPet instanceof LivingEntity living) {
                health = living.getHealth();
            }
            oldPet.discard();
        }

        boolean isWolf = context.getBoolean(RandomPetEvent.PET_IS_WOLF_KEY, true);
        List<PetBuff> buffs = RandomPetEvent.resolveBuffsFromContext(context);
        TameableEntity newPet = RandomPetEvent.spawnCompanion(destination, player, isWolf, health, buffs);
        if (newPet != null) {
            context.trackEntity(newPet.getUuid());
            context.set(RandomPetEvent.PET_UUID_KEY, newPet.getUuid());
            MerryEvents.LOGGER.info("[RandomPetEvent] Ported companion to dimension {} for {}",
                    destination.getRegistryKey().getValue(), player.getName().getString());
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[RandomPetEvent] Companion pet contract concluded for {}",
                    targetPlayer.getName().getString());
        }
    }

    private static List<PetBuff> rollRandomBuffs(Random random) {
        List<PetBuff> pool = new ArrayList<>(List.of(PetBuff.values()));
        Collections.shuffle(pool);

        int count = 2 + random.nextInt(2); // 2 to 3 distinct beneficial buffs
        return pool.subList(0, Math.min(count, pool.size()));
    }

    private static List<PetBuff> resolveBuffsFromContext(EventContext context) {
        List<String> names = context.getStringList(RandomPetEvent.PET_BUFFS_KEY);
        if (names.isEmpty()) {
            return List.of(PetBuff.SPEED);
        }

        List<PetBuff> buffs = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            try {
                buffs.add(PetBuff.valueOf(names.get(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return buffs.isEmpty() ? List.of(PetBuff.SPEED) : buffs;
    }

    @Nullable
    private static TameableEntity spawnCompanion(ServerWorld world, ServerPlayerEntity player, boolean isWolf,
            float health, List<PetBuff> buffs) {
        Random random = player.getRandom();
        TameableEntity pet;

        if (isWolf) {
            WolfEntity wolf = EntityType.WOLF.create(world);
            if (wolf == null) {
                return null;
            }
            wolf.setOwner(player);
            wolf.setCustomName(Text.translatable("event.merryevents.random_pet.wolf_name",
                    player.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD));
            wolf.setCustomNameVisible(true);
            pet = wolf;
        } else {
            CatEntity cat = EntityType.CAT.create(world);
            if (cat == null) {
                return null;
            }
            cat.setOwner(player);
            cat.setCustomName(Text.translatable("event.merryevents.random_pet.cat_name",
                    player.getName().getString()).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
            cat.setCustomNameVisible(true);
            pet = cat;
        }

        Vec3d spawnPos = MobSpawnUtil.findRandomGroundPosition(world, pet, player, 1.5, 4.0, 16);
        if (spawnPos == null) {
            spawnPos = player.getPos();
        }

        pet.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, random.nextFloat() * 360.0F, 0.0F);
        pet.setPersistent();

        for (int i = 0; i < buffs.size(); i++) {
            PetBuff buff = buffs.get(i);
            pet.addStatusEffect(new StatusEffectInstance(
                    buff.effect, StatusEffectInstance.INFINITE, buff.amplifier, false, true, true));
        }

        if (health > 0.0F) {
            pet.setHealth(Math.min(health, pet.getMaxHealth()));
        }

        if (!world.spawnEntity(pet)) {
            return null;
        }

        world.spawnParticles(ParticleTypes.HEART,
                spawnPos.x, spawnPos.y + 0.6, spawnPos.z,
                8, 0.3, 0.3, 0.3, 0.02);

        return pet;
    }
}