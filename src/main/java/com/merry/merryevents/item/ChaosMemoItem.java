package com.merry.merryevents.item;

import com.merry.merryevents.MerryEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Unique utility item storing encoded forbidden block data and bound target
 * player name.
 * Right-clicking air or blocks triggers client-side reference screen inspection
 * via a safe side-isolated callback.
 */
public final class ChaosMemoItem extends Item {
    public static final String ID = "chaos_memo";
    public static ChaosMemoItem INSTANCE;

    /**
     * Side-safe client action hook initialized exclusively on the client side.
     */
    @Nullable
    public static Consumer<ItemStack> clientUseAction = null;

    /**
     * Initializes the chaos memo item with custom settings.
     *
     * @param settings Item configuration settings
     */
    public ChaosMemoItem(Settings settings) {
        super(settings);
        INSTANCE = this;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() && clientUseAction != null) {
            clientUseAction.accept(stack);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient() && clientUseAction != null) {
            clientUseAction.accept(context.getStack());
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Extracts encoded forbidden blocks from the item stack's custom data
     * component.
     *
     * @param stack Target memo ItemStack
     * @return List of block identifiers
     */
    public static List<Identifier> getForbiddenBlocks(ItemStack stack) {
        List<Identifier> blocks = stack.get(MerryEvents.FORBIDDEN_BLOCKS_COMPONENT);
        return blocks != null ? blocks : List.of();
    }

    /**
     * Extracts the target player nickname bound to this memo instance.
     *
     * @param stack Target memo ItemStack
     * @return Bound player nickname or empty string if unbound
     */
    public static String getOwner(ItemStack stack) {
        String owner = stack.get(MerryEvents.MEMO_OWNER_COMPONENT);
        return owner != null ? owner : "";
    }
}