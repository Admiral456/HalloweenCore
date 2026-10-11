#version 120

// WarriorLand Halloween: cinematic crimson atmosphere.
// These options are exposed in the shader pack's Halloween settings page.
#define SKY_CRIMSON_INTENSITY 0.72 // [0.00 0.20 0.40 0.55 0.72 0.85 1.00]
#define TWILIGHT_GLOW 0.58 // [0.00 0.20 0.40 0.58 0.75 1.00]

uniform int worldTime;
uniform float rainStrength;
uniform float blindness;

varying vec4 color;

void main() {
    float t = float(worldTime);

    // Smooth bands follow Minecraft's client-side day/night clock.
    float dusk = smoothstep(10200.0, 11800.0, t)
               * (1.0 - smoothstep(13000.0, 14600.0, t));
    float dawn = max(
        1.0 - smoothstep(0.0, 1900.0, t),
        smoothstep(22000.0, 23999.0, t)
    );
    float night = smoothstep(12400.0, 14500.0, t)
                * (1.0 - smoothstep(22200.0, 23999.0, t));
    float twilight = clamp(max(dawn, dusk), 0.0, 1.0);

    vec3 dayTone = vec3(1.035, 0.985, 1.010);
    vec3 twilightTone = vec3(1.25, 0.59, 0.64);
    vec3 midnightTone = vec3(1.14, 0.63, 0.78);

    vec3 tone = mix(dayTone, twilightTone, twilight * SKY_CRIMSON_INTENSITY);
    tone = mix(tone, midnightTone, night * SKY_CRIMSON_INTENSITY * 0.62);

    vec3 graded = color.rgb * tone;
    float luminance = dot(max(graded, vec3(0.0)), vec3(0.2126, 0.7152, 0.0722));

    // A restrained ember haze lifts the horizon tones without flattening the sky.
    float horizonWeight = smoothstep(0.035, 0.52, luminance);
    graded += vec3(0.045, 0.004, 0.016) * twilight * TWILIGHT_GLOW * horizonWeight;

    // Rain cools and softens the sky, but leaves the Halloween undertone visible.
    float wetness = clamp(rainStrength, 0.0, 1.0) * 0.28;
    vec3 grey = vec3(dot(graded, vec3(0.299, 0.587, 0.114)));
    graded = mix(graded, grey * vec3(1.04, 0.76, 0.82), wetness);

    graded *= 1.0 - clamp(blindness, 0.0, 1.0);
    gl_FragData[0] = vec4(max(graded, vec3(0.0)), color.a);
}
