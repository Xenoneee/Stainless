package xenon.addon.stainless.mixin;

import xenon.addon.stainless.util.CrystalPartGate;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels ModelPart.render() calls for End Crystal parts based on CrystalPartGate.
 * The gate increments its counter when shouldRender() is called, so cancellation doesn't break tracking.
 */
@Mixin(ModelPart.class)
public class MixinModelPart {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V",
        at = @At("HEAD"), cancellable = true)
    private void xenon$gatePlain(MatrixStack matrices, VertexConsumer vc, int light, int overlay, CallbackInfo ci) {
        if (!CrystalPartGate.shouldRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
        at = @At("HEAD"), cancellable = true)
    private void xenon$gateColored(MatrixStack matrices, VertexConsumer vc, int light, int overlay, int color, CallbackInfo ci) {
        if (!CrystalPartGate.shouldRender()) {
            ci.cancel();
        }
    }
}
