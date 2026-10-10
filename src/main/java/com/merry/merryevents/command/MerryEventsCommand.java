package com.merry.merryevents.command;

import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.engine.ChaosDirector;
import com.merry.merryevents.engine.EventManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

public final class MerryEventsCommand {
    private static final int DEFAULT_DURATION_SECONDS = 30;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("merry")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("start")
                        .then(CommandManager.argument("eventId", IdentifierArgumentType.identifier())
                                .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(
                                        EventManager.getInstance().getRegisteredEventIds(), builder))
                                .executes(ctx -> {
                                    Identifier eventId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                    return executeStart(ctx.getSource(), eventId, DEFAULT_DURATION_SECONDS, null);
                                })
                                .then(CommandManager.argument("durationSeconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> {
                                            Identifier eventId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                            int seconds = IntegerArgumentType.getInteger(ctx, "durationSeconds");
                                            return executeStart(ctx.getSource(), eventId, seconds, null);
                                        })
                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    Identifier eventId = IdentifierArgumentType.getIdentifier(ctx,
                                                            "eventId");
                                                    int seconds = IntegerArgumentType.getInteger(ctx,
                                                            "durationSeconds");
                                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx,
                                                            "target");
                                                    return executeStart(ctx.getSource(), eventId, seconds, player);
                                                })))))
                .then(CommandManager.literal("stop")
                        .then(CommandManager.argument("eventId", IdentifierArgumentType.identifier())
                                .suggests((ctx, builder) -> {
                                    java.util.Set<Identifier> active = new java.util.HashSet<>(
                                            EventManager.getInstance().getActiveGlobalEventIds());
                                    if (ctx.getSource().isExecutedByPlayer()) {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        if (player != null) {
                                            var pMap = EventManager.getInstance().getActivePlayerEvents()
                                                    .get(player.getUuid());
                                            if (pMap != null) {
                                                active.addAll(pMap.keySet());
                                            }
                                        }
                                    }
                                    return CommandSource.suggestIdentifiers(active, builder);
                                })
                                .executes(ctx -> {
                                    Identifier eventId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                    ServerCommandSource source = ctx.getSource();
                                    return executeStop(source, eventId, null);
                                })
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            Identifier eventId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                            return executeStop(ctx.getSource(), eventId, target);
                                        }))))
                .then(CommandManager.literal("status")
                        .executes(ctx -> executeStatus(ctx.getSource())))
                .then(CommandManager.literal("director")
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    ChaosDirector director = ChaosDirector.getInstance();
                                    boolean enabled = ConfigManager.get().isAutoDirectorEnabled();
                                    int remainingSec = Math.max(0, director.getTicksUntilNextEvent() / 20);

                                    Text enabledText = Text.translatable(enabled ? "gui.yes" : "gui.no")
                                            .formatted(enabled ? Formatting.GREEN : Formatting.RED);

                                    source.sendMessage(Text.translatable("command.merryevents.director.header")
                                            .formatted(Formatting.GOLD));
                                    source.sendMessage(Text.translatable("command.merryevents.director.status.enabled",
                                            enabledText));
                                    source.sendMessage(Text
                                            .translatable("command.merryevents.director.status.countdown", remainingSec)
                                            .formatted(Formatting.YELLOW));
                                    source.sendMessage(Text.translatable("command.merryevents.director.status.deck",
                                            director.getDeck().size()).formatted(Formatting.AQUA));
                                    return 1;
                                }))
                        .then(CommandManager.literal("enable")
                                .executes(ctx -> {
                                    ConfigManager.get().setAutoDirectorEnabled(true);
                                    ConfigManager.save();
                                    ChaosDirector.getInstance().initializeCountdown();
                                    ctx.getSource().sendMessage(Text.translatable("command.merryevents.director.enable")
                                            .formatted(Formatting.GREEN));
                                    return 1;
                                }))
                        .then(CommandManager.literal("disable")
                                .executes(ctx -> {
                                    ConfigManager.get().setAutoDirectorEnabled(false);
                                    ConfigManager.save();
                                    ctx.getSource()
                                            .sendMessage(Text.translatable("command.merryevents.director.disable")
                                                    .formatted(Formatting.RED));
                                    return 1;
                                }))
                        .then(CommandManager.literal("skip")
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    boolean triggered = ChaosDirector.getInstance()
                                            .dispatchNextEvent(source.getServer());
                                    ChaosDirector.getInstance().initializeCountdown();
                                    if (triggered) {
                                        source.sendMessage(
                                                Text.translatable("command.merryevents.director.skip.success")
                                                        .formatted(Formatting.GREEN));
                                        return 1;
                                    } else {
                                        source.sendError(Text.translatable("command.merryevents.director.skip.error"));
                                        return 0;
                                    }
                                })))
                .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            ConfigManager.load();
                            ConfigManager.syncRegisteredEvents(
                                    EventManager.getInstance().getActiveGlobalEvents().isEmpty() ? null : null);
                            ChaosDirector.getInstance().refillDeck();
                            ctx.getSource().sendMessage(Text.translatable("command.merryevents.reload.success")
                                    .formatted(Formatting.GREEN));
                            return 1;
                        })));
    }

    private static int executeStop(ServerCommandSource source, Identifier eventId,
            @Nullable ServerPlayerEntity explicitTarget) {
        MinecraftServer server = source.getServer();
        EventManager manager = EventManager.getInstance();

        if (explicitTarget != null) {
            boolean stopped = manager.stopPlayerEvent(server, explicitTarget, eventId);
            if (stopped) {
                source.sendMessage(Text.translatable("command.merryevents.stop.success.player", eventId.toString(),
                        explicitTarget.getName().getString()).formatted(Formatting.YELLOW));
                return 1;
            } else {
                source.sendError(Text.translatable("command.merryevents.stop.error.not_active_player",
                        eventId.toString(), explicitTarget.getName().getString()));
                return 0;
            }
        }

        boolean stoppedGlobal = manager.stopGlobalEvent(server, eventId);
        boolean stoppedPlayer = false;

        if (source.isExecutedByPlayer()) {
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                stoppedPlayer = manager.stopPlayerEvent(server, player, eventId);
            }
        }

        if (stoppedGlobal || stoppedPlayer) {
            source.sendMessage(Text.translatable("command.merryevents.stop.success", eventId.toString())
                    .formatted(Formatting.YELLOW));
            return 1;
        } else {
            source.sendError(Text.translatable("command.merryevents.stop.error.not_active", eventId.toString()));
            return 0;
        }
    }

    private static int executeStatus(ServerCommandSource source) {
        EventManager manager = EventManager.getInstance();
        source.sendMessage(Text.translatable("command.merryevents.status.header").formatted(Formatting.GOLD));

        source.sendMessage(Text.translatable("command.merryevents.status.global_header").formatted(Formatting.YELLOW));
        var activeGlobals = manager.getActiveGlobalEvents();
        if (activeGlobals.isEmpty()) {
            source.sendMessage(Text.translatable("command.merryevents.status.none").formatted(Formatting.DARK_GRAY));
        } else {
            for (Map.Entry<Identifier, EventManager.ActiveEventInstance> entry : activeGlobals.entrySet()) {
                ChaosEvent event = entry.getValue().getEvent();
                int remainingSeconds = Math.max(0, entry.getValue().getRemainingTicks() / 20);
                source.sendMessage(Text.translatable("command.merryevents.status.event_entry",
                        event.getDisplayName(),
                        event.getCategory().getDisplayName(),
                        remainingSeconds).formatted(Formatting.GREEN));
            }
        }

        if (source.isExecutedByPlayer()) {
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                source.sendMessage(
                        Text.translatable("command.merryevents.status.player_header").formatted(Formatting.AQUA));
                var pMap = manager.getActivePlayerEvents().get(player.getUuid());
                if (pMap == null || pMap.isEmpty()) {
                    source.sendMessage(
                            Text.translatable("command.merryevents.status.none").formatted(Formatting.DARK_GRAY));
                } else {
                    for (EventManager.ActiveEventInstance instance : pMap.values()) {
                        int remainingSeconds = Math.max(0, instance.getRemainingTicks() / 20);
                        source.sendMessage(Text.translatable("command.merryevents.status.event_entry",
                                instance.getEvent().getDisplayName(),
                                instance.getEvent().getCategory().getDisplayName(),
                                remainingSeconds).formatted(Formatting.LIGHT_PURPLE));
                    }
                }
            }
        }

        List<Text> activeCategoryNames = new ArrayList<>();
        for (EventCategory cat : EventCategory.values()) {
            if (manager.isCategoryOccupiedGlobally(cat)) {
                activeCategoryNames.add(cat.getDisplayName());
            }
        }

        MutableText categoriesText = Text.empty();
        if (activeCategoryNames.isEmpty()) {
            categoriesText.append(
                    Text.translatable("command.merryevents.status.no_categories").formatted(Formatting.DARK_GRAY));
        } else {
            for (int i = 0; i < activeCategoryNames.size(); i++) {
                if (i > 0)
                    categoriesText.append(Text.literal(", ").formatted(Formatting.GRAY));
                categoriesText.append(activeCategoryNames.get(i).copy().formatted(Formatting.YELLOW));
            }
        }
        source.sendMessage(
                Text.translatable("command.merryevents.status.categories", categoriesText).formatted(Formatting.GRAY));

        ChaosDirector director = ChaosDirector.getInstance();
        boolean directorEnabled = ConfigManager.get().isAutoDirectorEnabled();
        int nextSeconds = Math.max(0, director.getTicksUntilNextEvent() / 20);
        Text dirStatus = Text.translatable(directorEnabled ? "gui.yes" : "gui.no")
                .formatted(directorEnabled ? Formatting.GREEN : Formatting.RED);
        source.sendMessage(Text.translatable("command.merryevents.status.director",
                dirStatus,
                nextSeconds,
                director.getDeck().size()).formatted(Formatting.DARK_AQUA));

        return 1;
    }

    private static int executeStart(ServerCommandSource source, Identifier eventId, int seconds,
            ServerPlayerEntity explicitTarget) {
        ChaosEvent event = EventManager.getInstance().getEvent(eventId);
        if (event == null) {
            source.sendError(Text.translatable("command.merryevents.start.error.unknown", eventId.toString()));
            return 0;
        }

        int durationTicks = seconds * 20;

        if (event.isGlobal()) {
            boolean started = EventManager.getInstance().startGlobalEvent(source.getServer(), eventId, durationTicks);
            if (started) {
                source.sendMessage(
                        Text.translatable("command.merryevents.start.success.global", event.getDisplayName(), seconds)
                                .formatted(Formatting.GREEN));
                return 1;
            } else {
                source.sendError(Text.translatable("command.merryevents.start.error.category_global",
                        event.getCategory().getDisplayName()));
                return 0;
            }
        } else {
            ServerPlayerEntity target;
            if (explicitTarget != null) {
                target = explicitTarget;
            } else if (source.isExecutedByPlayer()) {
                target = source.getPlayer();
            } else {
                source.sendError(Text.translatable("command.merryevents.start.error.no_player"));
                return 0;
            }

            if (target == null) {
                source.sendError(Text.translatable("command.merryevents.start.error.no_player"));
                return 0;
            }

            boolean started = EventManager.getInstance().startPlayerEvent(source.getServer(), target, eventId,
                    durationTicks);
            if (started) {
                source.sendMessage(Text.translatable("command.merryevents.start.success.player", event.getDisplayName(),
                        target.getName().getString(), seconds).formatted(Formatting.AQUA));
                return 1;
            } else {
                source.sendError(Text.translatable("command.merryevents.start.error.category_player",
                        event.getCategory().getDisplayName(), target.getName().getString()));
                return 0;
            }
        }
    }
}