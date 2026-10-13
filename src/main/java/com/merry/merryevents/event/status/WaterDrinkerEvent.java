package com.merry.merryevents.event.status;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.potion.Potions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Personal metabolism inversion event belonging to EventCategory.STATUS.
 * Suppresses food nourishment, enforces hydration hunger satisfaction, and
 * reports dry throat complaints into chat.
 */
public final class WaterDrinkerEvent implements ChaosEvent {

    /**
     * Dedicated-server safe hook initialized exclusively on the client side.
     */
    public static BooleanSupplier clientActiveSupplier = () -> false;

    @Override
    public Identifier getId() {
        return EventIds.WATER_DRINKER;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.STATUS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    /**
     * Queries whether the water drinker restriction is actively running on the
     * client side.
     *
     * @return True if water drinker state is active for the local player
     */
    public static boolean isClientActive() {
        return WaterDrinkerEvent.clientActiveSupplier.getAsBoolean();
    }

    /**
     * Evaluates whether the water drinker restriction is actively running for the
     * target player.
     *
     * @param player Server player instance to query
     * @return True if event is currently active on the target player
     */
    public static boolean isPlayerActive(ServerPlayerEntity player) {
        return EventManager.getInstance().isPlayerEventActive(player.getUuid(), EventIds.WATER_DRINKER);
    }

    /**
     * Resolves the translatable refusal dialogue text matching the given random
     * variant index.
     *
     * @param index Deterministic random phrase index from 1 to 10
     * @return Translatable text component for the selected refusal line
     */
    private static MutableText getRefusalText(int index) {
        return switch (index) {
            case 1 -> Text.translatable("event.merryevents.water_drinker.refuse.1");
            case 2 -> Text.translatable("event.merryevents.water_drinker.refuse.2");
            case 3 -> Text.translatable("event.merryevents.water_drinker.refuse.3");
            case 4 -> Text.translatable("event.merryevents.water_drinker.refuse.4");
            case 5 -> Text.translatable("event.merryevents.water_drinker.refuse.5");
            case 6 -> Text.translatable("event.merryevents.water_drinker.refuse.6");
            case 7 -> Text.translatable("event.merryevents.water_drinker.refuse.7");
            case 8 -> Text.translatable("event.merryevents.water_drinker.refuse.8");
            case 9 -> Text.translatable("event.merryevents.water_drinker.refuse.9");
            default -> Text.translatable("event.merryevents.water_drinker.refuse.10");
        };
    }

    /**
     * Dispatches a randomized refusal dialogue message directly into the chat
     * window and forces hunger state resynchronization.
     *
     * @param player Target player consuming solid food
     */
    public static void onConsumeFood(ServerPlayerEntity player) {
        int index = player.getRandom().nextInt(10) + 1;
        player.sendMessage(WaterDrinkerEvent.getRefusalText(index).formatted(Formatting.AQUA), false);
        player.playSoundToPlayer(SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.8F, 0.8F);

        player.networkHandler.sendPacket(new HealthUpdateS2CPacket(
                player.getHealth(),
                player.getHungerManager().getFoodLevel(),
                player.getHungerManager().getSaturationLevel()));
    }

    /**
     * Replenishes hunger and saturation when an active player drinks a valid water
     * bottle and updates client state.
     *
     * @param player Target player drinking potion
     * @param stack  Item stack consumed by the player
     */
    public static void onDrinkPotion(ServerPlayerEntity player, ItemStack stack) {
        if (!WaterDrinkerEvent.isPlayerActive(player)) {
            return;
        }

        PotionContentsComponent potion = stack.getOrDefault(DataComponentTypes.POTION_CONTENTS,
                PotionContentsComponent.DEFAULT);
        if (potion.matches(Potions.WATER)) {
            HungerManager hunger = player.getHungerManager();
            int newFood = Math.min(20, hunger.getFoodLevel() + 5);
            float newSaturation = Math.min((float) newFood, hunger.getSaturationLevel() + 4.0F);

            hunger.setFoodLevel(newFood);
            hunger.setSaturationLevel(newSaturation);

            player.networkHandler.sendPacket(new HealthUpdateS2CPacket(
                    player.getHealth(),
                    hunger.getFoodLevel(),
                    hunger.getSaturationLevel()));

            player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.6F, 1.2F);
            player.playSoundToPlayer(SoundEvents.ITEM_HONEY_BOTTLE_DRINK, SoundCategory.PLAYERS, 0.8F, 1.0F);

            MerryEvents.LOGGER.debug("[WaterDrinkerEvent] Restored hydration hunger for {} (Food: {}, Sat: {})",
                    player.getName().getString(), newFood, newSaturation);
        }
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer,
            EventContext context, int durationTicks) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[WaterDrinkerEvent] Hydration restriction active for {}",
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
            MerryEvents.LOGGER.info("[WaterDrinkerEvent] Standard dietary metabolism restored for {}",
                    targetPlayer.getName().getString());
        }
    }
}