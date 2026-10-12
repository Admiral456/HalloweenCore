#version 330 compatibility

uniform sampler2D texture;
uniform int worldTime;
uniform int worldDay;
in vec4 color;
in vec2 texcoord;
layout(location = 0) out vec4 fragColor;

#define HALLOWEEN_BLOOD_MOON_DAY 123456784

#ifndef BLOOD_MOON_STRENGTH
#define BLOOD_MOON_STRENGTH 0.90
#endif

float bloodMoonTransition() {
    if (worldDay != HALLOWEEN_BLOOD_MOON_DAY) return 0.0;
    return smoothstep(18000.0, 18020.0, float(worldTime));
}

void main() {
    vec4 sampled = texture2D(texture, texcoord) * color;
    if (sampled.a < 0.015) discard;

    bool nightTexture = worldTime > 12500 && worldTime < 23500;
    if (nightTexture) {
        vec3 halloweenMoon = sampled.rgb * vec3(1.24, 0.73, 0.47);
        vec3 bloodMoon = sampled.rgb * vec3(1.42, 0.15, 0.20);
        sampled.rgb = mix(halloweenMoon, bloodMoon,
                clamp(bloodMoonTransition() * BLOOD_MOON_STRENGTH, 0.0, 1.0));
    } else {
        sampled.rgb *= vec3(1.08, 0.88, 0.68);
    }
    fragColor = sampled;
}
