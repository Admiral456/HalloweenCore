#version 330 compatibility

uniform sampler2D texture;
uniform int worldTime;
uniform int worldDay;
uniform float rainStrength;
in vec4 color;
in vec2 texcoord;
layout(location = 0) out vec4 fragColor;

#define HALLOWEEN_BLOOD_MOON_DAY 123456784

void main() {
    vec4 cloud = texture2D(texture, texcoord) * color;
    if (cloud.a < 0.015) discard;
    float blood = worldDay == HALLOWEEN_BLOOD_MOON_DAY
            ? smoothstep(18000.0, 18020.0, float(worldTime)) : 0.0;

    vec3 normalTint = mix(vec3(0.62, 0.48, 0.62), vec3(0.86, 0.36, 0.16), 0.30);
    vec3 bloodTint = vec3(0.50, 0.035, 0.075);
    cloud.rgb *= mix(normalTint, bloodTint, blood);
    cloud.rgb *= 1.0 - clamp(rainStrength * 0.12, 0.0, 0.12);
    fragColor = cloud;
}
