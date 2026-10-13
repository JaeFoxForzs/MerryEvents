package com.merry.merryevents.event.mobs;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class ExplodingMobsEvent implements ChaosEvent {

    @Override
    public Identifier getId() {
        return EventIds.EXPLODING_MOBS;
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
     * Intercepts mob deaths to ignite biological detonation mechanics natively.
     *
     * @param livingEntity Target slain entity
     * @param damageSource Lethal damage source
     */
    public static void onEntityDeath(LivingEntity livingEntity, DamageSource damageSource) {
        if (livingEntity.getWorld().isClient() || !EventManager.getInstance().isGlobalEventActive(EventIds.EXPLODING_MOBS)) {
            return;
        }

        if (!(livingEntity instanceof Monster || livingEntity.getType().getSpawnGroup() == SpawnGroup.MONSTER)) {
            return;
        }

        if (damageSource.isOf(DamageTypes.EXPLOSION) || damageSource.isOf(DamageTypes.PLAYER_EXPLOSION)) {
            return;
        }

        float maxHp = livingEntity.getMaxHealth();
        double attackDamage = 0.0;
        var attackAttr = livingEntity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackDamage = attackAttr.getValue();
        }

        float power = (float) MathHelper.clamp(1.0 + (maxHp * 0.04) + (attackDamage * 0.1), 0.8, 4.5);

        livingEntity.getWorld().createExplosion(
                null,
                livingEntity.getX(),
                livingEntity.getY(),
                livingEntity.getZ(),
                power,
                World.ExplosionSourceType.MOB);

        MerryEvents.LOGGER.debug("[ExplodingMobsEvent] Hostile entity {} (HP={}, Attack={}) detonated with power {}",
                livingEntity.getName().getString(), maxHp, attackDamage, power);
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER
                .info("[ExplodingMobsEvent] Biological destabilization active: all hostiles detonate on death.");
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        MerryEvents.LOGGER.info("[ExplodingMobsEvent] Biological stabilization restored.");
    }
}