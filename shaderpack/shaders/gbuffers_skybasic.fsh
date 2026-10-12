#version 330 compatibility

uniform int worldTime;
uniform int worldDay;
uniform float sunAngle;
uniform float rainStrength;
uniform float viewWidth;
uniform float viewHeight;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform vec3 moonPosition;
uniform mat4 gbufferModelView;
uniform mat4 gbufferProjectionInverse;

in vec4 glcolor;
layout(location = 0) out vec4 fragColor;

#define HALLOWEEN_BLOOD_MOON_DAY 123456784

vec3 screenToView(vec3 screenPos) {
    vec4 ndcPos = vec4(screenPos, 1.0) * 2.0 - 1.0;
    vec4 viewPos = gbufferProjectionInverse * ndcPos;
    return viewPos.xyz / max(abs(viewPos.w), 0.0001);
}

float bloodMoonTransition() {
    if (worldDay != HALLOWEEN_BLOOD_MOON_DAY) return 0.0;
    return smoothstep(18000.0, 18020.0, float(worldTime)) * BLOOD_MOON_STRENGTH;
}

void main() {
    // Keep vanilla star points crisp; sky and stars share this pass in Iris.
    float vertexBrightness = glcolor.r + glcolor.g + glcolor.b;
    if (vertexBrightness > 2.72 && glcolor.a > 0.9) {
        fragColor = glcolor;
        return;
    }

    vec3 viewPos = normalize(screenToView(vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), 1.0)));
    vec3 worldUp = normalize(gbufferModelView[1].xyz);
    float elevation = dot(viewPos, worldUp);
    float horizon = 1.0 - smoothstep(-0.12, 0.56, elevation);

    // Muted autumn daytime and deep violet-blue nights.
    float day = smoothstep(0.22, 0.40, sunAngle) * (1.0 - smoothstep(0.60, 0.78, sunAngle));
    vec3 dayZenith = vec3(0.22, 0.19, 0.30);
    vec3 dayHorizon = vec3(0.69, 0.29, 0.12);
    vec3 nightZenith = vec3(0.018, 0.014, 0.052);
    vec3 nightHorizon = vec3(0.15, 0.045, 0.115);

    vec3 zenith = mix(nightZenith, dayZenith, day);
    vec3 horizonColor = mix(nightHorizon, dayHorizon, day);
    vec3 proceduralSky = mix(zenith, horizonColor, clamp(horizon, 0.0, 1.0));
    proceduralSky = mix(proceduralSky, skyColor * vec3(0.72, 0.43, 0.66), 0.18);
    proceduralSky = mix(proceduralSky, fogColor * vec3(0.82, 0.54, 0.70), horizon * 0.12);

    float blood = bloodMoonTransition();
    vec3 bloodZenith = vec3(0.055, 0.004, 0.018);
    vec3 bloodHorizon = vec3(0.43, 0.012, 0.035);
    vec3 bloodSky = mix(bloodZenith, bloodHorizon, clamp(horizon, 0.0, 1.0));
    bloodSky = mix(bloodSky, fogColor * vec3(1.15, 0.12, 0.15), 0.18);

    // A low-cost halo around the actual moon direction, with extra layers on High.
    float moonElevation = dot(normalize(moonPosition), worldUp);
    float halo = 0.0;
#if HALLOWEEN_QUALITY >= 1
    float angularDistance = length(cross(viewPos, normalize(moonPosition)));
    float nightVisibility = smoothstep(-0.05, 0.25, moonElevation) * (1.0 - day);
    halo = exp(-angularDistance * 58.0) * nightVisibility * 0.055;
#if HALLOWEEN_QUALITY >= 2
    halo += exp(-angularDistance * 23.0) * nightVisibility * 0.028;
#endif
#endif
    vec3 normalHalo = vec3(1.0, 0.29, 0.08) * halo;
    vec3 bloodHalo = vec3(0.92, 0.012, 0.035) * halo * 3.0;
    vec3 result = mix(proceduralSky + normalHalo, bloodSky + bloodHalo, clamp(blood, 0.0, 1.0));

    float fogMix = clamp(HALLOWEEN_FOG_STRENGTH * rainStrength * 0.28, 0.0, 0.28);
    result = mix(result, mix(fogColor, vec3(0.18, 0.045, 0.10), blood), fogMix);
    fragColor = vec4(max(result, vec3(0.0)), glcolor.a);
}
