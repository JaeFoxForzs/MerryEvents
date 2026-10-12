package com.merry.merryevents.util;

import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.config.EventConfigEntry;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Procedural generator assembling localized in-game reference books for all
 * registered chaos events.
 */
public final class ChaosBookGenerator {
    private static final int EVENTS_PER_PAGE = 2;

    private ChaosBookGenerator() {
    }

    /**
     * Synthesizes an authoritative Written Book item populated with cataloged
     * events, categories, and weights.
     *
     * @return Fully composed Written Book ItemStack
     */
    public static ItemStack createGuideBook() {
        List<RawFilteredPair<Text>> pages = new ArrayList<>();

        // Page 1: Cover & Table of Contents Intro
        MutableText cover = Text.empty();
        cover.append(Text.translatable("book.merryevents.title").formatted(Formatting.DARK_RED, Formatting.BOLD));
        cover.append(Text.literal("\n\n"));
        cover.append(Text.translatable("book.merryevents.intro").formatted(Formatting.DARK_GRAY));
        pages.add(RawFilteredPair.of(cover));

        Map<Identifier, ChaosEvent> registry = EventManager.getInstance().getRegistry();

        for (EventCategory category : EventCategory.values()) {
            List<ChaosEvent> categoryEvents = new ArrayList<>();
            for (ChaosEvent event : registry.values()) {
                if (event.getCategories().contains(category)) {
                    categoryEvents.add(event);
                }
            }

            if (categoryEvents.isEmpty()) {
                continue;
            }

            categoryEvents.sort(Comparator.comparing(e -> e.getId().toString()));

            for (int i = 0; i < categoryEvents.size(); i += ChaosBookGenerator.EVENTS_PER_PAGE) {
                MutableText page = Text.empty();

                // Category Header at the top of each page
                page.append(Text.literal("[").formatted(Formatting.DARK_GRAY));
                page.append(category.getDisplayName().copy().formatted(Formatting.DARK_BLUE, Formatting.BOLD));
                page.append(Text.literal("]\n\n").formatted(Formatting.DARK_GRAY));

                for (int j = i; j < Math.min(i + ChaosBookGenerator.EVENTS_PER_PAGE, categoryEvents.size()); j++) {
                    ChaosEvent event = categoryEvents.get(j);
                    EventConfigEntry cfg = ConfigManager.get().getEvents().get(event.getId().toString());

                    int weight = cfg != null ? cfg.weight() : event.getDefinition().defaultWeight();

                    int minSec = cfg != null ? cfg.minDurationSeconds()
                            : (event.getDefinition().defaultDurationTicks() / 20);
                    int maxSec = cfg != null ? cfg.maxDurationSeconds() : minSec;
                    String durationStr = (minSec == maxSec) ? String.valueOf(minSec) : (minSec + "-" + maxSec);

                    Formatting titleColor = event.isGlobal() ? Formatting.GOLD : Formatting.DARK_AQUA;

                    page.append(Text.literal("• ").formatted(Formatting.BLACK));
                    page.append(event.getDisplayName().copy().formatted(titleColor, Formatting.BOLD));
                    page.append(Text.literal("\n"));

                    page.append(Text.literal("  " + event.getId().getPath()).formatted(Formatting.DARK_GRAY));
                    page.append(Text.literal("\n"));

                    MutableText scopeText = null;
                    if (event.isGlobal()) {
                        scopeText = Text.translatable("command.merryevents.events.scope.global");
                    } else {
                        scopeText = Text.translatable("command.merryevents.events.scope.player");
                    }

                    MutableText durationText = Text.translatable("book.merryevents.duration", durationStr);

                    page.append(Text
                            .translatable("book.merryevents.entry_meta", scopeText, String.valueOf(weight),
                                    durationText)
                            .formatted(Formatting.DARK_GRAY));
                    page.append(Text.literal("\n\n"));
                }

                pages.add(RawFilteredPair.of(page));
            }
        }

        WrittenBookContentComponent content = new WrittenBookContentComponent(
                RawFilteredPair.of("MerryEvents Guide"),
                "MerryEvents",
                0,
                pages,
                true);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, content);
        book.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.merryevents.guide_book").formatted(Formatting.GOLD, Formatting.BOLD));

        return book;
    }
}