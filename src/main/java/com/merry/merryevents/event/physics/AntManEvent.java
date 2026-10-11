package com.merry.merryevents.event.physics;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific volumetric compression event belonging to EventCategory.PHYSICS.
 * Scales down the target player's hitbox and visual dimensions to ~0.45-0.5 blocks
 * via the native GENERIC_SCALE attribute modifier.
 */
public final class AntManEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("ant_man");

    private static final Identifier SCALE_MODIFIER_ID = MerryEvents.id("ant_man_scale");
    // -0.75 reduces standard scale 1.0 down to 0.25 (player height becomes ~0.45 blocks)
    private static final EntityAttributeModifier SCALE_MODIFIER = new EntityAttributeModifier(
            AntManEvent.SCALE_MODIFIER_ID,
            -0.75,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    @Override
    public Identifier getId() {
        return AntManEvent.ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.PHYSICS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            applyModifier(targetPlayer);
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.8F);
            MerryEvents.LOGGER.info("[AntManEvent] Player {} scaled down to sub-block miniature dimensions.",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
                             int remainingTicks) {
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            removeModifier(targetPlayer);
            targetPlayer.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 0.6F);
            MerryEvents.LOGGER.info("[AntManEvent] Player {} restored to standard volumetric baseline.",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer,
                                EventContext context) {
        applyModifier(newPlayer);
    }

    private static void applyModifier(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (attr != null && attr.getModifier(AntManEvent.SCALE_MODIFIER_ID) == null) {
            attr.addTemporaryModifier(AntManEvent.SCALE_MODIFIER);
        }
    }

    private static void removeModifier(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (attr != null) {
            attr.removeModifier(AntManEvent.SCALE_MODIFIER_ID);
        }
    }
}