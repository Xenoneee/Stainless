#version 150

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

    // If pixel is empty, check for outline
    if (centerCol.a < 0.01) {
        float outline = 0.0;
        float totalWeight = 0.0;

        // Sample in a circle pattern for smoother outlines
        int sqSamples = samples * samples;
        for (int x = -samples; x <= samples; x++) {
            for (int y = -samples; y <= samples; y++) {
                float dist = length(vec2(float(x), float(y)));
                if (dist > float(samples)) continue;

                vec2 offset = vec2(float(x), float(y)) * texelSize * radius;
                vec4 sampleCol = texture(DiffuseSampler, texCoord + offset);

                if (sampleCol.a > 0.01) {
                    float weight = 1.0 - (dist / float(samples));
                    outline = max(outline, weight);
                    totalWeight += weight;
                }
            }
        }

        // Draw outline with smooth falloff
        if (outline > 0.01) {
            fragColor = vec4(
                outlineColor.rgb,
                outlineColor.a * outline * lineStrength
            );
        } else {
            discard;
        }
    } else {
        // Draw fill - blend with original color
        vec3 blendedColor = mix(centerCol.rgb, fillColor.rgb, fillStrength);
        float blendedAlpha = centerCol.a * fillColor.a * fillStrength;

        fragColor = vec4(blendedColor, blendedAlpha);
    }
}
