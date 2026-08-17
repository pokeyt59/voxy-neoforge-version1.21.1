// Bundled voxy iris patch — translucent LoD geometry (water, glass, ice, ...).
//
// Compiled as its own program, so it repeats the helpers from voxy_opaque.glsl rather than sharing them.
// voxy.json enables SRC_ALPHA/ONE_MINUS_SRC_ALPHA blending on buffer 0 for the translucent pass, so distant
// water blends over the terrain already in colortex0 instead of punching it out.
//
// See voxy_opaque.glsl for why this writes finished colour to colortex0 and why the lighting is voxy's own.
//
// APPROXIMATE WATER: the pack's real water treatment (wave normals, screen-space reflections) lives inside
// its own dh_water program, gated on a Distant Horizons material id, and cannot be reached from here — it
// pulls in the pack's whole legacy-GLSL include tree, which will not compile in a core-profile shader. What
// this does instead is a cheap Schlick fresnel: glancing-angle fragments get brighter and more opaque, and
// tint toward the current sky colour. That is enough to stop LoD water reading as a flat dead slab and to
// make it respond to view angle and time of day, but it is NOT the pack's reflections and will not match
// them exactly at the vanilla/LoD boundary. The three constants below are the tuning knobs.

const float VOXY_WATER_F0          = 0.02;// Schlick reflectance of water at normal incidence
const float VOXY_WATER_SHEEN       = 0.60;// how strongly the sky tint is mixed in at glancing angles
const float VOXY_WATER_OPACITY_ADD = 0.45;// how much more opaque a glancing-angle fragment becomes

layout(location = 0) out vec4 voxy_fragColour;

// Vanilla lightmap on unit 1, bound by LightMapHelper.bind(1) each draw — see voxy_opaque.glsl for why this
// deliberately avoids iris's own `lightmap` sampler.
layout(binding = 1) uniform sampler2D voxy_lightSampler;

float voxy_faceShade(uint face) {
    if ((face >> 1u) == 1u) return 0.8;// north / south
    if ((face >> 1u) == 2u) return 0.6;// east / west
    return face == 0u ? 0.5 : 1.0;// down / up
}

// Face index layout matches quads2.vert: the high bits select the axis (0 = Y, 1 = Z, 2 = X) and the low bit
// selects its sign, so face 0/1 are down/up.
vec3 voxy_faceNormal(uint face) {
    float facing = (face & 1u) == 1u ? 1.0 : -1.0;
    uint axis = face >> 1u;
    if (axis == 0u) return vec3(0.0, facing, 0.0);
    if (axis == 1u) return vec3(0.0, 0.0, facing);
    return vec3(facing, 0.0, 0.0);
}

vec2 voxy_lightmapUV(vec2 lm) {
    return clamp(clamp(lm, 0.0, 1.0) * (15.0 / 16.0), vec2(8.0 / 255.0), vec2(248.0 / 255.0));
}

vec3 voxy_sampleLightmap(vec2 lm) {
    return texture(voxy_lightSampler, voxy_lightmapUV(lm)).rgb;
}

// Stand-in for the sky colour: the sky column of the vanilla lightmap is the ambient sky contribution at the
// current time of day, so it already warms at sunset and falls away at night. Block light is forced to zero
// so torches near the shore do not leak into the reflection tint.
vec3 voxy_skyTone(float skyLight) {
    return voxy_sampleLightmap(vec2(0.0, skyLight));
}

void voxy_emitFragment(VoxyFragmentParameters parameters) {
    vec4 colour = parameters.sampledColour * parameters.tinting;
    float shade = useMipmaps() ? voxy_faceShade(parameters.face) : 1.0;
    colour.rgb *= voxy_sampleLightmap(parameters.lightMap) * shade;

    // Schlick fresnel against the face normal. relativePos points from the camera to this fragment, so the
    // view vector is its negation.
    vec3 normal = voxy_faceNormal(parameters.face);
    vec3 viewDir = normalize(-parameters.relativePos);
    float fresnel = VOXY_WATER_F0 + (1.0 - VOXY_WATER_F0) * pow(1.0 - clamp(dot(normal, viewDir), 0.0, 1.0), 5.0);

    colour.rgb = mix(colour.rgb, voxy_skyTone(parameters.lightMap.y), fresnel * VOXY_WATER_SHEEN);
    colour.a = clamp(colour.a + fresnel * VOXY_WATER_OPACITY_ADD, 0.0, 1.0);

    voxy_fragColour = colour;
}
