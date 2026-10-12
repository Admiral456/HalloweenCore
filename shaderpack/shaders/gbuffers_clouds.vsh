#version 330 compatibility
out vec4 color;
out vec2 texcoord;
void main() {
    color = gl_Color;
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    gl_Position = ftransform();
}
