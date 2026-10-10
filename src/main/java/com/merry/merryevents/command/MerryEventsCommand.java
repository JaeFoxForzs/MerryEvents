package com.merry.merryevents.command;

import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.engine.EventManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public final class MerryEventsCommand {
    private static final int DEFAULT_DURATION_SECONDS = 30;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("merry")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("start")
                .then(CommandManager.argument("eventId", IdentifierArgumentType.identifier())
                    .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(EventManager.getInstance().getRegisteredEventIds(), builder))
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
                                Identifier eventId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                                int seconds = IntegerArgumentType.getInteger(ctx, "durationSeconds");
                                ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "target");
                                return executeStart(ctx.getSource(), eventId, seconds, player);
                            })
                        )
                    )
                )
            )
            .then(CommandManager.literal("stop")
                .then(CommandManager.argument("eventId", IdentifierArgumentType.identifier())
                    .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(EventManager.getInstance().getActiveGlobalEventIds(), builder))
                    .executes(ctx -> {
                        Identifier eventId = IdentifierArgumentType.getIdentifier(ctx, "eventId");
                        ServerCommandSource source = ctx.getSource();

                        EventManager.getInstance().stopGlobalEvent(source.getServer(), eventId);
                        if (source.isExecutedByPlayer()) {
                            EventManager.getInstance().stopPlayerEvent(source.getServer(), source.getPlayerOrThrow(), eventId);
                        }
                        source.sendMessage(Text.literal("Stopped event: " + eventId).formatted(Formatting.YELLOW));
                        return 1;
                    })
                )
            )
            .then(CommandManager.literal("status")
                .executes(ctx -> {
                    ServerCommandSource source = ctx.getSource();
                    var activeGlobals = EventManager.getInstance().getActiveGlobalEventIds();

                    source.sendMessage(Text.literal("=== MerryEvents Engine Status ===").formatted(Formatting.GOLD));
                    source.sendMessage(Text.literal("Global category mask: " + EventManager.getInstance().getGlobalCategoryMask()).formatted(Formatting.GRAY));

                    if (activeGlobals.isEmpty()) {
                        source.sendMessage(Text.literal("No active global events.").formatted(Formatting.DARK_GRAY));
                    } else {
                        for (Identifier id : activeGlobals) {
                            source.sendMessage(Text.literal(" - Active Global: " + id).formatted(Formatting.GREEN));
                        }
                    }
                    return 1;
                })
            )
        );
    }

    private static int executeStart(ServerCommandSource source, Identifier eventId, int seconds, ServerPlayerEntity explicitTarget) {
        ChaosEvent event = EventManager.getInstance().getEvent(eventId);
        if (event == null) {
            source.sendError(Text.literal("Unknown event: " + eventId));
            return 0;
        }

        int durationTicks = seconds * 20;

        if (event.isGlobal()) {
            boolean started = EventManager.getInstance().startGlobalEvent(source.getServer(), eventId, durationTicks);
            if (started) {
                source.sendMessage(Text.literal("Started global event: " + eventId + " (" + seconds + "s)").formatted(Formatting.GREEN));
                return 1;
            } else {
                source.sendError(Text.literal("Failed: Category '" + event.getCategory() + "' is already running a global event!"));
                return 0;
            }
        } else {
            ServerPlayerEntity target;
            if (explicitTarget != null) {
                target = explicitTarget;
            } else if (source.isExecutedByPlayer()) {
                target = source.getPlayer();
            } else {
                source.sendError(Text.literal("Specify a target player when executing unique events from console"));
                return 0;
            }

            if (target == null) {
                source.sendError(Text.literal("Player not found"));
                return 0;
            }

            boolean started = EventManager.getInstance().startPlayerEvent(source.getServer(), target, eventId, durationTicks);
            if (started) {
                source.sendMessage(Text.literal("Started event " + eventId + " for " + target.getName().getString() + " (" + seconds + "s)").formatted(Formatting.AQUA));
                return 1;
            } else {
                source.sendError(Text.literal("Failed: Category '" + event.getCategory() + "' is already occupied for " + target.getName().getString()));
                return 0;
            }
        }
    }
}