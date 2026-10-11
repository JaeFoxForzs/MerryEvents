package com.merry.merryevents.event.world;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import com.merry.merryevents.network.ForbiddenBlocksPayload;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-specific restriction event belonging to EventCategory.WORLD.
 * Restricts movement over pseudorandomly chosen blocks, persists hazard data
 * into
 * the Chaos Memo item, and strictly confines damage calculation to the target
 * player.
 */
public final class DontStepOnEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("dont_step_on");
    private static final String BLOCKS_KEY = "forbidden_blocks";

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.WORLD;
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

        ReferenceOpenHashSet<Block> forbiddenBlocks = new ReferenceOpenHashSet<>();
        List<Identifier> networkedIds = new ArrayList<>();
        List<Block> validBlocks = new ArrayList<>();

        for (Block block : Registries.BLOCK) {
            Item item = block.asItem();
            if (item != Items.AIR) {
                validBlocks.add(block);
            }
        }

        Random random = targetPlayer.getRandom();
        int count = 16 + random.nextInt(49);

        while (forbiddenBlocks.size() < count && !validBlocks.isEmpty()) {
            Block candidate = validBlocks.get(random.nextInt(validBlocks.size()));
            if (forbiddenBlocks.add(candidate)) {
                networkedIds.add(Registries.ITEM.getId(candidate.asItem()));
            }
        }

        context.set(BLOCKS_KEY, forbiddenBlocks);

        String targetName = targetPlayer.getGameProfile().getName();
        ForbiddenBlocksPayload syncPayload = new ForbiddenBlocksPayload(targetName, true);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, syncPayload);
        }

        ItemStack memo = new ItemStack(MerryEvents.CHAOS_MEMO);
        String uniqueTag = String.format("%06X", random.nextInt(0x1000000));
        Text memoName = Text.translatable("item.merryevents.chaos_memo.named", uniqueTag).formatted(Formatting.GOLD,
                Formatting.BOLD);

        memo.set(DataComponentTypes.CUSTOM_NAME, memoName);
        memo.set(MerryEvents.FORBIDDEN_BLOCKS_COMPONENT, networkedIds);
        memo.set(MerryEvents.MEMO_OWNER_COMPONENT, targetName);

        targetPlayer.giveItemStack(memo);
        targetPlayer.sendMessage(
                Text.translatable("event.merryevents.dont_step_on.warning").formatted(Formatting.RED, Formatting.BOLD),
                false);

        MerryEvents.LOGGER.info("[DontStepOnEvent] Restricted {} blocks for {} with tag #{}", forbiddenBlocks.size(),
                targetName, uniqueTag);
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks % 10 != 0) {
            return;
        }

        ReferenceOpenHashSet<Block> forbiddenBlocks = context.getOrDefault(BLOCKS_KEY, null);
        if (forbiddenBlocks == null) {
            return;
        }

        BlockState steppingState = targetPlayer.getSteppingBlockState();
        if (forbiddenBlocks.contains(steppingState.getBlock())) {
            float maxHealth = targetPlayer.getMaxHealth();
            float percentage = 0.10F + (targetPlayer.getRandom().nextFloat() * 0.80F);
            float damage = maxHealth * percentage;

            targetPlayer.damage(targetPlayer.getServerWorld().getDamageSources().magic(), damage);
        }
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        String targetName = targetPlayer != null ? targetPlayer.getGameProfile().getName() : "";
        ForbiddenBlocksPayload stopPayload = new ForbiddenBlocksPayload(targetName, false);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, stopPayload);
        }

        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[DontStepOnEvent] Hazard restrictions normalized for {}", targetName);
        }
    }
}