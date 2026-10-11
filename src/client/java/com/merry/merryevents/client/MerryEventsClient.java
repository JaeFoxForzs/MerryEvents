package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.*;
import com.merry.merryevents.client.effect.*;
import com.merry.merryevents.network.*;
import com.merry.merryevents.client.render.*;
import com.merry.merryevents.event.status.*;
import com.merry.merryevents.event.visual.*;
import com.merry.merryevents.event.world.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.*;

public class MerryEventsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MerryEvents.LOGGER.info("[MerryEvents-Client] Binding client payload receivers...");

        ClientEventManager.registerEffect(MerryEvents.id("ghost_gui"), new GhostGuiVisualEffect());
        ClientEventManager.registerEffect(MerryEvents.id("stiff_neck"), new StiffNeckClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("orbital_strike"), new OrbitalStrikeClientEffect());
        ClientEventManager.registerEffect(FlatMobsEvent.ID,
                new FlatMobsClientEffect());

        ClientEventManager.registerEffect(WaterDrinkerEvent.ID,
                new ClientChaosEffect() {
                    @Override
                    public void onStart(MinecraftClient client, int initialRemainingTicks) {
                    }

                    @Override
                    public void onStop(MinecraftClient client) {
                    }
                });
        WaterDrinkerEvent.clientActiveSupplier = () -> ClientEventManager
                .isEventActive(WaterDrinkerEvent.ID);

        ClientEventManager.registerEffect(LavaWaterSwapEvent.ID,
                new ClientChaosEffect() {
                    @Override
                    public void onStart(MinecraftClient client, int initialRemainingTicks) {
                    }

                    @Override
                    public void onStop(MinecraftClient client) {
                    }
                });
        LavaWaterSwapEvent.clientActiveSupplier = () -> ClientEventManager
                .isEventActive(LavaWaterSwapEvent.ID);

        DontStepOnClientEffect.init();

        ClientPlayNetworking.registerGlobalReceiver(EventSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MerryEvents.LOGGER.info("[MerryEvents-Client] Sync: id={}, active={}, ticks={}", payload.eventId(),
                        payload.active(), payload.durationTicks());
                ClientEventManager.handleEventSync(context.client(), payload.eventId(), payload.durationTicks(),
                        payload.active());
            });
        });

        OrbitalBeamRenderer.init();

        ClientPlayNetworking.registerGlobalReceiver(ForbiddenBlocksPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        DontStepOnClientEffect.setPlayerRestrictionStatus(payload.targetPlayerName(), payload.active());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(OrbitalStrikeSpawnPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                OrbitalBeamRenderer.addBeam(payload.pos(), payload.warmupTicks());
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientEventManager.reset(client);
            DontStepOnClientEffect.reset();
        });
    }
}