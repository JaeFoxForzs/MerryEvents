package com.merry.merryevents.event.mobs;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Player-specific assassination event belonging to EventCategory.MOBS.
 * Spawns an aggressive stalker (Vindicator or Killer Rabbit) targeting the
 * player until terminated.
 */
public final class PersonalKillerEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("personal_killer");
    private static final String KILLER_UUID_KEY = "stalker_uuid";

    @Override
    public Identifier getId() {
        return ID;
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

        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 8.0 + random.nextDouble() * 4.0;
        double spawnX = targetPlayer.getX() + Math.cos(angle) * distance;
        double spawnZ = targetPlayer.getZ() + Math.sin(angle) * distance;
        double spawnY = targetPlayer.getY();

        BlockPos spawnPos = BlockPos.ofFloored(spawnX, spawnY, spawnZ);
        if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(spawnPos.getX()),
                ChunkSectionPos.getSectionCoord(spawnPos.getZ()))) {
            spawnX = targetPlayer.getX();
            spawnZ = targetPlayer.getZ();
        }

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

        killer.refreshPositionAndAngles(spawnX, spawnY, spawnZ, random.nextFloat() * 360.0F, 0.0F);

        Text mobTitle = Text
                .translatable("event.merryevents.personal_killer.mob_name_target", targetPlayer.getName().getString())
                .formatted(Formatting.RED, Formatting.BOLD);
        killer.setCustomName(mobTitle);
        killer.setCustomNameVisible(true);
        killer.setPersistent();

        if (killer instanceof HostileEntity hostile) {
            hostile.setTarget(targetPlayer);
        }

        world.spawnEntity(killer);
        context.trackEntity(killer.getUuid());
        context.set(KILLER_UUID_KEY, killer.getUuid());

        MerryEvents.LOGGER.info("[PersonalKillerEvent] Dispatched stalker ({}) targeting {}",
                killer.getType().getName().getString(), targetPlayer.getName().getString());
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks % 20 != 0) {
            return;
        }

        UUID killerUuid = context.getOrDefault(KILLER_UUID_KEY, null);
        if (killerUuid == null) {
            return;
        }

        Entity killerEntity = targetPlayer.getServerWorld().getEntity(killerUuid);
        if (killerEntity == null || !killerEntity.isAlive()) {
            MerryEvents.LOGGER.info("[PersonalKillerEvent] Stalker slain. Terminating event for {}",
                    targetPlayer.getName().getString());
            EventManager.getInstance().stopPlayerEvent(server, targetPlayer, ID);
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