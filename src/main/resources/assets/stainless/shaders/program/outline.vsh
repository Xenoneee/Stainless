#version 150

// Vertex shader for outline post-processing effect
// Transforms vertices and passes texture coordinates to fragment shader

in vec3 Position;
in vec2 UV;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;

out vec2 texCoord;
out vec2 oneTexel;

void main() {
    vec4 outPos = ProjMat * vec4(Position, 1.0);
    // Use z=0.0 for proper 2D post-processing depth
    gl_Position = vec4(outPos.xy, 0.0, 1.0);

    texCoord = UV;
    oneTexel = 1.0 / InSize;
}
