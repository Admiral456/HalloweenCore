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

    // Halloween tint: keeps the vanilla time-of-day gradient, moon and stars,
    // while shifting the entire sky toward a darker orange/crimson palette.
    vec3 halloweenTint = vec3(1.12, 0.72, 0.48);
    sky.rgb = mix(sky.rgb, sky.rgb * halloweenTint, 0.28);

    fragColor = sky;
}
