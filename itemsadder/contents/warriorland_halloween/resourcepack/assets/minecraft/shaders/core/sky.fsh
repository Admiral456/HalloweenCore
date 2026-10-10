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

    // Keep the vanilla sky gradient and fog intact. A restrained warm/crimson
    // grade gives the world a seasonal mood without crushing shadows to black.
    const vec3 halloweenTint = vec3(1.10, 0.76, 0.62);
    sky.rgb = mix(sky.rgb, max(sky.rgb * halloweenTint, vec3(0.0)), 0.24);

    fragColor = sky;
}
