#version 150

// Fragment shader for outline post-processing effect
// Creates smooth outlines around entities by sampling neighboring pixels in a circular pattern

uniform sampler2D DiffuseSampler;
uniform vec2 texelSize;
uniform float radius;
uniform int samples;
uniform vec4 outlineColor;
uniform vec4 fillColor;
uniform float fillStrength;
uniform float lineStrength;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec4 centerCol = texture(DiffuseSampler, texCoord);

    // Check if current pixel is empty (transparent)
    if (centerCol.a < 0.01) {
        float outline = 0.0;
        float totalWeight = 0.0;

        // Sample neighboring pixels in a circular pattern for smooth outlines
        // This creates a radial blur effect to detect nearby opaque pixels
        for (int x = -samples; x <= samples; x++) {
            for (int y = -samples; y <= samples; y++) {
                // Calculate distance from center to create circular sampling pattern
                float dist = length(vec2(float(x), float(y)));

                // Skip samples outside the circle radius
                if (dist > float(samples)) continue;

                // Calculate texture offset for this sample
                vec2 offset = vec2(float(x), float(y)) * texelSize * radius;
                vec4 sampleCol = texture(DiffuseSampler, texCoord + offset);

                // If we found an opaque pixel nearby, contribute to outline
                if (sampleCol.a > 0.01) {
                    // Weight decreases with distance for smooth falloff
                    float weight = 1.0 - (dist / float(samples));
                    outline = max(outline, weight);
                    totalWeight += weight;
                }
            }
        }

        // Draw outline with smooth falloff based on distance
        if (outline > 0.01) {
            fragColor = vec4(
                outlineColor.rgb,
                outlineColor.a * outline * lineStrength
            );
        } else {
            // No outline needed, discard fragment
            discard;
        }
    } else {
        // Current pixel is opaque - draw fill color blended with original
        vec3 blendedColor = mix(centerCol.rgb, fillColor.rgb, fillStrength);
        float blendedAlpha = centerCol.a * fillColor.a * fillStrength;

        fragColor = vec4(blendedColor, blendedAlpha);
    }
}
