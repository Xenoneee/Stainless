package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

/**
 * Anim/Render tweaks for end crystals & players.
 * Mixins read these via EntityAnimations.INSTANCE.
 */
public class EntityAnimations extends StainlessModule {
    public static EntityAnimations INSTANCE;

    // Groups (sections in GUI)
    private final SettingGroup sgCrystals     = settings.createGroup("Crystals");
    private final SettingGroup sgCrystalParts = settings.createGroup("Crystal Parts");
    private final SettingGroup sgPlayers      = settings.createGroup("Players");

    // ---- Crystals main
    public final Setting<Boolean> crystalsEnabled = sgCrystals.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable crystal overrides.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> crystalScale = sgCrystals.add(new DoubleSetting.Builder()
        .name("crystal-scale")
        .description("Scale applied to the whole crystal.")
        .defaultValue(1.0)
        .min(0.1).sliderMin(0.1)
        .max(8.0).sliderMax(3.0)
        .build()
    );

    public final Setting<Double> crystalFloatFactor = sgCrystals.add(new DoubleSetting.Builder()
        .name("float-factor")
        .description("Bob amplitude multiplier (0 = vanilla).")
        .defaultValue(0.0) // 0 = leave vanilla
        .min(0.0).sliderMin(0.0)
        .max(4.0).sliderMax(2.0)
        .build()
    );

    public final Setting<Double> crystalRotationSpeed = sgCrystals.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Yaw rotation speed multiplier (0 = vanilla).")
        .defaultValue(0.0) // 0 = leave vanilla
        .min(-10.0).sliderMin(-5.0)
        .max(10.0).sliderMax(5.0)
        .build()
    );

    // ---- Crystal parts (names MUST match renderer mixin)
    public final Setting<Boolean> crystalInner = sgCrystalParts.add(new BoolSetting.Builder()
        .name("inner")
        .description("Render inner glass layer.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> crystalOuter = sgCrystalParts.add(new BoolSetting.Builder()
        .name("outer")
        .description("Render outer glass layer.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> crystalCore = sgCrystalParts.add(new BoolSetting.Builder()
        .name("core")
        .description("Render cube core.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> crystalBottom = sgCrystalParts.add(new BoolSetting.Builder()
        .name("bottom")
        .description("Render bedrock/obsidian base.")
        .defaultValue(true)
        .build()
    );

    // ---- Players
    public final Setting<Boolean> playersEnabled = sgPlayers.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable player overrides.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> playerScale = sgPlayers.add(new DoubleSetting.Builder()
        .name("player-scale")
        .description("Scale applied to player models.")
        .defaultValue(1.0)
        .min(0.1).sliderMin(0.1)
        .max(8.0).sliderMax(3.0)
        .build()
    );

    public EntityAnimations() {
        // Put the module in your custom category
        super(Stainless.STAINLESS_CATEGORY, "EntityAnimations", "Crystal & player animation/scale controls.");
        INSTANCE = this;
    }
}
