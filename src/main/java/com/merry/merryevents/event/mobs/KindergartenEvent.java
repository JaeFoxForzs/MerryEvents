package com.merry.merryevents.event.mobs;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Global entity miniaturization event belonging to EventCategory.MOBS.
 * Scales down all living entities, boosts movement velocity, and forces child
 * behavior characteristics.
 */
public final class KindergartenEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("kindergarten");

    private static final Identifier SCALE_MODIFIER_ID = MerryEvents.id("kindergarten_scale");
    private static final Identifier SPEED_MODIFIER_ID = MerryEvents.id("kindergarten_speed");

    private static final EntityAttributeModifier SCALE_MODIFIER = new EntityAttributeModifier(
            KindergartenEvent.SCALE_MODIFIER_ID,
            -0.4,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private static final EntityAttributeModifier SPEED_MODIFIER = new EntityAttributeModifier(
            KindergartenEvent.SPEED_MODIFIER_ID,
            0.35,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /**
     * Dedicated-server safe hook initialized exclusively on the client side.
     */
    public static BooleanSupplier clientActiveSupplier = () -> false;

    @Override
    public Identifier getId() {
        return KindergartenEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.MOBS;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    /**
     * Queries whether kindergarten miniaturization is actively running on the
     * client side.
     *
     * @return True if kindergarten event is active on the client
     */
    public static boolean isClientActive() {
        return KindergartenEvent.clientActiveSupplier.getAsBoolean();
    }

    /**
     * Queries whether kindergarten miniaturization is actively running globally.
     *
     * @param entity Context entity to query world side
     * @return True if kindergarten event is actively running
     */
    public static boolean isActive(Entity entity) {
        if (entity.getWorld().isClient()) {
            return KindergartenEvent.isClientActive();
        }
        return com.merry.merryevents.engine.EventManager.getInstance().isGlobalEventActive(KindergartenEvent.ID);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    KindergartenEvent.applyModifiers(living);
                }
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.AMBIENT, 1.0F, 1.8F);
        }

        MerryEvents.LOGGER.info("[KindergartenEvent] Miniaturization active globally across all living entities.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (remainingTicks % 20 != 0) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    EntityAttributeInstance scaleAttr = living.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
                    if (scaleAttr != null && scaleAttr.getModifier(KindergartenEvent.SCALE_MODIFIER_ID) == null) {
                        KindergartenEvent.applyModifiers(living);
                    }
                }
            }
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof LivingEntity living) {
                    KindergartenEvent.removeModifiers(living);
                }
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.AMBIENT, 1.0F, 0.8F);
        }

        MerryEvents.LOGGER.info("[KindergartenEvent] Standard entity scales and speeds restored.");
    }

    @Override
    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer,
            EventContext context) {
        KindergartenEvent.applyModifiers(newPlayer);
    }

    private static void applyModifiers(LivingEntity entity) {
        EntityAttributeInstance scaleAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scaleAttr != null && scaleAttr.getModifier(KindergartenEvent.SCALE_MODIFIER_ID) == null) {
            scaleAttr.addTemporaryModifier(KindergartenEvent.SCALE_MODIFIER);
        }

        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(KindergartenEvent.SPEED_MODIFIER_ID) == null) {
            speedAttr.addTemporaryModifier(KindergartenEvent.SPEED_MODIFIER);
        }
    }

    private static void removeModifiers(LivingEntity entity) {
        EntityAttributeInstance scaleAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scaleAttr != null) {
            scaleAttr.removeModifier(KindergartenEvent.SCALE_MODIFIER_ID);
        }

        EntityAttributeInstance speedAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(KindergartenEvent.SPEED_MODIFIER_ID);
        }
    }
}