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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Player-specific stalker event belonging to EventCategory.MOBS.
 * Spawns an aggressive persistent entity tracking the player until eliminated.
 */
public final class PersonalKillerEvent implements ChaosEvent {
    private static final String KILLER_UUID_KEY = "stalker_uuid";

    @Override
    public Identifier getId() {
        return EventIds.PERSONAL_KILLER;
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
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer == null) {
            return;
        }

        ServerWorld world = targetPlayer.getServerWorld();
        Random random = targetPlayer.getRandom();

        MobEntity killer;
        if (random.nextBoolean()) {
            VindicatorEntity vindicator = EntityType.VINDICATOR.create(world);
            if (vindicator != null) {
                vindicator.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                killer = vindicator;
            } else {
                return;
            }
        } else {
            RabbitEntity rabbit = EntityType.RABBIT.create(world);
            if (rabbit != null) {
                rabbit.setVariant(RabbitEntity.RabbitType.EVIL);
                killer = rabbit;
            } else {
                return;
            }
        }

        Vec3d spawnPos = MobSpawnUtil.findRandomGroundPosition(world, killer, targetPlayer, 8.0, 12.0, 24);
        if (spawnPos == null) {
            spawnPos = targetPlayer.getPos();
            MerryEvents.LOGGER.info(
                    "[PersonalKillerEvent] No distant ground found; spawning stalker directly beside {}",
                    targetPlayer.getName().getString());
        }

        killer.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, random.nextFloat() * 360.0F, 0.0F);

        Text mobTitle = Text
                .translatable("event.merryevents.personal_killer.mob_name_target", targetPlayer.getName().getString())
                .formatted(Formatting.RED, Formatting.BOLD);
        killer.setCustomName(mobTitle);
        killer.setCustomNameVisible(true);
        killer.setPersistent();

        if (killer instanceof HostileEntity hostile) {
            hostile.setTarget(targetPlayer);
        }

        if (!world.spawnEntity(killer)) {
            MerryEvents.LOGGER.warn("[PersonalKillerEvent] Failed to spawn stalker for {}",
                    targetPlayer.getName().getString());
            return;
        }

        context.trackEntity(killer.getUuid());
        context.set(PersonalKillerEvent.KILLER_UUID_KEY, killer.getUuid());

        MerryEvents.LOGGER.info("[PersonalKillerEvent] Dispatched stalker ({}) targeting {}",
                killer.getType().getName().getString(), targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks % 20 != 0) {
            return;
        }

        UUID killerUuid = context.getOrDefault(PersonalKillerEvent.KILLER_UUID_KEY, null);
        if (killerUuid == null) {
            MerryEvents.LOGGER.warn("[PersonalKillerEvent] No stalker was spawned; terminating event for {}",
                    targetPlayer.getName().getString());
            EventManager.getInstance().stopPlayerEvent(server, targetPlayer, EventIds.PERSONAL_KILLER);
            return;
        }

        Entity killerEntity = targetPlayer.getServerWorld().getEntity(killerUuid);
        if (killerEntity == null || !killerEntity.isAlive()) {
            MerryEvents.LOGGER.info("[PersonalKillerEvent] Stalker slain. Terminating event for {}",
                    targetPlayer.getName().getString());
            EventManager.getInstance().stopPlayerEvent(server, targetPlayer, EventIds.PERSONAL_KILLER);
            return;
        }

        if (killerEntity instanceof MobEntity mob && mob.getTarget() != targetPlayer) {
            mob.setTarget(targetPlayer);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        context.discardTrackedEntities(server);
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[PersonalKillerEvent] Stalker contract closed for {}",
                    targetPlayer.getName().getString());
        }
    }
}