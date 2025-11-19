#version 150

in vec3 Position;
in vec2 UV;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;

out vec2 texCoord;
out vec2 oneTexel;

void main() {
    vec4 outPos = ProjMat * vec4(Position, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);

    texCoord = UV;
    oneTexel = 1.0 / InSize;
}
