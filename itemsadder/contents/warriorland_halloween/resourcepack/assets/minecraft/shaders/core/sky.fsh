#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    vec4 sky = apply_fog(
        ColorModulator,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        0.0,
        FogSkyEnd,
        FogSkyEnd,
        FogSkyEnd,
        FogColor
    );

    // Preserve the vanilla sky's time-of-day base, then give it a clearly visible
    // seasonal orange/crimson cast without replacing the day/night gradient.
    vec3 halloweenTint = vec3(1.24, 0.52, 0.24);
    sky.rgb = mix(sky.rgb, sky.rgb * halloweenTint, 0.55);

    fragColor = sky;
}
