package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side camera controller executing smooth gaze attraction and dynamic tracking.
 * Every 3-7 seconds, locks onto a moving entity or landmark, softly drawing and holding
 * the player's view towards the target for 1.8-2.8 seconds before releasing focus.
 */
public final class AdhdClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;
    private static boolean registered = false;

    // Tracking state
    private static boolean isTracking = false;
    private static int trackingTicksRemaining = 0;
    private static int nextDistractionTicks = 0;
    private static long lastFrameTime = 0L;

    @Nullable
    private static Entity targetEntity = null;
    @Nullable
    private static Vec3d targetBlockCenter = null;

    // Tuning constants
    private static final int MIN_INTERVAL_TICKS = 3 * 20; // 3 seconds
    private static final int MAX_INTERVAL_TICKS = 7 * 20; // 7 seconds
    private static final int MIN_TRACK_TICKS = 36;        // ~1.8 seconds tracking
    private static final int MAX_TRACK_TICKS = 56;        // ~2.8 seconds tracking
    private static final double SEARCH_RADIUS = 16.0;
    private static final float ROTATION_SMOOTHNESS = 6.5F; // Exponential pull speed

    public static void init() {
        if (registered) {
            return;
        }

        // 1. Tick state-machine managing acquisition intervals and tracking lifetime
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!active || client.player == null || client.world == null) {
                if (isTracking) {
                    resetTracking();
                }
                return;
            }

            if (isTracking) {
                trackingTicksRemaining--;
                if (trackingTicksRemaining <= 0) {
                    resetTracking();
                }
            }

            nextDistractionTicks--;
            if (nextDistractionTicks <= 0) {
                acquireTarget(client);
                scheduleNextDistraction();
            }
        });

        // 2. High-framerate per-frame camera interpolation (60/144/240 FPS smooth motion)
        WorldRenderEvents.START.register(context -> {
            if (!active || !isTracking) {
                return;
            }
            updateGazeInterpolation();
        });

        registered = true;
    }

    private static void scheduleNextDistraction() {
        nextDistractionTicks = ThreadLocalRandom.current().nextInt(MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS + 1);
    }

    private static void resetTracking() {
        isTracking = false;
        trackingTicksRemaining = 0;
        targetEntity = null;
        targetBlockCenter = null;
        lastFrameTime = 0L;
    }

    private static void acquireTarget(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Box scanBox = client.player.getBoundingBox().expand(SEARCH_RADIUS);
        List<Entity> movingEntities = new ArrayList<>();

        // Search for dynamically moving entities around the player
        for (Entity entity : client.world.getOtherEntities(client.player, scanBox)) {
            if (!entity.isAlive() || entity.isSpectator()) {
                continue;
            }

            double dx = entity.getX() - entity.prevX;
            double dy = entity.getY() - entity.prevY;
            double dz = entity.getZ() - entity.prevZ;
            double moveSq = dx * dx + dy * dy + dz * dz;
            double velSq = entity.getVelocity().lengthSquared();

            // Threshold: moving faster than ~0.03 blocks per tick
            if (moveSq > 0.0009 || velSq > 0.0009) {
                movingEntities.add(entity);
            }
        }

        if (!movingEntities.isEmpty()) {
            targetEntity = movingEntities.get(rand.nextInt(movingEntities.size()));
            targetBlockCenter = null;
            isTracking = true;
            trackingTicksRemaining = rand.nextInt(MIN_TRACK_TICKS, MAX_TRACK_TICKS + 1);
            lastFrameTime = System.currentTimeMillis();
            client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.8F);
        } else {
            // Fallback: pick an arbitrary nearby solid landmark block
            BlockPos playerPos = client.player.getBlockPos();
            List<BlockPos> blockCandidates = new ArrayList<>();

            for (int attempt = 0; attempt < 24; attempt++) {
                int bx = playerPos.getX() + rand.nextInt(15) - 7;
                int by = playerPos.getY() + rand.nextInt(7) - 3;
                int bz = playerPos.getZ() + rand.nextInt(15) - 7;
                BlockPos candidate = new BlockPos(bx, by, bz);

                if (!client.world.getBlockState(candidate).isAir()) {
                    blockCandidates.add(candidate);
                }
            }

            if (!blockCandidates.isEmpty()) {
                BlockPos chosen = blockCandidates.get(rand.nextInt(blockCandidates.size()));
                targetEntity = null;
                targetBlockCenter = Vec3d.ofCenter(chosen);
                isTracking = true;
                trackingTicksRemaining = rand.nextInt(MIN_TRACK_TICKS, MAX_TRACK_TICKS + 1);
                lastFrameTime = System.currentTimeMillis();
                client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 1.5F);
            }
        }
    }

    private static void updateGazeInterpolation() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        Vec3d targetPos;
        if (targetEntity != null) {
            if (!targetEntity.isAlive() || targetEntity.getWorld() != client.world) {
                resetTracking();
                return;
            }
            targetPos = targetEntity.getEyePos();
        } else if (targetBlockCenter != null) {
            targetPos = targetBlockCenter;
        } else {
            resetTracking();
            return;
        }

        Vec3d eyePos = client.player.getEyePos();
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist < 0.001 && Math.abs(dy) < 0.001) {
            return;
        }

        // Minecraft standard yaw & pitch angle projections
        float targetYaw = MathHelper.wrapDegrees((float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float targetPitch = MathHelper.wrapDegrees((float) (-(MathHelper.atan2(dy, horizontalDist) * (180.0 / Math.PI))));

        long now = System.currentTimeMillis();
        if (lastFrameTime <= 0L) {
            lastFrameTime = now;
        }
        float dt = Math.min((now - lastFrameTime) / 1000.0F, 0.05F);
        lastFrameTime = now;

        // Exponential decay smoothing curve ensuring framerate independence
        float pull = 1.0F - (float) Math.exp(-ROTATION_SMOOTHNESS * dt);

        float yawDelta = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - client.player.getPitch());

        float newYaw = client.player.getYaw() + yawDelta * pull;
        float newPitch = MathHelper.clamp(client.player.getPitch() + pitchDelta * pull, -90.0F, 90.0F);

        // Update current and previous rotation simultaneously to neutralize frame delta stutter
        client.player.setYaw(newYaw);
        client.player.prevYaw = newYaw;
        client.player.setPitch(newPitch);
        client.player.prevPitch = newPitch;
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        active = true;
        scheduleNextDistraction();
        resetTracking();
    }

    @Override
    public void onStop(MinecraftClient client) {
        active = false;
        resetTracking();
    }
}