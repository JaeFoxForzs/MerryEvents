package com.merry.merryevents.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merry.merryevents.client.effect.FlatMobsClientEffect;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin intercepting living entity rendering pipelines to flatten models into
 * camera-facing 2D billboards.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>>
        extends EntityRenderer<T> implements FeatureRendererContext<T, M> {

    protected LivingEntityRendererMixin(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    /**
     * Intercepts model transform setup to orient entities towards the active camera
     * and compress depth along the Z axis.
     * Evaluates deterministic entity hash variants to randomize horizontal,
     * spherical, or tilted billboard behaviors.
     *
     * @param instance          Target living entity renderer instance
     * @param entity            Active living entity being rendered
     * @param matrices          Matrix stack holding current modelview
     *                          transformations
     * @param animationProgress Interpolated entity age animation progress
     * @param bodyYaw           Interpolated vanilla body yaw
     * @param tickDelta         Partial frame tick delta
     * @param scale             Entity scale multiplier
     * @param original          Original setupTransforms invocation wrapper
     */
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V"))
    private void merryevents$flattenEntityTransforms(
            LivingEntityRenderer<T, M> instance,
            T entity,
            MatrixStack matrices,
            float animationProgress,
            float bodyYaw,
            float tickDelta,
            float scale,
            Operation<Void> original) {

        if (!FlatMobsClientEffect.isActive() || this.dispatcher.camera == null) {
            original.call(instance, entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
            return;
        }

        float cameraYaw = this.dispatcher.camera.getYaw();
        float cameraPitch = this.dispatcher.camera.getPitch();

        original.call(instance, entity, matrices, animationProgress, cameraYaw, tickDelta, scale);

        int variant = Math.floorMod(entity.getId() * 31, 3);
        if (variant == 1) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        } else if (variant == 2) {
            float tiltZ = ((entity.getId() & 1) == 0) ? 6.0F : -6.0F;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tiltZ));
        }

        matrices.scale(1.0F, 1.0F, 0.002F);
    }

    /**
     * Intercepts model angle application to neutralize head yaw and pitch offsets
     * during flat mob rendering.
     * Ensures entity faces remain oriented flush toward the camera plane without
     * vanishing into profile slice angles.
     *
     * @param model             Target entity model instance
     * @param entity            Active entity being rendered
     * @param limbAngle         Interpolated limb swing position
     * @param limbDistance      Interpolated limb swing speed
     * @param animationProgress Continuous tick age progression
     * @param headYaw           Head rotation relative to body yaw in degrees
     * @param headPitch         Head pitch angle in degrees
     * @param original          Original setAngles invocation wrapper
     */
    @WrapOperation(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V"))
    private void merryevents$neutralizeHeadAngles(
            EntityModel<T> model,
            Entity entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            Operation<Void> original) {

        if (FlatMobsClientEffect.isActive() && this.dispatcher.camera != null) {
            original.call(model, entity, limbAngle, limbDistance, animationProgress, 0.0F, 0.0F);
        } else {
            original.call(model, entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        }
    }
}