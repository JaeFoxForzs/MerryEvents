package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.*;
import com.merry.merryevents.client.effect.*;
import com.merry.merryevents.event.camera.AdhdEvent;
import com.merry.merryevents.event.sounds.RubberWorldEvent;
import com.merry.merryevents.event.ui.ClosedInventoryEvent;
import com.merry.merryevents.network.*;
import com.merry.merryevents.client.render.*;
import com.merry.merryevents.event.camera.EpilepsyEvent;
import com.merry.merryevents.event.mobs.*;
import com.merry.merryevents.event.physics.*;
import com.merry.merryevents.event.status.*;
import com.merry.merryevents.event.vision.*;
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

        ClientEventManager.registerEffect(LobotomyEvent.ID, new LobotomyClientEffect());
        LobotomyClientEffect.init();

        // Core visual effects
        ClientEventManager.registerEffect(MerryEvents.id("ghost_gui"), new GhostGuiVisualEffect());
        ClientEventManager.registerEffect(MerryEvents.id("stiff_neck"), new StiffNeckClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("orbital_strike"), new OrbitalStrikeClientEffect());
        ClientEventManager.registerEffect(FlatMobsEvent.ID, new FlatMobsClientEffect());
        ClientEventManager.registerEffect(DontStepOnEvent.ID, new DontStepOnClientEffect());

        // Interface and inventory constraint effects
        ClientEventManager.registerEffect(MerryEvents.id("scrambled_buttons"), new ScrambledButtonsClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("sticky_fingers"), new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {
            }

            @Override
            public void onStop(MinecraftClient client) {
            }
        });

        ClientEventManager.registerEffect(MerryEvents.id("creeper_revenge"), new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {}
            @Override
            public void onStop(MinecraftClient client) {}
        });
        ClientEventManager.registerEffect(MerryEvents.id("ant_man"), new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {}
            @Override
            public void onStop(MinecraftClient client) {}
        });

        ClientEventManager.registerEffect(MerryEvents.id("butterfingers"), new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {
            }

            @Override
            public void onStop(MinecraftClient client) {
            }
        });
        ClientEventManager.registerEffect(MerryEvents.id("adhd"), new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {}
            @Override
            public void onStop(MinecraftClient client) {}
        });
        ClientEventManager.registerEffect(AdhdEvent.ID, new AdhdClientEffect());
        AdhdClientEffect.init();

        // Sensory and vision shaders
        ClientEventManager.registerEffect(DiscoEvent.ID, new DiscoClientEffect());
        ClientEventManager.registerEffect(MinersVisionEvent.ID, new MinersVisionClientEffect());
        ClientEventManager.registerEffect(MyopiaEvent.ID, new MyopiaClientEffect());
        ClientEventManager.registerEffect(UpsideDownEvent.ID, new UpsideDownClientEffect());
        ClientEventManager.registerEffect(ColorblindnessEvent.ID, new ColorblindnessClientEffect());

        // Auditory and UI lockdown effects
        ClientEventManager.registerEffect(RubberWorldEvent.ID, new RubberWorldClientEffect());
        ClientEventManager.registerEffect(ClosedInventoryEvent.ID, new ClosedInventoryClientEffect());
        ClientEventManager.registerEffect(EpilepsyEvent.ID, new EpilepsyClientEffect());
        EpilepsyClientEffect.init();
        ClientEventManager.registerEffect(BouncingHamsterEvent.ID, new BouncingHamsterClientEffect());
        ClientEventManager.registerEffect(UncontrolledRunEvent.ID, new UncontrolledRunClientEffect());
        UncontrolledRunClientEffect.init();

        // Global state condition providers
        ClientEventManager.registerEffect(WaterDrinkerEvent.ID, new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {
            }

            @Override
            public void onStop(MinecraftClient client) {
            }
        });
        WaterDrinkerEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(WaterDrinkerEvent.ID);

        ClientEventManager.registerEffect(KindergartenEvent.ID, new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {
            }

            @Override
            public void onStop(MinecraftClient client) {
            }
        });
        KindergartenEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(KindergartenEvent.ID);

        ClientEventManager.registerEffect(PerfectIceEvent.ID, new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {
            }

            @Override
            public void onStop(MinecraftClient client) {
            }
        });
        PerfectIceEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(PerfectIceEvent.ID);

        ClientEventManager.registerEffect(LavaWaterSwapEvent.ID, new ClientChaosEffect() {
            @Override
            public void onStart(MinecraftClient client, int initialRemainingTicks) {
            }

            @Override
            public void onStop(MinecraftClient client) {
            }
        });
        LavaWaterSwapEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(LavaWaterSwapEvent.ID);

        // Renderer and overlay lifecycle bindings
        DontStepOnClientEffect.init();
        DiscoClientEffect.init();
        MinersVisionClientEffect.init();
        OrbitalBeamRenderer.init();

        // Authoritative network payload registration
        ClientPlayNetworking.registerGlobalReceiver(EventSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MerryEvents.LOGGER.info("[MerryEvents-Client] Sync: id={}, active={}, ticks={}", payload.eventId(),
                        payload.active(), payload.durationTicks());
                ClientEventManager.handleEventSync(context.client(), payload.eventId(), payload.durationTicks(),
                        payload.active());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ForbiddenBlocksPayload.ID, (payload, context) -> {
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