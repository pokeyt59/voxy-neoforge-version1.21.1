// Bundled voxy iris patch — translucent LoD geometry (water, glass, ...).
//
// Compiled as its own program, so it repeats the helpers from voxy_opaque.glsl rather than sharing them.
// The only real difference is that alpha survives: voxy.json enables SRC_ALPHA/ONE_MINUS_SRC_ALPHA blending
// on buffer 0 for the translucent pass, so distant water blends over the terrain already in colortex0
// instead of punching it out.
//
// See voxy_opaque.glsl for why this writes finished colour to colortex0 and why the lighting is voxy's own.

layout(location = 0) out vec4 voxy_fragColour;

// Vanilla lightmap on unit 1, bound by LightMapHelper.bind(1) each draw — see voxy_opaque.glsl for why this
// deliberately avoids iris's own `lightmap` sampler.
layout(binding = 1) uniform sampler2D voxy_lightSampler;

float voxy_faceShade(uint face) {
    if ((face >> 1u) == 1u) return 0.8;// north / south
    if ((face >> 1u) == 2u) return 0.6;// east / west
    return face == 0u ? 0.5 : 1.0;// down / up
}

vec3 voxy_sampleLightmap(vec2 lm) {
    vec2 uv = clamp(clamp(lm, 0.0, 1.0) * (15.0 / 16.0), vec2(8.0 / 255.0), vec2(248.0 / 255.0));
    return texture(voxy_lightSampler, uv).rgb;
}

void voxy_emitFragment(VoxyFragmentParameters parameters) {
    vec4 colour = parameters.sampledColour * parameters.tinting;
    float shade = useMipmaps() ? voxy_faceShade(parameters.face) : 1.0;
    colour.rgb *= voxy_sampleLightmap(parameters.lightMap) * shade;
    voxy_fragColour = colour;
}
