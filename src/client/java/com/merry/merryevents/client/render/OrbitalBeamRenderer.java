package com.merry.merryevents.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class OrbitalBeamRenderer {
    private static final List<ActiveBeam> ACTIVE_BEAMS = new ArrayList<>();
    private static final float HALF_PI = 1.57079632679F;
    private static boolean texturesInitialized = false;

    public static void init() {
        WorldRenderEvents.LAST.register(OrbitalBeamRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ACTIVE_BEAMS.removeIf(ActiveBeam::tick);
        });
    }

    public static void addBeam(BlockPos pos, int warmupTicks) {
        ACTIVE_BEAMS.add(new ActiveBeam(pos, warmupTicks));
    }

    private static void ensureTextures() {
        if (!texturesInitialized) {
            OrbitalTextures.init();
            texturesInitialized = true;
        }
    }

    private static void render(WorldRenderContext context) {
        if (ACTIVE_BEAMS.isEmpty()) {
            return;
        }

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        ensureTextures();

        Vec3d camPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        float tickDelta = context.tickCounter().getTickDelta(true);
        long time = System.currentTimeMillis();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderLayer reticleLayer = RenderLayer.getBeaconBeam(OrbitalTextures.RETICLE_TEXTURE, true);
        VertexConsumer reticleConsumer = consumers.getBuffer(reticleLayer);
        for (int i = 0; i < ACTIVE_BEAMS.size(); i++) {
            drawReticle(reticleConsumer, mat, ACTIVE_BEAMS.get(i), tickDelta, time);
        }

        RenderLayer beamLayer = RenderLayer.getBeaconBeam(OrbitalTextures.BEAM_TEXTURE, true);
        VertexConsumer beamConsumer = consumers.getBuffer(beamLayer);
        for (int i = 0; i < ACTIVE_BEAMS.size(); i++) {
            drawBeam(beamConsumer, mat, ACTIVE_BEAMS.get(i), camPos, tickDelta, time);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(reticleLayer);
            immediate.draw(beamLayer);
        }

        matrices.pop();
    }

    private static void drawReticle(VertexConsumer consumer, Matrix4f mat, ActiveBeam beam, float tickDelta,
            long time) {
        float progress = beam.getProgress(tickDelta);
        float circleRadius = 1.7F;
        float circleT = MathHelper.clamp(progress / 0.12F, 0.0F, 1.0F);
        float appearScale = MathHelper.sin(circleT * HALF_PI);
        float currentRadius = circleRadius * appearScale;

        if (currentRadius <= 0.01F) {
            return;
        }

        float x = beam.pos.getX() + 0.5F;
        float y = beam.pos.getY();
        float z = beam.pos.getZ() + 0.5F;
        float groundY = y + 0.04F;

        float angle = (time % 100000) * 0.007F;
        float cos = MathHelper.cos(angle) * currentRadius;
        float sin = MathHelper.sin(angle) * currentRadius;

        float x0 = x - cos + sin;
        float z0 = z - sin - cos;

        float x1 = x - cos - sin;
        float z1 = z - sin + cos;

        float x2 = x + cos - sin;
        float z2 = z + sin + cos;

        float x3 = x + cos + sin;
        float z3 = z + sin - cos;

        consumer.vertex(mat, x0, groundY, z0).color(255, 255, 255, 255).texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x1, groundY, z1).color(255, 255, 255, 255).texture(0.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x2, groundY, z2).color(255, 255, 255, 255).texture(1.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x3, groundY, z3).color(255, 255, 255, 255).texture(1.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);

        consumer.vertex(mat, x3, groundY, z3).color(255, 255, 255, 255).texture(1.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, -1, 0);
        consumer.vertex(mat, x2, groundY, z2).color(255, 255, 255, 255).texture(1.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, -1, 0);
        consumer.vertex(mat, x1, groundY, z1).color(255, 255, 255, 255).texture(0.0F, 1.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, -1, 0);
        consumer.vertex(mat, x0, groundY, z0).color(255, 255, 255, 255).texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, -1, 0);
    }

    private static void drawBeam(VertexConsumer consumer, Matrix4f mat, ActiveBeam beam, Vec3d camPos, float tickDelta,
            long time) {
        float progress = beam.getProgress(tickDelta);
        float appearT = MathHelper.clamp(progress / 0.12F, 0.0F, 1.0F);
        float beamExpand = MathHelper.sin(appearT * HALF_PI);

        float wipeT = MathHelper.clamp((progress - 0.55F) / 0.45F, 0.0F, 1.0F);
        float wipeDown = 0.5F * (1.0F + MathHelper.cos(wipeT * (float) Math.PI));

        if (beamExpand <= 0.01F || wipeDown <= 0.01F) {
            return;
        }

        float x = beam.pos.getX() + 0.5F;
        float y = beam.pos.getY();
        float z = beam.pos.getZ() + 0.5F;

        double dx = camPos.x - x;
        double dz = camPos.z - z;
        double yaw = Math.atan2(dx, dz);

        float halfWidth = 1.0F * beamExpand;
        float fullHeight = 450.0F * wipeDown;

        float nx = (float) Math.cos(yaw) * halfWidth;
        float nz = (float) -Math.sin(yaw) * halfWidth;

        float vScroll = (time % 10000) * 0.003F;
        float vTop = wipeDown * 10.0F + vScroll;

        consumer.vertex(mat, x - nx, y, z - nz).color(255, 255, 255, 255).texture(0.0F, vScroll)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x + nx, y, z + nz).color(255, 255, 255, 255).texture(1.0F, vScroll)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x + nx, y + fullHeight, z + nz).color(255, 255, 255, 255).texture(1.0F, vTop)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x - nx, y + fullHeight, z - nz).color(255, 255, 255, 255).texture(0.0F, vTop)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);

        consumer.vertex(mat, x - nx, y + fullHeight, z - nz).color(255, 255, 255, 255).texture(0.0F, vTop)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x + nx, y + fullHeight, z + nz).color(255, 255, 255, 255).texture(1.0F, vTop)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x + nx, y, z + nz).color(255, 255, 255, 255).texture(1.0F, vScroll)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
        consumer.vertex(mat, x - nx, y, z - nz).color(255, 255, 255, 255).texture(0.0F, vScroll)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0, 1, 0);
    }

    private static class ActiveBeam {
        final BlockPos pos;
        final int maxWarmup;
        int remaining;

        ActiveBeam(BlockPos pos, int warmup) {
            this.pos = pos;
            this.maxWarmup = warmup;
            this.remaining = warmup;
        }

        boolean tick() {
            return --remaining <= 0;
        }

        float getProgress(float tickDelta) {
            float current = (maxWarmup - remaining) + tickDelta;
            return MathHelper.clamp(current / (float) maxWarmup, 0.0F, 1.0F);
        }
    }
}