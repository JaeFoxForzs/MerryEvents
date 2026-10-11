package com.merry.merryevents.command;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.config.ConfigManager;
import com.merry.merryevents.engine.ChaosDirector;
import com.merry.merryevents.engine.EventManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MerryEventsCommand {
    private static final int DEFAULT_DURATION_SECONDS = 30;

    private MerryEventsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("merry")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("start")
                        .then(CommandManager.argument("eventId", IdentifierArgumentType.identifier())
                                .suggests((ctx, builder) -> MerryEventsCommand.suggestEvents(builder,
                                        EventManager.getInstance().getRegisteredEventIds()))
                                .executes(ctx -> {
                                    Identifier rawId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                    return MerryEventsCommand.executeStart(ctx.getSource(), MerryEventsCommand.resolveEventId(rawId), -1, null);
                                })
                                .then(CommandManager.argument("durationSeconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> {
                                            Identifier rawId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                            int seconds = IntegerArgumentType.getInteger(ctx, "durationSeconds");
                                            return MerryEventsCommand.executeStart(ctx.getSource(), MerryEventsCommand.resolveEventId(rawId), seconds, null);
                                        })
                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                .executes(ctx -> {
                                                    Identifier rawId = IdentifierArgumentType.getIdentifier(ctx,
                                                            "eventId");
                                                    int seconds = IntegerArgumentType.getInteger(ctx,
                                                            "durationSeconds");
                                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx,
                                                            "target");
                                                    return MerryEventsCommand.executeStart(ctx.getSource(), MerryEventsCommand.resolveEventId(rawId), seconds,
                                                            player);
                                                })))))
                .then(CommandManager.literal("stop")
                        .then(CommandManager.argument("eventId", IdentifierArgumentType.identifier())
                                .suggests((ctx, builder) -> {
                                    Set<Identifier> active = new HashSet<>(
                                            EventManager.getInstance().getActiveGlobalEventIds());
                                    if (ctx.getSource().isExecutedByPlayer()) {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        if (player != null) {
                                            var playerMap = EventManager.getInstance().getActivePlayerEvents()
                                                    .get(player.getUuid());
                                            if (playerMap != null) {
                                                active.addAll(playerMap.keySet());
                                            }
                                        }
                                    }
                                    return MerryEventsCommand.suggestEvents(builder, active);
                                })
                                .executes(ctx -> {
                                    Identifier rawId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                    return MerryEventsCommand.executeStop(ctx.getSource(), MerryEventsCommand.resolveEventId(rawId), null);
                                })
                                .then(CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            Identifier rawId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                                            return MerryEventsCommand.executeStop(ctx.getSource(), MerryEventsCommand.resolveEventId(rawId), target);
                                        }))))
                .then(CommandManager.literal("status")
                        .executes(ctx -> MerryEventsCommand.executeStatus(ctx.getSource())))
                .then(CommandManager.literal("events")
                        .executes(ctx -> MerryEventsCommand.executeEvents(ctx.getSource())))
                .then(CommandManager.literal("director")
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    ChaosDirector director = ChaosDirector.getInstance();
                                    boolean enabled = ConfigManager.get().isAutoDirectorEnabled();
                                    int globalRemainingSec = Math.max(0, director.getTicksUntilNextGlobalEvent() / 20);

                                    Text enabledText = Text.translatable(enabled ? "gui.yes" : "gui.no")
                                            .formatted(enabled ? Formatting.GREEN : Formatting.RED);

                                    source.sendMessage(Text.translatable("command.merryevents.director.header")
                                            .formatted(Formatting.GOLD));
                                    source.sendMessage(Text.translatable("command.merryevents.director.status.enabled",
                                            enabledText));
                                    source.sendMessage(Text.translatable("command.merryevents.director.status.global",
                                            globalRemainingSec, director.getGlobalDeck().size())
                                            .formatted(Formatting.YELLOW));

                                    if (source.isExecutedByPlayer()) {
                                        ServerPlayerEntity player = source.getPlayer();
                                        int personalRemainingSec = player != null
                                                ? Math.max(0,
                                                        director.getPlayerCountdowns().getInt(player.getUuid()) / 20)
                                                : 0;
                                        source.sendMessage(Text
                                                .translatable("command.merryevents.director.status.personal",
                                                        director.getPersonalDeck().size(), personalRemainingSec)
                                                .formatted(Formatting.AQUA));
                                    } else {
                                        source.sendMessage(Text
                                                .translatable("command.merryevents.director.status.personal_console",
                                                        director.getPersonalDeck().size(),
                                                        director.getPlayerCountdowns().size())
                                                .formatted(Formatting.AQUA));
                                    }
                                    return 1;
                                }))
                        .then(CommandManager.literal("enable")
                                .executes(ctx -> {
                                    ConfigManager.get().setAutoDirectorEnabled(true);
                                    ConfigManager.save();
                                    ChaosDirector.getInstance().initializeGlobalCountdown();
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
                                    boolean triggeredGlobal = ChaosDirector.getInstance()
                                            .dispatchNextGlobalEvent(source.getServer());
                                    ChaosDirector.getInstance().initializeGlobalCountdown();

                                    boolean triggeredPersonal = false;
                                    if (source.isExecutedByPlayer()) {
                                        ServerPlayerEntity player = source.getPlayer();
                                        if (player != null) {
                                            triggeredPersonal = ChaosDirector.getInstance()
                                                    .dispatchNextPersonalEvent(source.getServer(), player);
                                            ChaosDirector.getInstance().initializePlayerCountdown(player.getUuid());
                                        }
                                    }

                                    if (triggeredGlobal || triggeredPersonal) {
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
                            ConfigManager.syncRegisteredEvents(EventManager.getInstance().getRegistry());
                            ChaosDirector.getInstance().refillDecks();
                            ctx.getSource().sendMessage(Text.translatable("command.merryevents.reload.success")
                                    .formatted(Formatting.GREEN));
                            return 1;
                        })));
    }

    private static CompletableFuture<Suggestions> suggestEvents(SuggestionsBuilder builder,
            Iterable<Identifier> eventIds) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        boolean explicitNamespace = remaining.contains(":") || remaining.startsWith(MerryEvents.MOD_ID);

        List<String> candidates = new ArrayList<>();
        for (Identifier id : eventIds) {
            if (explicitNamespace) {
                candidates.add(id.toString());
            } else {
                candidates.add(id.getPath());
            }
        }
        return CommandSource.suggestMatching(candidates, builder);
    }

    private static Identifier resolveEventId(Identifier rawId) {
        if (Identifier.DEFAULT_NAMESPACE.equals(rawId.getNamespace())) {
            Identifier namespacedId = MerryEvents.id(rawId.getPath());
            if (EventManager.getInstance().getEvent(namespacedId) != null
                    || EventManager.getInstance().getEvent(rawId) == null) {
                return namespacedId;
            }
        }
        return rawId;
    }

    private static int executeStop(ServerCommandSource source, Identifier eventId,
            @Nullable ServerPlayerEntity explicitTarget) {
        MinecraftServer server = source.getServer();
        EventManager manager = EventManager.getInstance();
        ChaosEvent event = manager.getEvent(eventId);

        if (event == null) {
            source.sendError(Text.translatable("command.merryevents.start.error.unknown", eventId.toString()));
            return 0;
        }

        Text eventDisplayName = event.getDisplayName();

        if (explicitTarget != null) {
            boolean stopped = manager.stopPlayerEvent(server, explicitTarget, eventId);
            if (stopped) {
                source.sendMessage(Text.translatable("command.merryevents.stop.success.player", eventDisplayName,
                        explicitTarget.getName().getString()).formatted(Formatting.YELLOW));
                return 1;
            } else {
                source.sendError(Text.translatable("command.merryevents.stop.error.not_active_player",
                        eventDisplayName, explicitTarget.getName().getString()));
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
            source.sendMessage(Text.translatable("command.merryevents.stop.success", eventDisplayName)
                    .formatted(Formatting.YELLOW));
            return 1;
        } else {
            source.sendError(Text.translatable("command.merryevents.stop.error.not_active", eventDisplayName));
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

        source.sendMessage(
                Text.translatable("command.merryevents.status.categories_header").formatted(Formatting.YELLOW));
        for (EventCategory cat : EventCategory.values()) {
            List<Text> globalEvents = new ArrayList<>();
            for (EventManager.ActiveEventInstance instance : manager.getActiveGlobalEvents().values()) {
                if (instance.getEvent().getCategory() == cat) {
                    globalEvents.add(instance.getEvent().getDisplayName());
                }
            }

            List<Text> playerEvents = new ArrayList<>();
            for (Map.Entry<UUID, Map<Identifier, EventManager.ActiveEventInstance>> playerEntry : manager
                    .getActivePlayerEvents().entrySet()) {
                ServerPlayerEntity activePlayer = source.getServer().getPlayerManager()
                        .getPlayer(playerEntry.getKey());
                Text playerName = activePlayer != null
                        ? activePlayer.getName().copy()
                        : Text.literal(playerEntry.getKey().toString());
                for (EventManager.ActiveEventInstance instance : playerEntry.getValue().values()) {
                    if (instance.getEvent().getCategory() == cat) {
                        playerEvents.add(Text.translatable("command.merryevents.status.category_player_event",
                                playerName, instance.getEvent().getDisplayName()));
                    }
                }
            }

            source.sendMessage(Text.translatable("command.merryevents.status.category_entry",
                    cat.getDisplayName(),
                    MerryEventsCommand.joinOrFree(globalEvents),
                    MerryEventsCommand.joinOrFree(playerEvents)).formatted(
                            globalEvents.isEmpty() && playerEvents.isEmpty()
                                    ? Formatting.DARK_GRAY
                                    : Formatting.GRAY));
        }

        ChaosDirector director = ChaosDirector.getInstance();
        boolean directorEnabled = ConfigManager.get().isAutoDirectorEnabled();
        int nextGlobalSeconds = Math.max(0, director.getTicksUntilNextGlobalEvent() / 20);
        Text dirStatus = Text.translatable(directorEnabled ? "gui.yes" : "gui.no")
                .formatted(directorEnabled ? Formatting.GREEN : Formatting.RED);
        source.sendMessage(Text.translatable("command.merryevents.status.director",
                dirStatus,
                nextGlobalSeconds,
                director.getGlobalDeck().size(),
                director.getPersonalDeck().size()).formatted(Formatting.DARK_AQUA));

        return 1;
    }

    private static Text joinOrFree(List<Text> entries) {
        if (entries.isEmpty()) {
            return Text.translatable("command.merryevents.status.category_free").formatted(Formatting.DARK_GRAY);
        }

        MutableText joined = Text.empty();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                joined.append(Text.literal(", ").formatted(Formatting.GRAY));
            }
            joined.append(entries.get(i).copy().formatted(Formatting.WHITE));
        }
        return joined;
    }

    private static int executeEvents(ServerCommandSource source) {
        EventManager manager = EventManager.getInstance();
        List<ChaosEvent> events = new ArrayList<>(manager.getRegistry().values());
        events.sort(Comparator.comparing(event -> event.getId().toString()));

        source.sendMessage(Text.translatable("command.merryevents.events.header", events.size())
                .formatted(Formatting.GOLD));
        for (ChaosEvent event : events) {
            var config = ConfigManager.get().getEvents().get(event.getId().toString());
            boolean autoEnabled = config == null || config.enabled();
            Text scope = Text.translatable(event.isGlobal()
                    ? "command.merryevents.events.scope.global"
                    : "command.merryevents.events.scope.player");
            Text autoStart = Text.translatable(autoEnabled
                    ? "command.merryevents.events.autostart.enabled"
                    : "command.merryevents.events.autostart.disabled");
            source.sendMessage(Text.translatable("command.merryevents.events.entry",
                    event.getDisplayName(),
                    event.getId().toString(),
                    event.getCategory().getDisplayName(),
                    scope,
                    autoStart).formatted(Formatting.GRAY));
        }
        return 1;
    }

    private static int executeStart(ServerCommandSource source, Identifier eventId, int seconds,
            ServerPlayerEntity explicitTarget) {
        ChaosEvent event = EventManager.getInstance().getEvent(eventId);
        if (event == null) {
            source.sendError(Text.translatable("command.merryevents.start.error.unknown", eventId.toString()));
            return 0;
        }

        int durationTicks;
        if (seconds <= 0) {
            int customTicks = event.getCustomDurationTicks(source.getServer());
            durationTicks = (customTicks > 0) ? customTicks : MerryEventsCommand.DEFAULT_DURATION_SECONDS * 20;
        } else {
            durationTicks = seconds * 20;
        }

        int finalDurationSeconds = durationTicks / 20;

        if (event.isGlobal()) {
            boolean started = EventManager.getInstance().startGlobalEvent(source.getServer(), eventId, durationTicks);
            if (started) {
                source.sendMessage(
                        Text.translatable("command.merryevents.start.success.global", event.getDisplayName(),
                                finalDurationSeconds).formatted(Formatting.GREEN));
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
                        target.getName().getString(), finalDurationSeconds).formatted(Formatting.AQUA));
                return 1;
            } else {
                source.sendError(Text.translatable("command.merryevents.start.error.category_player",
                        event.getCategory().getDisplayName(), target.getName().getString()));
                return 0;
            }
        }
    }
}