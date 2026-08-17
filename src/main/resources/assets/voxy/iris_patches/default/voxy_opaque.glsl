// Bundled voxy iris patch — opaque LoD geometry.
//
// This is appended verbatim to voxy's own quads.frag (#version 460 core) by
// IrisVoxyRenderPipeline.patchOpaqueShader, after the generated sampler declarations. It must therefore be
// entirely self-contained: no #version, and no #include of shaderpack files (those are only resolved for
// patch files that actually ship inside the pack).
//
// Everything above this point is already available:
//   - VoxyFragmentParameters / the voxy_emitFragment forward declaration
//   - useMipmaps(), which doubles as voxy's "this model is shaded" flag
//   - texture unit 1, which MDICSectionRenderer.bindRenderingBuffers points at the vanilla lightmap via
//     LightMapHelper.bind(1) before every LoD draw, opaque and translucent alike
//
// What we write is the finished, lit colour into colortex0. That matches how packs handle Distant Horizons
// LoD — Complementary's dh_terrain is likewise a forward pass ending in `/* DRAWBUFFERS:0 */`, not a
// deferred gbuffer fill — so the pack's composite chain (fog, sky blending, tonemap, bloom) picks the LoD up
// as ordinary scene colour. Combined with excludeLodsFromVanillaDepth=false, which blits voxy's depth back
// out to the vanilla depth buffer, the pack's depth-driven atmospherics land on LoD at the right distance
// instead of treating it as infinitely far away.
//
// Lighting here is voxy's own lightmap-based shading rather than the pack's: a patch cannot call into the
// pack's lighting without pulling in its whole legacy-GLSL include tree, which will not compile inside a
// core-profile shader. LoD therefore gets no shadow-map shadows — the same tradeoff DH LoD makes.

layout(location = 0) out vec4 voxy_fragColour;

// The vanilla lightmap, as bound by voxy itself on unit 1. Deliberately NOT iris's own `lightmap` sampler:
// iris hands those out without an accompanying sampler object, and the patch sampler binder leaves whatever
// sampler object the previous draw left on that unit. Iris binds its shadow samplers with
// GL_COMPARE_REF_TO_TEXTURE, and sampling a colour texture through a leftover comparison sampler returns
// zero — which renders every LoD fragment black. LightMapHelper.bind() clears unit 1's sampler each draw, so
// reading from there is both safe and identical to what voxy's own unpatched path uses.
layout(binding = 1) uniform sampler2D voxy_lightSampler;

// Vanilla's fixed per-face shading. quads2.vert applies this itself in the unpatched path and deliberately
// leaves it to the patch here, so the two pipelines have to agree or LoD would pop when shaders toggle.
float voxy_faceShade(uint face) {
    if ((face >> 1u) == 1u) return 0.8;// north / south
    if ((face >> 1u) == 2u) return 0.6;// east / west
    return face == 0u ? 0.5 : 1.0;// down / up
}

// lm arrives as (blockLight, skyLight) normalised to 0..1. Convert back to voxy's own lightmap convention —
// level/16, clamped half a texel off each edge — so patched and unpatched lighting agree exactly; see
// getLighting() in lod/gl46/bindings.glsl, which this mirrors.
vec3 voxy_sampleLightmap(vec2 lm) {
    vec2 uv = clamp(clamp(lm, 0.0, 1.0) * (15.0 / 16.0), vec2(8.0 / 255.0), vec2(248.0 / 255.0));
    return texture(voxy_lightSampler, uv).rgb;
}

vec4 voxy_shadeFragment(VoxyFragmentParameters parameters) {
    vec4 colour = parameters.sampledColour * parameters.tinting;
    float shade = useMipmaps() ? voxy_faceShade(parameters.face) : 1.0;
    colour.rgb *= voxy_sampleLightmap(parameters.lightMap) * shade;
    return colour;
}

void voxy_emitFragment(VoxyFragmentParameters parameters) {
    // Cutout has already been discarded upstream, so anything reaching here is fully opaque.
    voxy_fragColour = vec4(voxy_shadeFragment(parameters).rgb, 1.0);
}
