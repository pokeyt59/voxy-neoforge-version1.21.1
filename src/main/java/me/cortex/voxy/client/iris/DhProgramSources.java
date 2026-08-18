package me.cortex.voxy.client.iris;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

import java.util.function.Function;

/**
 * Captures and prepares the shaderpack's own Distant Horizons fragment programs so voxy can render its LoD
 * with them, getting the pack's real lighting — shadows, water waves, screen-space reflections, materials.
 *
 * <p>Iris will not do this for us: {@code DHMixinConfigPlugin} gates its DH rendering on
 * {@code isModLoaded("distanthorizons")} and {@code IrisLodRenderProgram} is compiled against DH's API types.
 * Two pieces of Iris are not gated, though, and together they are enough:
 * <ul>
 *   <li>{@code ShaderPackSourceNames.findPotentialStarts()} walks every {@code ProgramId}, which includes
 *       {@code DhTerrain}/{@code DhWater}, so the pack's DH sources are include-processed and arrive here
 *       already flattened even with DH absent.</li>
 *   <li>{@code TransformPatcher.patchDHTerrain} lives outside the gated compat package and mentions no DH
 *       types, so it converts those legacy sources to core-profile GLSL for us.</li>
 * </ul>
 *
 * <p>Only the <b>fragment</b> stage is kept. The patched vertex shader expects DH's packed attribute format
 * ({@code in uvec4 vPosition}, {@code in uvec4 irisExtra}, {@code in vec4 iris_color}) which voxy's procedural
 * MDIC geometry does not produce; voxy supplies its own vertex shader emitting the varyings the fragment
 * reads instead.
 */
public class DhProgramSources {
    /** Fragment varyings the voxy vertex shader must emit for these programs to link. */
    public static final String[] REQUIRED_VARYINGS = {
            "mat", "lmCoord", "upVec", "sunVec", "northVec", "eastVec",
            "normal", "playerPos", "glColor", "iris_FogFragCoord"
    };

    //DH material ids (EDhApiBlockMaterial ordinals). Voxy maps its blocks onto these so a pack's per-material
    //branches fire — Complementary uses leaves, lava, water, grass and illuminated, but other packs branch on
    //more, so the whole table is mapped rather than only the five this pack happens to read.
    //Cross-checked two ways: read out of Complementary's constant-folded shader source, and against DH's
    //published EDhApiBlockMaterial javadoc. Both agree.
    public static final int DH_BLOCK_UNKNOWN = 0;
    public static final int DH_BLOCK_LEAVES = 1;
    public static final int DH_BLOCK_STONE = 2;
    public static final int DH_BLOCK_WOOD = 3;
    public static final int DH_BLOCK_METAL = 4;
    public static final int DH_BLOCK_DIRT = 5;
    public static final int DH_BLOCK_LAVA = 6;
    public static final int DH_BLOCK_DEEPSLATE = 7;
    public static final int DH_BLOCK_SNOW = 8;
    public static final int DH_BLOCK_SAND = 9;
    public static final int DH_BLOCK_TERRACOTTA = 10;
    public static final int DH_BLOCK_NETHER_STONE = 11;
    public static final int DH_BLOCK_WATER = 12;
    public static final int DH_BLOCK_GRASS = 13;
    public static final int DH_BLOCK_AIR = 14;
    public static final int DH_BLOCK_ILLUMINATED = 15;

    //The single line in the patched fragment that declares the vertex colour input.
    private static final String GL_COLOR_DECL = "in vec4 glColor;";

    //DH LoD is untextured — the pack shades a flat per-vertex colour (`vec4 color = vec4(glColor.rgb, 1.0)`).
    //Voxy's LoD is textured, so glColor is redefined as an atlas sample modulated by the vertex tint.
    //Alpha deliberately stays the vertex value: the pack reads glColor.a as vanilla AO (and as a centre
    //factor), not as opacity, so taking it from the texture would corrupt its shading.
    //This mirrors a pattern the pack already relies on — it initialises globals from varyings itself, e.g.
    //`float NdotU = dot(normal, upVec);` — so a non-const global initialiser here is nothing new to it.
    //The atlas lookup mirrors quads.frag exactly: merged quads span several tiles and repeat the texture, so
    //the tile index has to come off a per-pixel modf rather than an interpolated uv, and the mip gradients
    //are taken from the smooth uv so tile wrapping does not blow the derivatives out at tile seams.
    private static final String GL_COLOR_INJECTION = """
            in vec4 voxy_vertexTint;
            in vec2 voxy_uv;
            flat in uvec3 voxy_texData;
            uniform sampler2D voxy_atlas;
            uniform sampler2D voxy_depthBound;
            vec2 voxy_atlasTexPos() {
                vec2 tile;
                vec2 inTile = modf(voxy_uv, tile) * (1.0 / (vec2(3.0, 2.0) * 256.0));
                uint modelId = voxy_texData.x;
                uint face = voxy_texData.y;
                vec2 modelUV = vec2(modelId & 0xFFu, (modelId >> 8) & 0xFFu) * (1.0 / 256.0);
                return modelUV + (vec2(face >> 1u, face & 1u) * (1.0 / (vec2(3.0, 2.0) * 256.0))) + inTile;
            }
            //Voxy allocates the atlas with a full mip chain but only populates up to the block atlas's mip
            //level, and clamps GL_TEXTURE_MAX_LOD on its own sampler object to match. Iris binds voxy_atlas
            //through its own sampler allocation, which never receives that clamp, so with the default
            //NEAREST_MIPMAP_LINEAR filter any fragment whose derivatives select a higher mip samples
            //unpopulated, zero-filled levels and comes back black. That lands precisely on LoD, which is
            //distant and heavily minified, while nearby vanilla geometry picks low mips and looks fine.
            //So the mip is selected explicitly here and clamped, rather than left to the sampler.
            //The clamp is applied by shortening the gradients rather than by resolving a single lod and
            //calling textureLod. Collapsing to one lod also collapses the anisotropy: a scalar picks the
            //longer axis, so terrain viewed at a grazing angle — which is most of a landscape seen from
            //altitude, and all of it once zoomed — samples a far blurrier mip than it needs. Scaling both
            //gradients by the same factor caps the mip while keeping their ratio, so the hardware still
            //filters anisotropically and matches the textureGrad the normal fragment path uses.
            const float VOXY_ATLAS_MAX_LOD = %VOXY_ATLAS_MAX_LOD%;//vanilla's block atlas mip level
            vec4 voxy_sampleAtlas() {
                vec2 texPos = voxy_atlasTexPos();
                if ((voxy_texData.z & 2u) != 0u) {
                    return textureLod(voxy_atlas, texPos, 0.0);
                }
                vec2 smoothUV = voxy_uv * (1.0 / (vec2(3.0, 2.0) * 256.0));
                vec2 dx = dFdx(smoothUV);
                vec2 dy = dFdy(smoothUV);
                vec2 atlasSize = vec2(textureSize(voxy_atlas, 0));
                float rho = max(length(dx * atlasSize), length(dy * atlasSize));
                float maxRho = exp2(VOXY_ATLAS_MAX_LOD);
                if (rho > maxRho) {
                    float shrink = maxRho / rho;
                    dx *= shrink;
                    dy *= shrink;
                }
                return textureGrad(voxy_atlas, texPos, dx, dy);
            }
            //Distinct flat colours per LoD level rather than a ramp, so adjacent levels are unmistakable in a
            //screenshot and the boundaries between them can be counted.
            vec3 voxy_lodDebugColour() {
                uint lvl = (voxy_texData.z >> 8) & 0xFu;
                if (lvl == 0u) return vec3(1.0, 0.0, 0.0);
                if (lvl == 1u) return vec3(1.0, 0.5, 0.0);
                if (lvl == 2u) return vec3(1.0, 1.0, 0.0);
                if (lvl == 3u) return vec3(0.0, 1.0, 0.0);
                if (lvl == 4u) return vec3(0.0, 1.0, 1.0);
                if (lvl == 5u) return vec3(0.0, 0.0, 1.0);
                if (lvl == 6u) return vec3(1.0, 0.0, 1.0);
                return vec3(1.0);
            }
            vec4 glColor = vec4(voxy_sampleAtlas().rgb * voxy_vertexTint.rgb, voxy_vertexTint.a);
            """;

    //Statements prepended to the pack fragment's main(). The pack's DH program has neither of these, because
    //DH LoD is untextured and DH does its own occlusion, so dropping voxy's versions would show up as LoD
    //punching through vanilla terrain and as leaves/grass rendering like opaque squares.
    private static final String MAIN_ENTRY_ANCHOR = "void main() {";
    private static final String MAIN_ENTRY_INJECTION = """
            void main() {
            {
                //Voxy's depth-bound discard, mirroring quads.frag. The 2x2 max closes the 1-pixel
                //rasterisation gaps between independently-instanced section AABBs that would otherwise show
                //as hairline seams.
                ivec2 voxy_fc = ivec2(gl_FragCoord.xy);
                ivec2 voxy_sz = textureSize(voxy_depthBound, 0) - ivec2(1);
                float voxy_m00 = texelFetch(voxy_depthBound, voxy_fc, 0).r;
                float voxy_m10 = texelFetch(voxy_depthBound, min(voxy_fc + ivec2(1, 0), voxy_sz), 0).r;
                float voxy_m01 = texelFetch(voxy_depthBound, min(voxy_fc + ivec2(0, 1), voxy_sz), 0).r;
                float voxy_m11 = texelFetch(voxy_depthBound, min(voxy_fc + ivec2(1, 1), voxy_sz), 0).r;
                if (gl_FragCoord.z < max(max(voxy_m00, voxy_m10), max(voxy_m01, voxy_m11))) discard;
                //Alpha cutout. Sampled at lod 0 like quads.frag does, so mipping cannot erode thin geometry.
                if ((voxy_texData.z & 1u) == 1u && textureLod(voxy_atlas, voxy_atlasTexPos(), 0).a <= 0.1) discard;
            }
            """;

    private final String terrainFragment;
    private final String waterFragment;

    private DhProgramSources(String terrainFragment, String waterFragment) {
        this.terrainFragment = terrainFragment;
        this.waterFragment = waterFragment;
    }

    public String getTerrainFragment() {
        return this.terrainFragment;
    }

    /** @return the patched water fragment, or null when the pack ships no dh_water */
    public String getWaterFragment() {
        return this.waterFragment;
    }

    /**
     * @return prepared sources, or null when the pack has no usable DH programs — in which case voxy keeps
     *         using its bundled patch, so this must degrade quietly rather than throw.
     */
    public static DhProgramSources create(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider) {
        try {
            String terrain = prepare("dh_terrain", directory, sourceProvider);
            if (terrain == null) {
                Logger.info("[voxy-dh] pack has no usable dh_terrain, keeping voxy's bundled shader patch");
                return null;
            }
            String water = prepare("dh_water", directory, sourceProvider);
            Logger.info("[voxy-dh] prepared pack DH programs: terrain=" + terrain.length()
                    + " chars, water=" + (water == null ? "absent" : water.length() + " chars"));
            return new DhProgramSources(terrain, water);
        } catch (Throwable t) {
            Logger.error("[voxy-dh] failed to prepare pack DH programs, keeping voxy's bundled shader patch", t);
            return null;
        }
    }

    private static String prepare(String name, AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider) {
        String vertex = sourceProvider.apply(directory.resolve(name + ".vsh"));
        String fragment = sourceProvider.apply(directory.resolve(name + ".fsh"));
        if (fragment == null) {
            return null;
        }

        //The transformer wants the vertex stage too even though we discard its output, since the two are
        //patched as a unit and it reconciles the varyings between them.
        Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textures = new Object2ObjectOpenHashMap<>();
        var patched = TransformPatcher.patchDHTerrain(name, vertex, null, null, null, fragment, textures);
        if (patched == null) {
            Logger.error("[voxy-dh] " + name + ": transformer returned nothing");
            return null;
        }

        String patchedFragment = patched.get(PatchShaderType.FRAGMENT);
        if (patchedFragment == null) {
            Logger.error("[voxy-dh] " + name + ": transformer produced no fragment stage");
            return null;
        }

        return injectAtlasSampling(name, patchedFragment);
    }

    //Packs fade DH LoD in over distance because DH only ever produces geometry beyond vanilla render range,
    //so anything nearer is assumed to be vanilla's job. Voxy's LoD instead starts right at the vanilla
    //boundary and is occluded by its own depth-bound test, so leaving this in deletes the whole near and mid
    //LoD band -- terrain vanishes into a void with only the most distant fragments surviving.
    //Optional rather than required: a pack that does not fade simply will not contain it.
    private static final String DH_DISTANCE_FADE = "color.a *= smoothstep(far * 0.5f, far * 0.7f, lengthCylinder);";
    private static final String DH_DISTANCE_FADE_REPLACEMENT = "//voxy: distance fade-in removed, see DhProgramSources\n";

    private static final String FRAG_OUTPUT_WRITE = "iris_FragData0 = color;";
    private static final String FRAG_OUTPUT_DEBUG = "iris_FragData0 = vec4(1.0, 0.0, 1.0, 1.0);";

    private static String removeDistanceFade(String name, String fragment) {
        int occurrences = countOccurrences(fragment, DH_DISTANCE_FADE);
        if (occurrences == 0) {
            Logger.info("[voxy-dh] " + name + ": no DH distance fade found, leaving alpha alone");
            return fragment;
        }
        Logger.info("[voxy-dh] " + name + ": removed " + occurrences + " DH distance fade(s)");
        return fragment.replace(DH_DISTANCE_FADE, DH_DISTANCE_FADE_REPLACEMENT);
    }

    private static String injectAtlasSampling(String name, String fragment) {
        //Bail rather than guess on either anchor: a pack whose fragment is shaped differently would otherwise
        //get a silently half-rewritten shader.
        String patched = replaceExactlyOnce(name, fragment, GL_COLOR_DECL,
                GL_COLOR_INJECTION.replace("%VOXY_ATLAS_MAX_LOD%", blockAtlasMipLevel() + ".0"));
        if (patched == null) return null;
        int debug = me.cortex.voxy.client.config.VoxyConfig.CONFIG.dhDebugMode;
        //Mode 2 drops voxy's own discards so they can be ruled in or out as the cause of missing LoD.
        String mainInjection = debug >= 2 ? MAIN_ENTRY_ANCHOR : MAIN_ENTRY_INJECTION;
        patched = replaceExactlyOnce(name, patched, MAIN_ENTRY_ANCHOR, mainInjection);
        if (patched == null) return null;
        patched = removeDistanceFade(name, patched);
        if (debug >= 1) {
            //Overwrite the final colour to isolate where shading goes wrong. Modes 1/2 answered "are the
            //fragments even surviving"; 3 onward each expose one input the pack's lighting depends on, so a
            //wrong one shows up directly instead of having to be inferred from a black result.
            String override = switch (debug) {
                //Atlas x tint, i.e. the albedo fed to the pack. Black here means the texturing is at fault;
                //a correct-looking texture means the fault is downstream in the lighting.
                case 3 -> "iris_FragData0 = vec4(glColor.rgb, 1.0);";
                //Light levels: red = block, green = sky. Black means voxy is handing over no light at all.
                case 4 -> "iris_FragData0 = vec4(lmCoord, 0.0, 1.0);";
                //View-space normal. Should shift coherently as the camera turns; a flat colour means the
                //normals are constant and the pack's NdotU/NdotL terms will collapse.
                case 5 -> "iris_FragData0 = vec4(normal * 0.5 + 0.5, 1.0);";
                //Sun vector, likewise view space. Should change through the day/night cycle.
                case 6 -> "iris_FragData0 = vec4(sunVec * 0.5 + 0.5, 1.0);";
                //Camera-relative position, wrapped so scale errors are visible as banding.
                case 7 -> "iris_FragData0 = vec4(fract(playerPos / 16.0), 1.0);";
                //Atlas sample alone, with the tint taken out of the picture.
                case 8 -> "iris_FragData0 = vec4(voxy_sampleAtlas().rgb, 1.0);";
                //Vertex tint alone.
                case 9 -> "iris_FragData0 = vec4(voxy_vertexTint.rgb, 1.0);";
                //Raw atlas coordinate, to show whether the lookup lands anywhere sensible.
                case 10 -> "iris_FragData0 = vec4(fract(voxy_atlasTexPos() * 256.0), 0.0, 1.0);";
                //DH material id, so the classification can be read off directly rather than inferred from
                //how the lighting looks: red = leaves, green = grass, blue = water, yellow = illuminated,
                //magenta = lava, black = unclassified terrain.
                case 11 -> "iris_FragData0 = vec4("
                        + "float(mat == 1 || mat == 15 || mat == 6), "
                        + "float(mat == 13 || mat == 15), "
                        + "float(mat == 12 || mat == 6), 1.0);";
                //LoD level, so whether the hierarchy actually subdivides can be read off the screen instead
                //of inferred from how sharp things look: red = 0 (finest), orange, yellow, green, cyan,
                //blue, magenta, white = 7+ (coarsest). Zooming should push colours toward red near the
                //centre of view; if the picture is unchanged, detail selection is not responding at all and
                //the fault is in traversal rather than in shading.
                case 12 -> "iris_FragData0 = vec4(voxy_lodDebugColour(), 1.0);";
                default -> FRAG_OUTPUT_DEBUG;
            };
            patched = replaceExactlyOnce(name, patched, FRAG_OUTPUT_WRITE, override);
            Logger.warn("[voxy-dh] " + name + ": DEBUG MODE " + debug + " active -> " + override.strip());
        }
        return patched;
    }

    /**
     * The mip level vanilla's block atlas is built to, which is what voxy populates its own atlas up to.
     *
     * <p>Voxy allocates a full mip chain but only fills this many levels, and clamps GL_TEXTURE_MAX_LOD on its
     * own sampler to match (ModelStore). Iris samples through a sampler allocation of its own that never gets
     * that clamp, so the bound has to be reapplied inside the shader — and it has to be the real value, not a
     * guess. Hardcoding 4 happens to work only because 4 is the default Mipmap Levels setting; at any other
     * setting the shader would read unpopulated levels and distant LoD would come back black.
     *
     * <p>Read at program-build time rather than passed in, because the programs are built before ModelStore
     * exists. The atlas itself is loaded well before any pipeline is constructed, so it is available here.
     */
    private static int blockAtlasMipLevel() {
        try {
            var texture = net.minecraft.client.Minecraft.getInstance().getTextureManager()
                    .getTexture(net.minecraft.resources.ResourceLocation
                            .fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png"));
            if (texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas) {
                return atlas.mipLevel;
            }
            Logger.warn("[voxy-dh] block atlas was not a TextureAtlas, falling back to mip level 4");
        } catch (Exception e) {
            Logger.warn("[voxy-dh] could not read the block atlas mip level, falling back to 4: " + e);
        }
        return 4;
    }

    private static String replaceExactlyOnce(String name, String source, String anchor, String replacement) {
        int occurrences = countOccurrences(source, anchor);
        if (occurrences != 1) {
            Logger.error("[voxy-dh] " + name + ": expected exactly one '" + anchor.strip() + "', found "
                    + occurrences + " — cannot inject, skipping this pack's DH programs");
            return null;
        }
        return source.replace(anchor, replacement);
    }

    /**
     * Builds voxy's real LoD vertex shader with the DH varying block enabled. Using this rather than a stub
     * means the link test also proves the vertex side and the injected fragment code agree on the interface.
     */
    public static String buildDhVertexSource() {
        String source = me.cortex.voxy.client.core.gl.shader.ShaderLoader.parse("voxy:lod/gl46/quads2.vert");
        //ShaderLoader prepends a single canonical #version line; the define has to land after it.
        return source.replaceFirst("(?m)^(#version[^\\n]*\\n)", "$1#define DH_SHADER\n");
    }

    /**
     * Compiles and links the prepared fragment programs against voxy's real LoD vertex shader to prove the
     * interface holds, logging the outcome. Renders nothing and is safe to fail — voxy keeps using its
     * bundled patch. Uniforms are deliberately left unbound: unbound uniforms link fine and read zero, which
     * keeps this isolated to the shader interface.
     */
    public DhPrograms buildPrograms(net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline,
                             net.irisshaders.iris.uniforms.custom.CustomUniforms customUniforms) {
        String vertex;
        try {
            vertex = buildDhVertexSource();
        } catch (Throwable t) {
            Logger.error("[voxy-dh] could not build the DH vertex shader source", t);
            return null;
        }
        var terrain = tryBuild("dh_terrain", this.terrainFragment, vertex, pipeline, customUniforms);
        if (terrain == null) {
            //Without an opaque program there is nothing worth switching to; voxy keeps its bundled patch.
            return null;
        }
        var water = this.waterFragment == null
                ? null
                : tryBuild("dh_water", this.waterFragment, vertex, pipeline, customUniforms);
        return new DhPrograms(terrain, water, customUniforms);
    }

    private static net.irisshaders.iris.gl.program.Program tryBuild(String name, String fragment, String vertex,
                                net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline,
                                net.irisshaders.iris.uniforms.custom.CustomUniforms customUniforms) {
        try {
            //The last argument is the set of RESERVED texture units, not the flipped render targets — iris
            //registers three externally-managed samplers in addLevelSamplers (the block atlas, lightmap and
            //iris_overlay) on the conventional vanilla units 0/1/2, and refuses to add them unless those
            //units are reserved here. Everything else, voxy_atlas included, is allocated a free unit above.
            var builder = net.irisshaders.iris.gl.program.ProgramBuilder.begin(
                    "voxy_" + name, vertex, null, fragment,
                    com.google.common.collect.ImmutableSet.of(0, 1, 2));

            //Same binding recipe iris uses for its own DH program (IrisLodRenderProgram). ProgramBuilder
            //extends ProgramUniforms.Builder and implements SamplerHolder/ImageHolder, so it is all three
            //holders at once. Note this is addDynamicUniforms, not addCommonUniforms — that is what keeps an
            //IdMap out of the picture entirely.
            net.irisshaders.iris.uniforms.CommonUniforms.addDynamicUniforms(
                    builder, net.irisshaders.iris.gl.state.FogMode.PER_VERTEX);
            net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms.addBuiltinReplacementUniforms(builder);
            //Without this the pack's own custom uniforms (Complementary defines a lot of them) all read zero.
            customUniforms.assignTo(builder);

            pipeline.addGbufferOrShadowSamplers(builder, builder, pipeline::getFlippedAfterPrepare,
                    false, true, true, false);

            //Voxy's block atlas, consumed by the texturing injected into the pack's fragment. Supplied lazily
            //because the model bakery has not created it yet at pipeline construction time.
            builder.addDynamicSampler(VoxyDhBindings::currentAtlas, "voxy_atlas");
            //The depth-bound buffer backing the discard injected into the pack fragment; republished
            //per viewport, so likewise resolved lazily.
            builder.addDynamicSampler(VoxyDhBindings::currentDepthBound, "voxy_depthBound");

            var program = builder.build();
            customUniforms.mapholderToPass(builder, program);
            Logger.info("[voxy-dh] " + name + ": PROGRAM BUILT (uniforms + samplers bound)");
            return program;
        } catch (Throwable t) {
            Logger.error("[voxy-dh] " + name + ": PROGRAM BUILD FAILED", t);
            return null;
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = haystack.indexOf(needle);
        while (idx != -1) {
            count++;
            idx = haystack.indexOf(needle, idx + needle.length());
        }
        return count;
    }
}
