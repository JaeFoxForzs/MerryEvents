package com.merry.merryevents.client.render;

import com.merry.merryevents.MerryEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Random;

/**
 * Procedural texture synthesizer generating celestial strike laser and reticle
 * textures.
 */
public final class OrbitalTextures {
    public static final Identifier BEAM_TEXTURE = MerryEvents.id("textures/vfx/orbital_beam_cartoon.png");
    public static final Identifier RETICLE_TEXTURE = MerryEvents.id("textures/vfx/orbital_reticle_cartoon.png");

    private OrbitalTextures() {
    }

    /**
     * Generates and registers procedural beam and floor targeting reticle textures
     * into TextureManager.
     */
    public static void init() {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Procedural cartoon beam generation: bright outline, crimson body, hot
        // white core
        NativeImage beamImage = new NativeImage(256, 512, false);
        Random rand = new Random(42);

        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < 256; x++) {
                float uDist = Math.abs((x / 255.0F) - 0.5F) * 2.0F; // 0.0 at center, 1.0 at outer edge

                int r = 0, g = 0, b = 0, a = 0;

                if (uDist <= 0.98F) {
                    if (uDist < 0.22F) {
                        // High-luminance white-hot core
                        r = 255;
                        g = 245;
                        b = 230;
                        a = 255;
                    } else if (uDist < 0.76F) {
                        // Saturated crimson cartoon body
                        r = 245;
                        g = 20;
                        b = 20;
                        a = 240;
                    } else {
                        // Pastel peach edge outline
                        r = 255;
                        g = 185;
                        b = 185;
                        a = 255;
                    }

                    // Cel-shaded sharp boundary step
                    if (uDist > 0.95F) {
                        a = (int) ((1.0F - (uDist - 0.95F) / 0.03F) * 255);
                    }
                }

                beamImage.setColor(x, y, OrbitalTextures.abgr(a, r, g, b));
            }
        }

        // Scatter 4-pointed cartoon stars with density biased toward the center column
        for (int i = 0; i < 900; i++) {
            float centerDist = (float) Math.pow(rand.nextFloat(), 2.2);
            float xNorm = rand.nextBoolean() ? 0.5F + centerDist * 0.45F : 0.5F - centerDist * 0.45F;
            int cx = (int) (xNorm * 255.0F);
            int cy = rand.nextInt(512);

            float centerProximity = 1.0F - Math.abs(xNorm - 0.5F) * 2.0F;
            int starRadius = Math.max(1, (int) (centerProximity * 6.5F));

            OrbitalTextures.drawCartoonStar(beamImage, cx, cy, starRadius);
        }

        client.getTextureManager().registerTexture(OrbitalTextures.BEAM_TEXTURE,
                new NativeImageBackedTexture(beamImage));

        // 2. Procedural floor reticle generation: concentric rings, ticks, and center
        // crosshair
        NativeImage reticleImage = new NativeImage(256, 256, false);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                float dx = (x - 128.0F) / 128.0F;
                float dy = (y - 128.0F) / 128.0F;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                int r = 0, g = 0, b = 0, a = 0;

                if (dist <= 1.0F) {
                    double angle = Math.atan2(dy, dx);

                    boolean outerRing = dist >= 0.82F && dist <= 0.96F && Math.sin(angle * 8.0) > 0.2;
                    boolean middleRing = dist >= 0.52F && dist <= 0.62F;
                    boolean centerDot = dist <= 0.20F;
                    boolean cross = Math.abs(Math.sin(angle * 2.0) * dist) < 0.04F && dist <= 0.78F;

                    if (outerRing || middleRing || centerDot || cross) {
                        if (outerRing || centerDot) {
                            r = 255;
                            g = 195;
                            b = 185;
                            a = 250; // High-luminance contour
                        } else {
                            r = 245;
                            g = 25;
                            b = 25;
                            a = 230; // Crimson body
                        }
                    }
                }

                reticleImage.setColor(x, y, OrbitalTextures.abgr(a, r, g, b));
            }
        }

        client.getTextureManager().registerTexture(OrbitalTextures.RETICLE_TEXTURE,
                new NativeImageBackedTexture(reticleImage));
    }

    private static void drawCartoonStar(NativeImage img, int cx, int cy, int radius) {
        for (int dy = -radius * 2; dy <= radius * 2; dy++) {
            for (int dx = -radius * 2; dx <= radius * 2; dx++) {
                float d = (float) (Math.pow(Math.abs(dx), 0.6) + Math.pow(Math.abs(dy), 0.6));
                if (d <= Math.pow(radius, 0.6)) {
                    int px = Math.floorMod(cx + dx, 256);
                    int py = Math.floorMod(cy + dy, 512);
                    img.setColor(px, py, OrbitalTextures.abgr(255, 255, 255, 240)); // Luminous white star
                }
            }
        }
    }

    private static int abgr(int a, int r, int g, int b) {
        return (a << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
    }
}