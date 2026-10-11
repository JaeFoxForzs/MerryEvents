package com.merry.merryevents.event.sounds;

import com.merry.merryevents.MerryEvents;
import com.merry.merryevents.api.ChaosEvent;
import com.merry.merryevents.api.EventCategory;
import com.merry.merryevents.api.EventContext;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Player-specific auditory hallucination event belonging to
 * EventCategory.SOUNDS.
 * Periodically dispatches random positional sound packets directly behind the
 * target player.
 */
public final class ParanoiaSoundsEvent implements ChaosEvent {
    public static final Identifier ID = MerryEvents.id("paranoia_sounds");

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public EventCategory getCategory() {
        return EventCategory.SOUNDS;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public void onStartServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[ParanoiaSoundsEvent] Phantom auditory stream connected to player {}",
                    targetPlayer.getName().getString());
        }
    }

    @Override
    public void onTickServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context,
            int remainingTicks) {
        if (targetPlayer == null || remainingTicks % 90 != 0) {
            return;
        }

        Random random = targetPlayer.getRandom();
        RegistryEntry.Reference<SoundEvent> chosenSound = null;

        for (int attempts = 0; attempts < 6; attempts++) {
            Optional<RegistryEntry.Reference<SoundEvent>> candidate = Registries.SOUND_EVENT.getRandom(random);
            if (candidate.isPresent()) {
                String path = candidate.get().registryKey().getValue().getPath();
                if (!path.startsWith("music") && !path.startsWith("ui.") && !path.startsWith("ambient.")) {
                    chosenSound = candidate.get();
                    break;
                }
            }
        }

        if (chosenSound == null) {
            return;
        }

        Vec3d lookVec = targetPlayer.getRotationVector();
        double px = targetPlayer.getX() - (lookVec.x * 2.5);
        double py = targetPlayer.getY() + 0.5;
        double pz = targetPlayer.getZ() - (lookVec.z * 2.5);

        targetPlayer.networkHandler.sendPacket(new PlaySoundS2CPacket(
                chosenSound,
                SoundCategory.PLAYERS,
                px,
                py,
                pz,
                0.75F,
                0.85F + (random.nextFloat() * 0.3F),
                random.nextLong()));

        MerryEvents.LOGGER.debug("[ParanoiaSoundsEvent] Dispatched phantom sound {} behind player {}",
                chosenSound.registryKey().getValue(), targetPlayer.getName().getString());
    }

    @Override
    public void onStopServer(MinecraftServer server, @Nullable ServerPlayerEntity targetPlayer, EventContext context) {
        if (targetPlayer != null) {
            MerryEvents.LOGGER.info("[ParanoiaSoundsEvent] Auditory hallucinations terminated for player {}",
                    targetPlayer.getName().getString());
        }
    }
}