package com.merry.merryevents.client.effect;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.ClientEventManager;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.item.ChaosMemoItem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Autonomous, high-performance UI overlay rendering the forbidden blocks guide.
 * Operates independently of server event lifecycles to allow persistent
 * inspection
 * of memo items with pre-cached layout data.
 */
public final class DontStepOnClientEffect implements ClientChaosEffect {
    private static final Set<String> ACTIVE_TARGET_PLAYERS = new HashSet<>();
    private static final MemoRenderCache CACHE = new MemoRenderCache();
    private static boolean registered = false;

    /**
     * Initializes global HUD and inventory screen render callbacks.
     */
    public static void init() {
        if (registered) {
            return;
        }

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen == null) {
                renderOverlay(context, -1000, -1000);
            }
        });

        ScreenEvents.AFTER_INIT.register((clientInstance, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?>) {
                ScreenEvents.afterRender(screen).register((scr, context, mouseX, mouseY, tickDelta) -> {
                    renderOverlay(context, mouseX, mouseY);
                });
            }
        });

        registered = true;
    }

    /**
     * Updates authoritative server-side active status for a specific player
     * profile.
     *
     * @param playerName Target player nickname
     * @param active     True if restriction is active, false if cleared
     */
    public static void setPlayerRestrictionStatus(String playerName, boolean active) {
        if (playerName == null || playerName.isEmpty()) {
            if (!active) {
                ACTIVE_TARGET_PLAYERS.clear();
            }
            return;
        }
        String normalized = playerName.toLowerCase(Locale.ROOT);
        if (active) {
            ACTIVE_TARGET_PLAYERS.add(normalized);
        } else {
            ACTIVE_TARGET_PLAYERS.remove(normalized);
        }
    }

    /**
     * Evaluates whether the given player name currently suffers from active floor
     * hazards.
     *
     * @param playerName Target player nickname to evaluate
     * @return True if player is actively tracked in hazard set
     */
    public static boolean isPlayerRestricted(String playerName) {
        return !playerName.isEmpty() && ACTIVE_TARGET_PLAYERS.contains(playerName.toLowerCase(Locale.ROOT));
    }

    /**
     * Resets active hazard tracker sets upon server disconnection.
     */
    public static void reset() {
        ACTIVE_TARGET_PLAYERS.clear();
        CACHE.clear();
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
    }

    @Override
    public void onStop(MinecraftClient client) {
    }

    /**
     * Renders the memo guide overlay whenever the item is actively held in hands or
     * cursor.
     *
     * @param context Active graphical draw context
     * @param mouseX  Interpolated mouse X coordinate or negative value when locked
     * @param mouseY  Interpolated mouse Y coordinate or negative value when locked
     */
    private static void renderOverlay(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || ClientEventManager.isHudHidden()) {
            return;
        }

        ItemStack memoStack = getActiveMemoStack(client.player);
        if (memoStack.isEmpty()) {
            return;
        }

        CACHE.updateIfChanged(client, memoStack);
        if (CACHE.itemStacks.isEmpty()) {
            return;
        }

        boolean isEventActive = isPlayerRestricted(CACHE.ownerName);

        int startX = 10;
        int startY = 10;
        int borderColor = isEventActive ? 0xFFFF5555 : 0xFF777777;

        context.fill(startX, startY, startX + CACHE.boxWidth, startY + CACHE.boxHeight, 0xD0141414);
        context.drawBorder(startX, startY, CACHE.boxWidth, CACHE.boxHeight, borderColor);

        context.drawText(client.textRenderer, CACHE.title, startX + 8, startY + 6, 0xFFFFFF, true);
        context.drawText(client.textRenderer, CACHE.ownerText, startX + 8, startY + 18, 0xFFFFFF, true);

        Text statusBadge = isEventActive
                ? Text.translatable("ui.merryevents.dont_step_on.status.active").formatted(Formatting.GREEN,
                        Formatting.BOLD)
                : Text.translatable("ui.merryevents.dont_step_on.status.inactive").formatted(Formatting.RED,
                        Formatting.BOLD);
        Text statusText = Text.translatable("ui.merryevents.dont_step_on.status_label").formatted(Formatting.GRAY)
                .append(statusBadge);
        context.drawText(client.textRenderer, statusText, startX + 8, startY + 29, 0xFFFFFF, true);

        context.fill(startX + 6, startY + 41, startX + CACHE.boxWidth - 6, startY + 42, 0x40FFFFFF);
        context.drawText(client.textRenderer, CACHE.sectionHeader, startX + 8, startY + 45, 0xFFFFFF, true);

        int gridStartX = startX + (CACHE.boxWidth - CACHE.gridWidth) / 2;
        int gridStartY = startY + 56;

        boolean canInteractWithMouse = client.currentScreen instanceof HandledScreen && mouseX >= 0 && mouseY >= 0;
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < CACHE.itemStacks.size(); i++) {
            int x = gridStartX + (i % 6) * 18;
            int y = gridStartY + (i / 6) * 18;
            ItemStack stack = CACHE.itemStacks.get(i);
            context.drawItem(stack, x, y);

            if (canInteractWithMouse && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                hoveredStack = stack;
            }
        }

        if (canInteractWithMouse && !hoveredStack.isEmpty()) {
            context.drawItemTooltip(client.textRenderer, hoveredStack, mouseX, mouseY);
        }
    }

    /**
     * Resolves the active memo item stack present in main hand, off hand, or mouse
     * cursor.
     *
     * @param player Target client player instance
     * @return Memo ItemStack if actively held or dragged, otherwise ItemStack.EMPTY
     */
    private static ItemStack getActiveMemoStack(PlayerEntity player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandStack();
        if (main.isOf(MerryEvents.CHAOS_MEMO)) {
            return main;
        }
        ItemStack off = player.getOffHandStack();
        if (off.isOf(MerryEvents.CHAOS_MEMO)) {
            return off;
        }
        if (player.currentScreenHandler != null) {
            ItemStack cursor = player.currentScreenHandler.getCursorStack();
            if (cursor.isOf(MerryEvents.CHAOS_MEMO)) {
                return cursor;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Zero-allocation cache preserving pre-instantiated ItemStacks and layout
     * measurements.
     */
    private static final class MemoRenderCache {
        private ItemStack lastSource = ItemStack.EMPTY;
        final List<ItemStack> itemStacks = new ArrayList<>();
        String ownerName = "";
        Text title = Text.empty();
        Text ownerText = Text.empty();
        Text sectionHeader = Text.empty();
        int boxWidth = 0;
        int boxHeight = 0;
        int gridWidth = 0;

        void updateIfChanged(MinecraftClient client, ItemStack currentStack) {
            if (!currentStack.isEmpty() && ItemStack.areEqual(this.lastSource, currentStack)) {
                return;
            }

            if (currentStack.isEmpty()) {
                return;
            }

            this.lastSource = currentStack.copy();
            this.itemStacks.clear();

            List<Identifier> blocks = ChaosMemoItem.getForbiddenBlocks(currentStack);
            for (int i = 0; i < blocks.size(); i++) {
                Item item = Registries.ITEM.get(blocks.get(i));
                if (item != Items.AIR) {
                    this.itemStacks.add(new ItemStack(item));
                }
            }

            this.ownerName = ChaosMemoItem.getOwner(currentStack);
            this.title = currentStack.getName();

            Text targetValue = this.ownerName.isEmpty()
                    ? Text.translatable("ui.merryevents.dont_step_on.target_unknown").formatted(Formatting.GRAY)
                    : Text.literal(this.ownerName).formatted(Formatting.YELLOW);
            this.ownerText = Text.translatable("ui.merryevents.dont_step_on.target", targetValue)
                    .formatted(Formatting.GRAY);
            this.sectionHeader = Text.translatable("ui.merryevents.dont_step_on.title").formatted(Formatting.RED,
                    Formatting.BOLD);

            int cols = 6;
            int total = this.itemStacks.size();
            int rows = (int) Math.ceil((double) total / cols);

            this.gridWidth = Math.min(total, cols) * 18 - 2;
            int gridHeight = rows * 18 - 2;

            Text dummyStatus = Text.translatable("ui.merryevents.dont_step_on.status_label")
                    .append(Text.translatable("ui.merryevents.dont_step_on.status.inactive"));

            int maxTextWidth = Math.max(client.textRenderer.getWidth(this.title),
                    Math.max(client.textRenderer.getWidth(this.ownerText),
                            Math.max(client.textRenderer.getWidth(dummyStatus),
                                    client.textRenderer.getWidth(this.sectionHeader))));

            this.boxWidth = Math.max(this.gridWidth, maxTextWidth) + 16;
            this.boxHeight = 56 + gridHeight + 6;
        }

        void clear() {
            this.lastSource = ItemStack.EMPTY;
            this.itemStacks.clear();
            this.ownerName = "";
        }
    }
}