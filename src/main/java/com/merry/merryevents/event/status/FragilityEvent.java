package com.merry.merryevents.event.status;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Player-specific vulnerability event belonging to EventCategory.STATUS.
 * Enforces a temporary attribute modifier reducing maximum health to 3 hearts.
 */
public final class FragilityEvent implements ChaosEvent {
    private static final Identifier MODIFIER_ID = MerryEvents.id("fragility_health_penalty");
    private static final EntityAttributeModifier PENALTY_MODIFIER = new EntityAttributeModifier(
            MODIFIER_ID,
            -14.0,
            EntityAttributeModifier.Operation.ADD_VALUE);

    @Override
    public Identifier getId() {
        return EventIds.FRAGILITY;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.STATUS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            applyModifier(targetPlayer);
            MerryEvents.LOGGER.info("[FragilityEvent] Player {} vitality reduced to 3 hearts via attribute modifier.",
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
            MerryEvents.LOGGER.info("[FragilityEvent] Player {} vitality attribute restored.",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onPlayerRespawn(MinecraftServer server, ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer,
            EventContext context) {
        applyModifier(newPlayer);
    }

    private void applyModifier(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(MODIFIER_ID);
            attr.addTemporaryModifier(PENALTY_MODIFIER);
            if (player.getHealth() > 6.0F) {
                player.setHealth(6.0F);
            }
        }
    }

    private void removeModifier(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(MODIFIER_ID);
        }
    }
}