package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.effect.*;
import com.merry.merryevents.client.render.OrbitalBeamRenderer;
import com.merry.merryevents.event.mobs.KindergartenEvent;
import com.merry.merryevents.event.physics.PerfectIceEvent;
import com.merry.merryevents.event.status.WaterDrinkerEvent;
import com.merry.merryevents.event.world.LavaWaterSwapEvent;
import com.merry.merryevents.network.EventSyncPayload;
import com.merry.merryevents.network.ForbiddenBlocksPayload;
import com.merry.merryevents.network.OrbitalStrikeSpawnPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public class MerryEventsClient implements ClientModInitializer {
    private static final ClientChaosEffect SERVER_STATE_EFFECT = new ClientChaosEffect() {
        @Override
        public void onStart(MinecraftClient client, int initialRemainingTicks) {
        }

        @Override
        public void onStop(MinecraftClient client) {
        }
    };

    @Override
    public void onInitializeClient() {
        MerryEvents.LOGGER.info("[MerryEvents-Client] Binding client payload receivers...");

        ClientEventManager.init();

        // 1. Core visual & camera effects
        ClientEventManager.registerEffect(EventIds.LOBOTOMY, new LobotomyClientEffect());
        LobotomyClientEffect.init();

        ClientEventManager.registerEffect(EventIds.GHOST_GUI, new GhostGuiVisualEffect());
        ClientEventManager.registerEffect(EventIds.STIFF_NECK, new StiffNeckClientEffect());
        ClientEventManager.registerEffect(EventIds.ORBITAL_STRIKE, new OrbitalStrikeClientEffect());
        ClientEventManager.registerEffect(EventIds.FLAT_MOBS, new FlatMobsClientEffect());
        ClientEventManager.registerEffect(EventIds.DONT_STEP_ON, new DontStepOnClientEffect());

        // 2. Input & UI constraints
        ClientEventManager.registerEffect(EventIds.SCRAMBLED_BUTTONS, new ScrambledButtonsClientEffect());
        ClientEventManager.registerEffect(EventIds.CLOSED_INVENTORY, new ClosedInventoryClientEffect());

        ClientEventManager.registerEffect(EventIds.ADHD, new AdhdClientEffect());
        AdhdClientEffect.init();

        // 3. Post-processing shaders & viewports
        ClientEventManager.registerEffect(EventIds.DISCO, new DiscoClientEffect());
        ClientEventManager.registerEffect(EventIds.MINERS_VISION, new MinersVisionClientEffect());
        ClientEventManager.registerEffect(EventIds.MYOPIA, new MyopiaClientEffect());
        ClientEventManager.registerEffect(EventIds.UPSIDE_DOWN, new UpsideDownClientEffect());
        ClientEventManager.registerEffect(EventIds.COLORBLINDNESS, new ColorblindnessClientEffect());

        ClientEventManager.registerEffect(EventIds.STICKY_FINGERS, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.CREEPER_REVENGE, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.ANT_MAN, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.BUTTERFINGERS, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.CATAPULT, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.RANDOM_PET, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.EQUILIBRIUM, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.FIRE_SKIN, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.ZOO, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.ACID_RAIN, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.RUST_RAIN, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.WATER_DRINKER, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.KINDERGARTEN, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.PERFECT_ICE, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.LAVA_WATER_SWAP, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EventIds.MELTING_EXPERIENCE, SERVER_STATE_EFFECT);

        // 4. Auditory & physics constraints
        ClientEventManager.registerEffect(EventIds.RUBBER_WORLD, new RubberWorldClientEffect());
        ClientEventManager.registerEffect(EventIds.BOUNCING_HAMSTER, new BouncingHamsterClientEffect());

        ClientEventManager.registerEffect(EventIds.EPILEPSY, new EpilepsyClientEffect());
        EpilepsyClientEffect.init();

        ClientEventManager.registerEffect(EventIds.UNCONTROLLED_RUN, new UncontrolledRunClientEffect());
        UncontrolledRunClientEffect.init();

        // 5. Client prediction bridges
        WaterDrinkerEvent.clientActiveSupplier = () -> ClientEventManager
                .isEventActive(EventIds.WATER_DRINKER);
        KindergartenEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(EventIds.KINDERGARTEN);
        PerfectIceEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(EventIds.PERFECT_ICE);
        LavaWaterSwapEvent.clientActiveSupplier = () -> ClientEventManager
                .isEventActive(EventIds.LAVA_WATER_SWAP);

        // 6. Subsystem listeners initialization
        DontStepOnClientEffect.init();
        DiscoClientEffect.init();
        MinersVisionClientEffect.init();
        OrbitalBeamRenderer.init();

        // 7. Network payload registration
        ClientPlayNetworking.registerGlobalReceiver(EventSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                MerryEvents.LOGGER.info("[MerryEvents-Client] Sync: id={}, active={}, ticks={}, seq={}",
                        payload.eventId(),
                        payload.active(), payload.durationTicks(), payload.startSequence());
                ClientEventManager.handleEventSync(context.client(), payload.eventId(), payload.durationTicks(),
                        payload.active(), payload.startSequence());
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