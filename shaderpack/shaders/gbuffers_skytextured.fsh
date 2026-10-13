#version 330 compatibility

uniform sampler2D texture;
uniform int worldTime;
uniform int worldDay;
in vec4 color;
in vec2 texcoord;
layout(location = 0) out vec4 fragColor;

#define HALLOWEEN_BLOOD_MOON_DAY 123456784

#define BLOOD_MOON_STRENGTH 0.90 // [0.60 0.75 0.90 1.00]

float bloodMoonTransition() {
    if (worldDay != HALLOWEEN_BLOOD_MOON_DAY) return 0.0;
    return smoothstep(18000.0, 18020.0, float(worldTime));
}

void main() {
    vec4 sampled = texture2D(texture, texcoord) * color;
    if (sampled.a < 0.015) discard;

    bool nightTexture = worldTime > 12500 && worldTime < 23500;
    if (nightTexture) {
        vec3 halloweenMoon = sampled.rgb * vec3(0.88, 0.90, 0.94);
        vec3 bloodMoon = sampled.rgb * vec3(1.30, 0.28, 0.32);
        sampled.rgb = mix(halloweenMoon, bloodMoon,
                clamp(bloodMoonTransition() * BLOOD_MOON_STRENGTH, 0.0, 1.0));
    } else {
        sampled.rgb *= vec3(1.02, 1.00, 0.94);
    }
    fragColor = sampled;
}
