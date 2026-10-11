#version 120

// Tints the vanilla moon texture at night; vanilla moon phases and transparency remain intact.
#define BLOOD_MOON_INTENSITY 0.90 // [0.00 0.20 0.40 0.60 0.75 0.90 1.00]

uniform sampler2D texture;
uniform int worldTime;
uniform float blindness;

varying vec4 color;
varying vec2 coord0;

void main() {
    vec4 col = color * vec4(vec3(1.0 - clamp(blindness, 0.0, 1.0)), 1.0)
             * texture2D(texture, coord0);

    float t = float(worldTime);
    float night = smoothstep(12100.0, 14500.0, t)
                * (1.0 - smoothstep(22200.0, 23800.0, t));

    float moonLuminance = dot(col.rgb, vec3(0.299, 0.587, 0.114));
    vec3 bloodMoon = moonLuminance * vec3(1.60, 0.34, 0.43);
    bloodMoon += vec3(0.045, 0.002, 0.012) * col.a;

    col.rgb = mix(col.rgb, max(bloodMoon, vec3(0.0)), night * BLOOD_MOON_INTENSITY);
    gl_FragData[0] = col;
}
