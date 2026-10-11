package com.merry.merryevents.client.effect;

import com.merry.merryevents.client.api.ClientChaosEffect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Client visual driver scanning localized terrain and rendering occluded ore
 * bounding boxes.
 * Utilizes motion-gated cache validation to prevent client framerate hitches.
 */
public final class MinersVisionClientEffect implements ClientChaosEffect {
    private static volatile boolean active = false;
    private static final List<HighlightedOre> ORE_CACHE = new ArrayList<>();
    private static BlockPos lastScanOrigin = null;
    private static int scanCooldown = 0;
    private static boolean registered = false;

    // Custom depth-independent line layer ensuring visibility through solid
    // geometry
    private static final RenderLayer LINES_THROUGH_WALLS = RenderLayer.MultiPhase.of(
            "merryevents_lines_always",
            VertexFormats.LINES,
            VertexFormat.DrawMode.LINES,
            1536,
            false,
            true,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.LINES_PROGRAM)
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.5D)))
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .writeMaskState(RenderPhase.ALL_MASK)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                    .build(false));

    public record HighlightedOre(BlockPos pos, float r, float g, float b) {
    }

    /**
     * Binds periodic client scan ticks and post-world line render callbacks.
     */
    public static void init() {
        if (MinersVisionClientEffect.registered) {
            return;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!MinersVisionClientEffect.active || client.world == null || client.player == null) {
                if (!MinersVisionClientEffect.ORE_CACHE.isEmpty()) {
                    MinersVisionClientEffect.ORE_CACHE.clear();
                    MinersVisionClientEffect.lastScanOrigin = null;
                }
                return;
            }

            BlockPos currentPos = client.player.getBlockPos();
            boolean movedSignificantly = MinersVisionClientEffect.lastScanOrigin == null
                    || !MinersVisionClientEffect.lastScanOrigin.isWithinDistance(currentPos, 2.0);

            if (--MinersVisionClientEffect.scanCooldown <= 0 || movedSignificantly) {
                MinersVisionClientEffect.scanCooldown = 20;
                MinersVisionClientEffect.updateOres(client, currentPos);
            }
        });

        WorldRenderEvents.LAST.register(MinersVisionClientEffect::render);
        MinersVisionClientEffect.registered = true;
    }

    private static void updateOres(MinecraftClient client, BlockPos playerPos) {
        MinersVisionClientEffect.lastScanOrigin = playerPos;
        int radius = 12;
        List<HighlightedOre> found = new ArrayList<>();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int blockX = playerPos.getX() + dx;
                int blockZ = playerPos.getZ() + dz;

                int chunkX = ChunkSectionPos.getSectionCoord(blockX);
                int chunkZ = ChunkSectionPos.getSectionCoord(blockZ);
                if (!client.world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                for (int dy = -radius; dy <= radius; dy++) {
                    mutable.set(blockX, playerPos.getY() + dy, blockZ);
                    BlockState state = client.world.getBlockState(mutable);
                    int color = MinersVisionClientEffect.getOreColor(state.getBlock());
                    if (color != 0) {
                        float r = ((color >> 16) & 0xFF) / 255.0F;
                        float g = ((color >> 8) & 0xFF) / 255.0F;
                        float b = (color & 0xFF) / 255.0F;
                        found.add(new HighlightedOre(mutable.toImmutable(), r, g, b));
                    }
                }
            }
        }
        MinersVisionClientEffect.ORE_CACHE.clear();
        MinersVisionClientEffect.ORE_CACHE.addAll(found);
    }

    private static int getOreColor(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)
            return 0x00FFFF;
        if (block == Blocks.ANCIENT_DEBRIS)
            return 0xFF5533;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)
            return 0xFFD700;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)
            return 0xD8AF93;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)
            return 0x17DD62;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)
            return 0x2244FF;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)
            return 0xFF2222;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)
            return 0xE07340;
        if (block == Blocks.NETHER_QUARTZ_ORE)
            return 0xEFEFEF;
        return 0;
    }

    private static void render(WorldRenderContext context) {
        if (!MinersVisionClientEffect.active || MinersVisionClientEffect.ORE_CACHE.isEmpty())
            return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumerProvider consumers = context.consumers();
        if (consumers != null) {
            VertexConsumer buffer = consumers.getBuffer(MinersVisionClientEffect.LINES_THROUGH_WALLS);
            for (HighlightedOre ore : MinersVisionClientEffect.ORE_CACHE) {
                BlockPos pos = ore.pos;
                Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0,
                        pos.getZ() + 1.0);
                WorldRenderer.drawBox(matrices, buffer, box, ore.r, ore.g, ore.b, 0.9F);
            }

            if (consumers instanceof VertexConsumerProvider.Immediate imm) {
                imm.draw(MinersVisionClientEffect.LINES_THROUGH_WALLS);
            }
        }

        matrices.pop();
    }

    @Override
    public void onStart(MinecraftClient client, int initialRemainingTicks) {
        MinersVisionClientEffect.active = true;
        MinersVisionClientEffect.scanCooldown = 0;
        MinersVisionClientEffect.lastScanOrigin = null;
    }

    @Override
    public void onStop(MinecraftClient client) {
        MinersVisionClientEffect.active = false;
        MinersVisionClientEffect.ORE_CACHE.clear();
        MinersVisionClientEffect.lastScanOrigin = null;
    }
}