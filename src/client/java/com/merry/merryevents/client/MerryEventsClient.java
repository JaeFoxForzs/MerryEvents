package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.effect.GhostGuiVisualEffect;
import com.merry.merryevents.client.effect.StiffNeckClientEffect;
import com.merry.merryevents.network.EventSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MerryEventsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MerryEvents.LOGGER.info("[MerryEvents-Client] Binding client payload receivers...");

        ClientEventManager.registerEffect(MerryEvents.id("ghost_gui"), new GhostGuiVisualEffect());
        ClientEventManager.registerEffect(MerryEvents.id("stiff_neck"), new StiffNeckClientEffect());

        ClientEventManager.registerEffect(MerryEvents.id("ghost_gui"), new GhostGuiVisualEffect());

        ClientPlayNetworking.registerGlobalReceiver(EventSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MerryEvents.LOGGER.info("[MerryEvents-Client] Sync: id={}, active={}, ticks={}", payload.eventId(), payload.active(), payload.durationTicks());
                ClientEventManager.handleEventSync(context.client(), payload.eventId(), payload.durationTicks(), payload.active());
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEventManager.reset(client));
    }
}