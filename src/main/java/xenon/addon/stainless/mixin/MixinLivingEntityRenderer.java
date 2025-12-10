package xenon.addon.stainless.mixin;

import xenon.addon.stainless.modules.EntityAnimations;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD")
    )
    private void addon$scale(EntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        var cfg = EntityAnimations.INSTANCE;
        if (cfg != null && cfg.isActive() && cfg.playersEnabled.get()) {
            float s = cfg.playerScale.get().floatValue();
            if (s != 1.0f) matrices.scale(s, s, s);
        }
    }
}
