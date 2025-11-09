package xenon.addon.stainless.mixin;

import xenon.stainless.mixin.mixinhelper.CrystalPartGate;
import xenon.addon.stainless.modules.EntityAnimations;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystalEntityRenderer.class)
public final class MixinEndCrystalEntityRenderer {
    @Unique private float xenon$oldAge;
    @Unique private boolean xenon$ageDirty;

    @Inject(
        method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD")
    )
    private void xenon$pre(EndCrystalEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        // Start with sane defaults; most PvP crystals do render the bottom.
        boolean vanillaShowsBottom = true;

        EntityAnimations m = EntityAnimations.INSTANCE;
        if (m != null && m.isActive() && m.crystalsEnabled.get()) {
            // Scale
            float s = m.crystalScale.get().floatValue();
            if (s != 1.0f) matrices.scale(s, s, s);

            // Rotation speed (age multiplier)
            float speed = m.crystalRotationSpeed.get().floatValue();
            if (speed != 1.0f && speed != 0.0f) {
                xenon$oldAge = state.age;
                xenon$ageDirty = true;
                state.age = state.age * speed;
            }

            // Bob amplitude tweak
            float factor = m.crystalFloatFactor.get().floatValue();
            if (factor != 1.0f) {
                double bob = Math.sin(state.age * 0.2D) / 2.0D + 0.5D;
                bob = bob * bob + bob;
                double base = bob * 0.2D;
                matrices.translate(0.0D, base * (factor - 1.0D), 0.0D);
            }

            // Kick off the part gate for this draw
            CrystalPartGate.begin(
                vanillaShowsBottom,             // don’t reference state field that isn’t mapped here
                m.crystalOuter.get(),           // show outer glass?
                m.crystalInner.get(),           // show inner glass?
                m.crystalCore.get(),            // show core cube?
                m.crystalBottom.get()           // show bottom platform?
            );
        } else {
            // Module disabled → draw everything normally
            CrystalPartGate.begin(true, true, true, true, true);
            CrystalPartGate.end();
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("RETURN")
    )
    private void xenon$post(EndCrystalEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        CrystalPartGate.end();
        if (xenon$ageDirty) {
            state.age = xenon$oldAge;
            xenon$ageDirty = false;
        }
    }
}
