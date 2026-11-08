#version 450

// =============================================================================
//  Fake-HDR post effect (SDR in, SDR out).
//
//  Gives an 8-bit picture an "HDR look" without touching the swapchain or the
//  display mode: only the highlights are lifted and bloomed, mids get a touch of
//  depth and colour, and a soft shoulder rolls everything back under SDR white so
//  nothing clips. Works in linear light, which is what keeps the lift from
//  muddying colours the way a gamma-space curve does.
//
//  Binary on/off, composable: runs at any resolution, after the optional scaling
//  and CAS passes. Same bindings and push-constant block as before.
//
//  Conventions match upscale.vert: combined-image-sampler at binding 0, the
//  push-constant block leads with vec4 ndc (offset 0), fragTexCoord in [0,1].
// =============================================================================

layout(binding = 0) uniform sampler2D InputTexture;

layout(push_constant) uniform PC {
    vec4 ndc;        // quad NDC rect, consumed by upscale.vert (offset 0)
    vec2 resolution; // input texture size in pixels
} pc;

layout(location = 0) in  vec2 fragTexCoord;
layout(location = 0) out vec4 outColor;

const float GAMMA        = 2.2;
const float HIGHLIGHT_LO = 0.55;  // luma where the highlight lift starts
const float HIGHLIGHT_HI = 0.95;  // luma where it is fully on
const float LIFT         = 0.60;  // extra brightness added at full highlight
const float BLOOM_AMOUNT = 0.30;
const float SHOULDER     = 0.82;  // linear level where the soft clip begins
const float MID_DEPTH    = 1.08;  // >1 deepens mids/darks in linear light
const float SATURATION   = 1.12;

vec3 toLinear(vec3 c) { return pow(max(c, vec3(0.0)), vec3(GAMMA)); }
vec3 toGamma(vec3 c)  { return pow(max(c, vec3(0.0)), vec3(1.0 / GAMMA)); }
float luma(vec3 c)    { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// Only the bright parts of the picture feed the bloom.
vec3 highlightTap(vec2 uv) {
    vec3 c = toLinear(texture(InputTexture, uv).rgb);
    return c * smoothstep(HIGHLIGHT_LO, HIGHLIGHT_HI, luma(c));
}

// Smooth roll-off: identity below SHOULDER, asymptotic to 1.0 above it.
vec3 shoulder(vec3 c) {
    vec3 over = max(c - vec3(SHOULDER), vec3(0.0));
    float room = 1.0 - SHOULDER;
    vec3 rolled = vec3(SHOULDER) + room * (1.0 - exp(-over / room));
    return mix(c, rolled, step(vec3(SHOULDER), c));
}

void main() {
    vec2 uv = fragTexCoord;
    // Taps are sized in pixels, scaled so the glow looks the same at any resolution.
    vec2 px = (1.0 / pc.resolution) * max(pc.resolution.y / 1080.0, 0.5);

    vec3 lin = toLinear(texture(InputTexture, uv).rgb);

    // --- Highlight bloom: two rings of 8 taps, wide and narrow ---
    vec3 bloom = vec3(0.0);
    const vec2 dirs[8] = vec2[8](
        vec2( 1.0,  0.0), vec2(-1.0,  0.0), vec2( 0.0,  1.0), vec2( 0.0, -1.0),
        vec2( 0.7071,  0.7071), vec2(-0.7071,  0.7071),
        vec2( 0.7071, -0.7071), vec2(-0.7071, -0.7071));
    for (int i = 0; i < 8; i++) {
        bloom += highlightTap(uv + dirs[i] * px * 3.0);
        bloom += highlightTap(uv + dirs[i] * px * 8.0);
    }
    bloom *= (1.0 / 16.0);

    // --- Highlight lift, in linear light ---
    float w = smoothstep(HIGHLIGHT_LO, HIGHLIGHT_HI, luma(lin));
    vec3 c = lin * (1.0 + LIFT * w * w);

    // --- Mid/dark depth and a little colour ---
    c = pow(max(c, vec3(0.0)), vec3(MID_DEPTH));
    float l = luma(c);
    c = mix(vec3(l), c, mix(SATURATION, 1.0, w)); // keep bright areas from over-saturating

    c += bloom * BLOOM_AMOUNT;

    outColor = vec4(clamp(toGamma(shoulder(c)), 0.0, 1.0), 1.0);
}
