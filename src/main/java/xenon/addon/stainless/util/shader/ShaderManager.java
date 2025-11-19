package xenon.addon.stainless.util.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages shader effects and framebuffers for ESP rendering.
 * Handles framebuffer lifecycle and shader effect application.
 */
public class ShaderManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<String, ShaderEffect> shaderEffects = new HashMap<>();
    private Framebuffer entityFramebuffer;
    private Framebuffer outlineFramebuffer;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public ShaderManager() {
        createFramebuffers();
    }

    /**
     * Creates or recreates framebuffers with proper size checking.
     * Only recreates if size has changed to avoid unnecessary allocations.
     */
    public void createFramebuffers() {
        if (mc == null || mc.getWindow() == null) return;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();

        // Only recreate if size changed or buffers don't exist
        if (lastWidth == width && lastHeight == height && entityFramebuffer != null && outlineFramebuffer != null) {
            return;
        }

        // Clean up existing framebuffers
        if (entityFramebuffer != null) {
            entityFramebuffer.delete();
        }
        if (outlineFramebuffer != null) {
            outlineFramebuffer.delete();
        }

        // Create new framebuffers
        entityFramebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        entityFramebuffer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        outlineFramebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
        outlineFramebuffer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        lastWidth = width;
        lastHeight = height;
    }

    /**
     * Gets or creates a shader effect
     */
    public ShaderEffect getShaderEffect(String name) {
        return shaderEffects.computeIfAbsent(name, k -> new ShaderEffect(
            Identifier.of("stainless", name)
        ));
    }

    /**
     * Create a colored vertex consumer provider
     */
    public VertexConsumerProvider createVertexConsumers(VertexConsumerProvider.Immediate immediate, Color color) {
        OutlineVertexConsumerProvider outlineProvider = mc.getBufferBuilders().getOutlineVertexConsumers();
        outlineProvider.setColor(color.r, color.g, color.b, color.a);
        return outlineProvider;
    }

    /**
     * Applies a shader effect with custom rendering.
     *
     * @param effect The shader effect to apply
     * @param setupUniforms Callback to set up shader uniforms
     * @param renderContent Callback to render content to the framebuffer
     */
    public void applyShader(ShaderEffect effect, Runnable setupUniforms, Runnable renderContent) {
        // Null checks
        if (effect == null || !effect.isLoaded() || renderContent == null) {
            // Fallback: just render normally
            if (renderContent != null) {
                renderContent.run();
            }
            return;
        }

        // Ensure framebuffers are created
        if (entityFramebuffer == null || mc == null || mc.getFramebuffer() == null) {
            createFramebuffers();
            if (entityFramebuffer == null) {
                renderContent.run();
                return;
            }
        }

        try {
            // Save current framebuffer
            Framebuffer mainFramebuffer = mc.getFramebuffer();

            // Clear entity framebuffer
            entityFramebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            entityFramebuffer.beginWrite(true);

            // Clear with OpenGL directly
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
            renderContent.run();

            // Switch back to main framebuffer
            mainFramebuffer.beginWrite(true);

            // Set up shader uniforms
            if (setupUniforms != null) {
                setupUniforms.run();
            }

            // Apply shader effect
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            effect.render(entityFramebuffer);

            RenderSystem.disableBlend();
        } catch (Exception e) {
            // Silently handle errors to prevent crashes
        }
    }

    /**
     * Gets the entity framebuffer
     */
    public Framebuffer getEntityFramebuffer() {
        return entityFramebuffer;
    }

    /**
     * Gets the outline framebuffer
     */
    public Framebuffer getOutlineFramebuffer() {
        return outlineFramebuffer;
    }

    /**
     * Cleanup all resources
     */
    public void cleanup() {
        if (entityFramebuffer != null) {
            entityFramebuffer.delete();
        }
        if (outlineFramebuffer != null) {
            outlineFramebuffer.delete();
        }
        shaderEffects.values().forEach(ShaderEffect::cleanup);
        shaderEffects.clear();
    }

    /**
     * Resize all framebuffers
     */
    public void resize(int width, int height) {
        if (entityFramebuffer != null) {
            entityFramebuffer.resize(width, height);
        }
        if (outlineFramebuffer != null) {
            outlineFramebuffer.resize(width, height);
        }
        shaderEffects.values().forEach(effect -> effect.resize(width, height));
    }
}
