package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.effect.GhostGuiVisualEffect;
import com.merry.merryevents.client.effect.OrbitalStrikeClientEffect;
import com.merry.merryevents.client.effect.StiffNeckClientEffect;
import com.merry.merryevents.client.render.OrbitalBeamRenderer;
import com.merry.merryevents.network.EventSyncPayload;
import com.merry.merryevents.network.OrbitalStrikeSpawnPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MerryEventsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MerryEvents.LOGGER.info("[MerryEvents-Client] Binding client payload receivers...");

        ClientEventManager.registerEffect(MerryEvents.id("ghost_gui"), new GhostGuiVisualEffect());
        ClientEventManager.registerEffect(MerryEvents.id("stiff_neck"), new StiffNeckClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("orbital_strike"), new OrbitalStrikeClientEffect());

        ClientEventManager.registerEffect(com.merry.merryevents.event.world.LavaWaterSwapEvent.ID,
                new com.merry.merryevents.client.api.ClientChaosEffect() {
                    @Override
                    public void onStart(net.minecraft.client.MinecraftClient client, int initialRemainingTicks) {
                    }

                    @Override
                    public void onStop(net.minecraft.client.MinecraftClient client) {
                    }
                });
        com.merry.merryevents.event.world.LavaWaterSwapEvent.clientActiveSupplier = () -> ClientEventManager
                .isEventActive(com.merry.merryevents.event.world.LavaWaterSwapEvent.ID);

        ClientPlayNetworking.registerGlobalReceiver(EventSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MerryEvents.LOGGER.info("[MerryEvents-Client] Sync: id={}, active={}, ticks={}", payload.eventId(),
                        payload.active(), payload.durationTicks());
                ClientEventManager.handleEventSync(context.client(), payload.eventId(), payload.durationTicks(),
                        payload.active());
            });
        });

        OrbitalBeamRenderer.init();

        ClientPlayNetworking.registerGlobalReceiver(OrbitalStrikeSpawnPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                OrbitalBeamRenderer.addBeam(payload.pos(), payload.warmupTicks());
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEventManager.reset(client));
    }
}