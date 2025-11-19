package xenon.addon.stainless.modules;

import xenon.addon.stainless.Stainless;
import xenon.addon.stainless.StainlessModule;
import xenon.addon.stainless.util.shader.ShaderManager;
import xenon.addon.stainless.util.shader.ShaderEffect;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Advanced shader-based ESP with customizable outlines and fill colors.
 * Supports multiple shader modes including gradients, rainbow, and custom colors.
 */
public class Shader extends StainlessModule {

    public enum ShaderMode {
        Default,
        Gradient,
        Rainbow,
        Astolfo,
        Fade
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");
    private final SettingGroup sgColors  = settings.createGroup("Colors");
    private final SettingGroup sgTargets = settings.createGroup("Targets");

    // General
    private final Setting<ShaderMode> shaderMode = sgGeneral.add(new EnumSetting.Builder<ShaderMode>()
        .name("mode")
        .description("Shader mode.")
        .defaultValue(ShaderMode.Gradient)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum range to render entities.")
        .defaultValue(169)
        .min(1).sliderRange(10, 256)
        .build()
    );

    private final Setting<Integer> lineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("line-width")
        .description("Width of shader outline.")
        .defaultValue(10)
        .min(1).sliderRange(1, 20)
        .build()
    );

    private final Setting<Double> step = sgGeneral.add(new DoubleSetting.Builder()
        .name("step")
        .description("Offset between entities for wave effects.")
        .defaultValue(0.3)
        .sliderRange(0.01, 5.0)
        .build()
    );

    private final Setting<Double> gradientSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("gradient-speed")
        .description("Speed of gradient animation.")
        .defaultValue(1.0)
        .sliderRange(0.1, 10.0)
        .visible(() -> shaderMode.get() == ShaderMode.Gradient)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Speed of color animation.")
        .defaultValue(1.0)
        .sliderRange(0.1, 10.0)
        .build()
    );

    // Render
    private final Setting<Double> lineStrength = sgRender.add(new DoubleSetting.Builder()
        .name("line-strength")
        .description("Opacity of outline.")
        .defaultValue(1.0)
        .range(0, 1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Double> fillStrength = sgRender.add(new DoubleSetting.Builder()
        .name("fill-strength")
        .description("Opacity of fill.")
        .defaultValue(0.9)
        .range(0, 1)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> quality = sgRender.add(new IntSetting.Builder()
        .name("quality")
        .description("Shader quality (higher = smoother).")
        .defaultValue(4)
        .min(1).sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> inner = sgRender.add(new BoolSetting.Builder()
        .name("inner")
        .description("Render inner fill.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> texture = sgRender.add(new BoolSetting.Builder()
        .name("texture")
        .description("Render entity textures.")
        .defaultValue(false)
        .build()
    );

    // Colors
    private final Setting<SettingColor> fill = sgColors.add(new ColorSetting.Builder()
        .name("fill")
        .description("Primary fill color.")
        .defaultValue(new SettingColor(203, 227, 240, 102))
        .build()
    );

    private final Setting<SettingColor> fillSecond = sgColors.add(new ColorSetting.Builder()
        .name("fill-second")
        .description("Secondary fill color.")
        .defaultValue(new SettingColor(203, 227, 240, 255))
        .visible(() -> shaderMode.get() == ShaderMode.Gradient || shaderMode.get() == ShaderMode.Fade)
        .build()
    );

    private final Setting<SettingColor> outline = sgColors.add(new ColorSetting.Builder()
        .name("outline")
        .description("Primary outline color.")
        .defaultValue(new SettingColor(203, 227, 240, 255))
        .build()
    );

    private final Setting<SettingColor> outlineSecond = sgColors.add(new ColorSetting.Builder()
        .name("outline-second")
        .description("Secondary outline color.")
        .defaultValue(new SettingColor(203, 227, 240, 198))
        .visible(() -> shaderMode.get() == ShaderMode.Gradient || shaderMode.get() == ShaderMode.Fade)
        .build()
    );

    // Targets
    private final Setting<Boolean> crystals = sgTargets.add(new BoolSetting.Builder()
        .name("crystals")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> crystalRange = sgTargets.add(new IntSetting.Builder()
        .name("crystal-range")
        .defaultValue(60)
        .min(1).sliderRange(10, 256)
        .visible(crystals::get)
        .build()
    );

    private final Setting<Boolean> items = sgTargets.add(new BoolSetting.Builder()
        .name("items")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> itemRange = sgTargets.add(new IntSetting.Builder()
        .name("item-range")
        .defaultValue(60)
        .min(1).sliderRange(10, 256)
        .visible(items::get)
        .build()
    );

    private final Setting<Boolean> animals = sgTargets.add(new BoolSetting.Builder()
        .name("animals")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hostiles = sgTargets.add(new BoolSetting.Builder()
        .name("hostiles")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> players = sgTargets.add(new BoolSetting.Builder()
        .name("players")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> self = sgTargets.add(new BoolSetting.Builder()
        .name("self")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> exp = sgTargets.add(new BoolSetting.Builder()
        .name("exp")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pearls = sgTargets.add(new BoolSetting.Builder()
        .name("pearls")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> minecarts = sgTargets.add(new BoolSetting.Builder()
        .name("minecarts")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> dead = sgTargets.add(new BoolSetting.Builder()
        .name("dead")
        .description("Render dead entities.")
        .defaultValue(false)
        .build()
    );

    private ShaderManager shaderManager;

    public Shader() {
        super(Stainless.STAINLESS_CATEGORY, "Shader", "Advanced shader ESP with model outlines.");
    }

    @Override
    public void onActivate() {
        if (shaderManager == null) {
            shaderManager = new ShaderManager();
        }
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        if (shaderManager != null) {
            shaderManager.cleanup();
            shaderManager = null;
        }
    }

    // =====================================
    //             RENDER
    // =====================================

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null || shaderManager == null) return;

        double t = (System.currentTimeMillis() % 10000L) / 1000.0;

        // Get the shader effect
        ShaderEffect effect = shaderManager.getShaderEffect("outline");

        // Apply shader with entity rendering
        shaderManager.applyShader(effect, () -> {
            // Set shader uniforms (convert Double to float with explicit cast)
            effect.setUniformValue("radius", lineWidth.get().floatValue() / 10.0f);
            effect.setUniformValue("samples", quality.get().floatValue());
            effect.setUniformValue("lineStrength", lineStrength.get().floatValue());
            effect.setUniformValue("fillStrength", fillStrength.get().floatValue());
            effect.setUniformValue("texelSize",
                1.0f / mc.getWindow().getScaledWidth(),
                1.0f / mc.getWindow().getScaledHeight()
            );
        }, () -> {
            // Render entities to framebuffer
            renderEntities(event);
        });
    }

    /**
     * Renders all entities in range with the shader effect applied.
     */
    private void renderEntities(Render3DEvent event) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();

        double t = (System.currentTimeMillis() % 10000L) / 1000.0;

        for (Entity e : mc.world.getEntities()) {
            if (!shouldRender(e)) continue;

            double maxRange = getEntityRange(e);
            if (mc.player.squaredDistanceTo(e) > maxRange * maxRange) continue;

            // Get colors for this entity
            Color fillColor = getFillColor(e, t);
            Color outlineColor = getOutlineColor(e, t);

            // Render the entity
            renderEntity(event, e, dispatcher, camera, fillColor, outlineColor);
        }
    }

    /**
     * Renders a single entity with the shader effect.
     * Uses proper position interpolation for smooth rendering.
     */
    private void renderEntity(Render3DEvent event, Entity entity,
                              EntityRenderDispatcher dispatcher, Vec3d camera,
                              Color fillColor, Color outlineColor) {
        try {
            float tickDelta = event.tickDelta;
            MatrixStack matrices = event.matrices;

            // Calculate interpolated position using prevX/prevY/prevZ
            double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX()) - camera.x;
            double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY()) - camera.y;
            double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()) - camera.z;

            // Get entity renderer
            @SuppressWarnings("unchecked")
            EntityRenderer<Entity, EntityRenderState> renderer =
                (EntityRenderer<Entity, EntityRenderState>) dispatcher.getRenderer(entity);

            // Create render state
            EntityRenderState state = renderer.createRenderState();
            renderer.updateRenderState(entity, state, tickDelta);

            // Get vertex consumers
            VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

            matrices.push();
            matrices.translate(x, y, z);

            // Render entity
            int light = texture.get() ? dispatcher.getLight(entity, tickDelta) : 0x00F000F0;
            renderer.render(state, matrices, immediate, light);

            matrices.pop();

            immediate.draw();
        } catch (Exception ignored) {
            // Skip entities that fail to render
        }
    }

    // =====================================
    //          TARGET FILTER
    // =====================================

    /**
     * Determines if an entity should be rendered based on target settings.
     */
    private boolean shouldRender(Entity e) {
        if (!dead.get() && !e.isAlive()) return false;
        if (e == mc.player) return self.get();
        if (e instanceof PlayerEntity) return players.get();
        if (e instanceof EndCrystalEntity) return crystals.get();
        if (e instanceof ItemEntity) return items.get();
        if (e instanceof HostileEntity) return hostiles.get();
        if (e instanceof PassiveEntity) return animals.get();
        if (e instanceof ExperienceOrbEntity) return exp.get();
        if (e instanceof EnderPearlEntity) return pearls.get();
        if (e instanceof AbstractMinecartEntity) return minecarts.get();
        return false;
    }

    private double getEntityRange(Entity e) {
        if (e instanceof EndCrystalEntity) return crystalRange.get();
        if (e instanceof ItemEntity) return itemRange.get();
        return range.get();
    }

    // =====================================
    //                COLORS
    // =====================================

    private Color getFillColor(Entity e, double t) {
        SettingColor a = fill.get();
        SettingColor b = fillSecond.get();

        return switch (shaderMode.get()) {
            case Default -> toColor(a);
            case Gradient -> gradient(e, t, a, b, gradientSpeed.get());
            case Rainbow -> rainbow(e, t, a.a);
            case Astolfo -> astolfo(e, t, a.a);
            case Fade -> fade(e, t, a, b);
        };
    }

    private Color getOutlineColor(Entity e, double t) {
        SettingColor a = outline.get();
        SettingColor b = outlineSecond.get();

        return switch (shaderMode.get()) {
            case Default -> toColor(a);
            case Gradient -> gradient(e, t, a, b, gradientSpeed.get());
            case Rainbow -> rainbow(e, t, a.a);
            case Astolfo -> astolfo(e, t, a.a);
            case Fade -> fade(e, t, a, b);
        };
    }

    private Color gradient(Entity e, double t, SettingColor a, SettingColor b, double spd) {
        double dist = Math.sqrt(mc.player.squaredDistanceTo(e));
        double n = MathHelper.clamp(dist / range.get(), 0, 1);
        double phase = (t * spd + e.getId() * step.get()) % 1.0;
        n = (n + phase) / 2.0;

        return new Color(
            (int) (a.r + (b.r - a.r) * n),
            (int) (a.g + (b.g - a.g) * n),
            (int) (a.b + (b.b - a.b) * n),
            a.a
        );
    }

    private Color rainbow(Entity e, double t, int alpha) {
        float hue = (float) ((t * speed.get() + e.getId() * step.get()) % 1.0);
        java.awt.Color rw = java.awt.Color.getHSBColor(hue, 1f, 1f);
        return new Color(rw.getRed(), rw.getGreen(), rw.getBlue(), alpha);
    }

    private Color astolfo(Entity e, double t, int alpha) {
        float hue = (float) (((t * speed.get() + e.getId() * step.get()) % 1.0) * 0.3 + 0.75);
        java.awt.Color rw = java.awt.Color.getHSBColor(hue, 0.7f, 1f);
        return new Color(rw.getRed(), rw.getGreen(), rw.getBlue(), alpha);
    }

    private Color fade(Entity e, double t, SettingColor a, SettingColor b) {
        double phase = Math.abs(Math.sin((t * speed.get() + e.getId() * step.get()) * Math.PI));

        return new Color(
            (int) (a.r + (b.r - a.r) * phase),
            (int) (a.g + (b.g - a.g) * phase),
            (int) (a.b + (b.b - a.b) * phase),
            a.a
        );
    }

    private Color toColor(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }
}
