package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Client visual effect driver shuffling button labels on in-game screens.
 * Recursively traverses nested parent elements and restores original labels
 * upon termination.
 */
public final class ScrambledButtonsClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;
    private static final Map<ClickableWidget, Text> ORIGINAL_TEXTS = new WeakHashMap<>();

    /**
     * Queries whether menu button labels should currently be scrambled.
     *
     * @return True if button scramble effect is active
     */
    public static boolean isActive() {
        return ScrambledButtonsClientEffect.active;
    }

    /**
     * Recursively traverses and scrambles all clickable widgets present on the
     * target screen.
     *
     * @param screen Target screen whose widgets will be scrambled
     */
    public static void scrambleScreen(Screen screen) {
        if (screen == null) {
            return;
        }

        List<ClickableWidget> widgets = new ArrayList<>();
        Set<Element> visited = new HashSet<>();

        for (Element element : screen.children()) {
            ScrambledButtonsClientEffect.collectClickableWidgets(element, widgets, visited);
        }

        if (widgets.size() <= 1) {
            return;
        }

        List<Text> messages = new ArrayList<>(widgets.size());
        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            ScrambledButtonsClientEffect.ORIGINAL_TEXTS.putIfAbsent(widget, widget.getMessage());
            messages.add(widget.getMessage());
        }

        Collections.shuffle(messages, new Random());

        for (int i = 0; i < widgets.size(); i++) {
            widgets.get(i).setMessage(messages.get(i));
        }
    }

    /**
     * Restores original pre-scramble button texts on the currently displayed
     * screen.
     *
     * @param screen Target screen whose widgets will be restored
     */
    public static void restoreScreen(Screen screen) {
        if (screen == null || ScrambledButtonsClientEffect.ORIGINAL_TEXTS.isEmpty()) {
            return;
        }

        List<ClickableWidget> widgets = new ArrayList<>();
        Set<Element> visited = new HashSet<>();

        for (Element element : screen.children()) {
            ScrambledButtonsClientEffect.collectClickableWidgets(element, widgets, visited);
        }

        for (int i = 0; i < widgets.size(); i++) {
            ClickableWidget widget = widgets.get(i);
            Text original = ScrambledButtonsClientEffect.ORIGINAL_TEXTS.remove(widget);
            if (original != null) {
                widget.setMessage(original);
            }
        }
    }

    private static void collectClickableWidgets(Element element, List<ClickableWidget> result, Set<Element> visited) {
        if (element == null || !visited.add(element)) {
            return;
        }

        if (element instanceof ClickableWidget clickable && !clickable.getMessage().getString().isEmpty()) {
            result.add(clickable);
        }

        if (element instanceof ParentElement parent) {
            for (Element child : parent.children()) {
                ScrambledButtonsClientEffect.collectClickableWidgets(child, result, visited);
            }
        }
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        ScrambledButtonsClientEffect.active = true;
        if (client.currentScreen != null) {
            ScrambledButtonsClientEffect.scrambleScreen(client.currentScreen);
        }
    }

    @Override
    public void onStop(MinecraftClient client) {
        ScrambledButtonsClientEffect.active = false;
        if (client.currentScreen != null) {
            ScrambledButtonsClientEffect.restoreScreen(client.currentScreen);
        }
        ScrambledButtonsClientEffect.ORIGINAL_TEXTS.clear();
    }
}