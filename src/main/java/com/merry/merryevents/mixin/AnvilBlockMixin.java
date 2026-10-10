package com.merry.merryevents.mixin;

import com.merry.merryevents.engine.EventManager;
import com.merry.merryevents.event.weather.AnvilRainEvent;
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
 * Mixin intercepting anvil landing physics during AnvilRainEvent to break
 * underlying blocks and shatter upon impact.
 */
@Mixin(AnvilBlock.class)
public abstract class AnvilBlockMixin {
    /**
     * Smashes the block beneath the landing anvil and obliterates the placed anvil
     * block during active anvil showers.
     *
     * @param world              Active world instance
     * @param pos                Position where the anvil attempted to solidify
     * @param fallingBlockState  State of the falling anvil
     * @param currentStateInPos  Replaced state at landing position
     * @param fallingBlockEntity Falling entity instance
     * @param ci                 Callback info
     */
    @Inject(method = "onLanding", at = @At("TAIL"))
    private void merryevents$shatterAnvilAndBreakBelow(World world, BlockPos pos, BlockState fallingBlockState,
            BlockState currentStateInPos, FallingBlockEntity fallingBlockEntity, CallbackInfo ci) {
        if (!world.isClient() && EventManager.getInstance().isGlobalEventActive(AnvilRainEvent.ID)) {
            BlockPos targetPos = pos.down();
            BlockState targetState = world.getBlockState(targetPos);
            if (!targetState.isAir() && targetState.getHardness(world, targetPos) >= 0.0F) {
                world.breakBlock(targetPos, true);
            }

            world.removeBlock(pos, false);
            world.syncWorldEvent(WorldEvents.ANVIL_DESTROYED, pos, 0);
        }
    }

    /**
     * Destroys the surface block directly impacted by the shattered falling anvil
     * during AnvilRainEvent.
     * Evaluates collision shapes to accurately target solid supporting surfaces
     * across bounding transitions.
     *
     * @param world              Active world instance
     * @param pos                Landing destruction coordinate
     * @param fallingBlockEntity Falling entity instance
     * @param ci                 Callback info
     */
    @Inject(method = "onDestroyedOnLanding", at = @At("TAIL"))
    private void merryevents$breakBelowOnDestroy(World world, BlockPos pos, FallingBlockEntity fallingBlockEntity,
            CallbackInfo ci) {
        if (!world.isClient() && EventManager.getInstance().isGlobalEventActive(AnvilRainEvent.ID)) {
            BlockPos targetPos = pos;
            BlockState targetState = world.getBlockState(targetPos);
            if (targetState.isAir() || targetState.getCollisionShape(world, targetPos).isEmpty()) {
                targetPos = pos.down();
                targetState = world.getBlockState(targetPos);
            }

            if (!targetState.isAir() && targetState.getHardness(world, targetPos) >= 0.0F) {
                world.breakBlock(targetPos, true);
            }
        }
    }
}