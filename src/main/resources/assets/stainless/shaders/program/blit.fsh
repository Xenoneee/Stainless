#version 150

// Simple blit (copy) fragment shader
// Copies the input texture to the output without modification

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler, texCoord);
}
