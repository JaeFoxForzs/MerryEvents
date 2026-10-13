package com.merry.merryevents.client;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.client.api.ClientChaosEffect;
import com.merry.merryevents.client.effect.*;
import com.merry.merryevents.client.render.OrbitalBeamRenderer;
import com.merry.merryevents.event.inventory.MeltingExperienceEvent;
import com.merry.merryevents.event.mobs.RandomPetEvent;
import com.merry.merryevents.event.mobs.ZooEvent;
import com.merry.merryevents.event.mobs.KindergartenEvent;
import com.merry.merryevents.event.physics.CatapultEvent;
import com.merry.merryevents.event.physics.PerfectIceEvent;
import com.merry.merryevents.event.status.EquilibriumEvent;
import com.merry.merryevents.event.status.FireSkinEvent;
import com.merry.merryevents.event.status.WaterDrinkerEvent;
import com.merry.merryevents.event.weather.AcidRainEvent;
import com.merry.merryevents.event.weather.RustRainEvent;
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
        ClientEventManager.registerEffect(MerryEvents.id("lobotomy"), new LobotomyClientEffect());
        LobotomyClientEffect.init();

        ClientEventManager.registerEffect(MerryEvents.id("ghost_gui"), new GhostGuiVisualEffect());
        ClientEventManager.registerEffect(MerryEvents.id("stiff_neck"), new StiffNeckClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("orbital_strike"), new OrbitalStrikeClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("flat_mobs"), new FlatMobsClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("dont_step_on"), new DontStepOnClientEffect());

        // 2. Input & UI constraints
        ClientEventManager.registerEffect(MerryEvents.id("scrambled_buttons"), new ScrambledButtonsClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("closed_inventory"), new ClosedInventoryClientEffect());

        ClientEventManager.registerEffect(MerryEvents.id("adhd"), new AdhdClientEffect());
        AdhdClientEffect.init();

        // 3. Post-processing shaders & viewports
        ClientEventManager.registerEffect(MerryEvents.id("disco"), new DiscoClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("miners_vision"), new MinersVisionClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("myopia"), new MyopiaClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("upside_down"), new UpsideDownClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("colorblindness"), new ColorblindnessClientEffect());

        ClientEventManager.registerEffect(MerryEvents.id("sticky_fingers"), SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(MerryEvents.id("creeper_revenge"), SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(MerryEvents.id("ant_man"), SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(MerryEvents.id("butterfingers"), SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(CatapultEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(RandomPetEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(EquilibriumEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(FireSkinEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(ZooEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(AcidRainEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(RustRainEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(WaterDrinkerEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(KindergartenEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(PerfectIceEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(LavaWaterSwapEvent.ID, SERVER_STATE_EFFECT);
        ClientEventManager.registerEffect(MeltingExperienceEvent.ID, SERVER_STATE_EFFECT);

        // 4. Auditory & physics constraints
        ClientEventManager.registerEffect(MerryEvents.id("rubber_world"), new RubberWorldClientEffect());
        ClientEventManager.registerEffect(MerryEvents.id("bouncing_hamster"), new BouncingHamsterClientEffect());

        ClientEventManager.registerEffect(MerryEvents.id("epilepsy"), new EpilepsyClientEffect());
        EpilepsyClientEffect.init();

        ClientEventManager.registerEffect(MerryEvents.id("uncontrolled_run"), new UncontrolledRunClientEffect());
        UncontrolledRunClientEffect.init();

        // 5. Client prediction bridges
        WaterDrinkerEvent.clientActiveSupplier = () -> ClientEventManager
                .isEventActive(MerryEvents.id("water_drinker"));
        KindergartenEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(MerryEvents.id("kindergarten"));
        PerfectIceEvent.clientActiveSupplier = () -> ClientEventManager.isEventActive(MerryEvents.id("perfect_ice"));
        LavaWaterSwapEvent.clientActiveSupplier = () -> ClientEventManager
                .isEventActive(MerryEvents.id("lava_water_swap"));

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