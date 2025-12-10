package xenon.addon.stainless.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LimbAnimator; // 1.21.x location
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("limbAnimator")
    LimbAnimator getLimbAnimator();
}
