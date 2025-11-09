package xenon.addon.stainless;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.impl.util.log.Log;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import xenon.addon.stainless.modules.*;

public class Stainless extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category STAINLESS_CATEGORY = new Category("Stainless", Items.TNT.getDefaultStack());

    @Override
    public void onInitialize() {
        LOG.info("Initializing Stainless");

        initializeModules(Modules.get());
        Config.get().chatFeedback.set(false);
        Config.get().save();
    }

        private void initializeModules(Modules modules) {
        Modules.get().add(new AntiConcrete());
        Modules.get().add(new AntiConcreteDetection());
        Modules.get().add(new AntiFeetplace());
        Modules.get().add(new AutoConcrete());
        Modules.get().add(new AutoMinePlus());
        Modules.get().add(new AutoPearlStasis());
        Modules.get().add(new AutoPearlThrow());
        Modules.get().add(new AutoTNTplus());
        Modules.get().add(new AutoWebFeetPlace());
        Modules.get().add(new BetterScaffold());
        Modules.get().add(new EntityAnimations());
    }

    @Override
    public void onRegisterCategories() {
        // Register the category to Meteor
        Modules.registerCategory(STAINLESS_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "xenon.addon.stainless";
    }

    public String getName() {
        return "Stainless";
    }
}
