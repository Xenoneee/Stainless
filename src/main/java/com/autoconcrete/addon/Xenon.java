package com.autoconcrete.addon;

import com.autoconcrete.addon.modules.AutoMinePlus;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;

public class Xenon extends MeteorAddon {
    // Define the custom category with an icon (e.g., TNT)
    public static final Category XENON_CATEGORY = new Category("Xenon", Items.TNT.getDefaultStack());

    @Override
    public void onInitialize() {
        // Register modules here
        Modules.get().add(new com.autoconcrete.addon.modules.AutoConcrete());
        Modules.get().add(new com.autoconcrete.addon.modules.AntiConcrete());
        Modules.get().add(new com.autoconcrete.addon.modules.AutoTNTplus());
        Modules.get().add(new com.autoconcrete.addon.modules.AntiConcreteDetection());
        Modules.get().add(new com.autoconcrete.addon.modules.AutoMinePlus());
        Modules.get().add(new com.autoconcrete.addon.modules.AutoPearlThrow());
        Modules.get().add(new com.autoconcrete.addon.modules.AutoWebFeetPlace());
        Modules.get().add(new com.autoconcrete.addon.modules.AntiFeetplace());
        Modules.get().add(new com.autoconcrete.addon.modules.BetterScaffold());
        Modules.get().add(new com.autoconcrete.addon.modules.AutoPearlStasis());
        Modules.get().add(new com.autoconcrete.addon.modules.EntityAnimations());
    }

    @Override
    public void onRegisterCategories() {
        // Register the category to Meteor
        Modules.registerCategory(XENON_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.autoconcrete.addon";
    }

    // Removed @Override here to prevent compiler error
    public String getName() {
        return "Xenon";
    }
}
