package com.merry.merryevents.mixin;

import com.merry.merryevents.event.EventIds;
import com.merry.merryevents.engine.EventManager;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts anvil landing dynamics during active AnvilRainEvents to pulverize
 * terrain while strictly protecting container blocks and inventory block
 * entities.
 */
@Mixin(AnvilBlock.class)
public abstract class AnvilBlockMixin {

    /**
     * Pulverizes natural terrain directly beneath the landing anvil while
     * preserving container blocks.
     * Enforces item drop generation if a block entity is somehow targeted to
     * prevent item deletion.
     *
     * @param world              Target world instance
     * @param pos                Position where the anvil solidified
     * @param fallingBlockState  BlockState of the falling anvil
     * @param currentStateInPos  BlockState currently present at target coordinate
     * @param fallingBlockEntity FallingBlockEntity executing the landing
     * @param ci                 Callback info
     */
    @Inject(method = "onLanding", at = @At("TAIL"))
    private void merryevents$shatterAnvilAndBreakBelow(World world, BlockPos pos, BlockState fallingBlockState,
            BlockState currentStateInPos, FallingBlockEntity fallingBlockEntity, CallbackInfo ci) {
        if (!world.isClient() && EventManager.getInstance().isGlobalEventActive(EventIds.ANVIL_RAIN)) {
            BlockPos targetPos = pos.down();
            BlockState targetState = world.getBlockState(targetPos);

            if (!targetState.isAir() && targetState.getHardness(world, targetPos) >= 0.0F) {
                if (!targetState.hasBlockEntity()) {
                    world.breakBlock(targetPos, true);
                }
            }

            world.removeBlock(pos, false);
            world.syncWorldEvent(WorldEvents.ANVIL_DESTROYED, pos, 0);
        }
    }

    /**
     * Pulverizes the supporting surface block when a high-velocity anvil shatters
     * on impact.
     * Bypasses block destruction if the impacted surface contains a block entity.
     *
     * @param world              Target world instance
     * @param pos                Impact coordinate
     * @param fallingBlockEntity Shattered entity instance
     * @param ci                 Callback info
     */
    @Inject(method = "onDestroyedOnLanding", at = @At("TAIL"))
    private void merryevents$breakBelowOnDestroy(World world, BlockPos pos, FallingBlockEntity fallingBlockEntity,
            CallbackInfo ci) {
        if (!world.isClient() && EventManager.getInstance().isGlobalEventActive(EventIds.ANVIL_RAIN)) {
            BlockPos targetPos = pos;
            BlockState targetState = world.getBlockState(targetPos);
            if (targetState.isAir() || targetState.getCollisionShape(world, targetPos).isEmpty()) {
                targetPos = pos.down();
                targetState = world.getBlockState(targetPos);
            }

            if (!targetState.isAir() && targetState.getHardness(world, targetPos) >= 0.0F) {
                if (!targetState.hasBlockEntity()) {
                    world.breakBlock(targetPos, true);
                }
            }
        }
    }
}