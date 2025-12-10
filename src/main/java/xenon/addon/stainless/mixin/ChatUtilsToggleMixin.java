package xenon.addon.stainless.mixin;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppress Meteor's generic "Toggled %s %s." chat for your modules (binds & hold).
 * ChatUtils is not obfuscated, so remap=false.
 */
@Mixin(value = ChatUtils.class, remap = false)
public abstract class ChatUtilsToggleMixin {
    // Exact JVM descriptor for info(String, Object...)
    @Inject(method = "info(Ljava/lang/String;[Ljava/lang/Object;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void stainless$suppressToggled(String message, Object[] args, CallbackInfo ci) {
        if (message == null) return;

        // Be loose; catch both the format string ("Toggled %s %s.") and the already-formatted version.
        String m = message.trim().toLowerCase();
        if (!m.startsWith("toggled ")) return;

        // If we can resolve the module from arg[0], only cancel for your addon modules.
        // If we can't resolve, cancel anyway (prevents any generic toggle line from slipping through).
        Module mod = null;
        if (args != null && args.length >= 1) {
            String modName = String.valueOf(args[0]);
            mod = Modules.get().getAll().stream()
                .filter(x -> x.name.equals(modName))
                .findFirst().orElse(null);
        }

        if (mod == null || mod.getClass().getName().startsWith("xenon.addon.stainless")) {
            ci.cancel(); // your StainlessModule prints the nice ON/OFF line already
        }
    }
}
