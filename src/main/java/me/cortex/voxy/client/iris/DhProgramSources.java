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

    //DH material ids, as they appear already constant-folded in the patched source. Voxy maps its blocks onto
    //these so the pack's per-material branches (leaves subsurface, grass smoothness, emissive, water) fire.
    public static final int DH_BLOCK_LEAVES = 1;
    public static final int DH_BLOCK_LAVA = 6;
    public static final int DH_BLOCK_WATER = 12;
    public static final int DH_BLOCK_GRASS = 13;
    public static final int DH_BLOCK_ILLUMINATED = 15;

    //The single line in the patched fragment that declares the vertex colour input.
    private static final String GL_COLOR_DECL = "in vec4 glColor;";

    //DH LoD is untextured — the pack shades a flat per-vertex colour (`vec4 color = vec4(glColor.rgb, 1.0)`).
    //Voxy's LoD is textured, so glColor is redefined as an atlas sample modulated by the vertex tint.
    //Alpha deliberately stays the vertex value: the pack reads glColor.a as vanilla AO (and as a centre
    //factor), not as opacity, so taking it from the texture would corrupt its shading.
    //This mirrors a pattern the pack already relies on — it initialises globals from varyings itself, e.g.
    //`float NdotU = dot(normal, upVec);` — so a non-const global initialiser here is nothing new to it.
    private static final String GL_COLOR_INJECTION = """
            in vec4 voxy_vertexTint;
            in vec2 voxy_atlasUV;
            uniform sampler2D voxy_atlas;
            vec4 glColor = vec4(texture(voxy_atlas, voxy_atlasUV).rgb * voxy_vertexTint.rgb, voxy_vertexTint.a);
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

    private static String injectAtlasSampling(String name, String fragment) {
        int occurrences = countOccurrences(fragment, GL_COLOR_DECL);
        if (occurrences != 1) {
            //Bail rather than guess: a pack whose fragment declares glColor differently would otherwise get a
            //silently half-rewritten shader.
            Logger.error("[voxy-dh] " + name + ": expected exactly one '" + GL_COLOR_DECL + "', found "
                    + occurrences + " — cannot inject texturing, skipping this pack's DH programs");
            return null;
        }
        return fragment.replace(GL_COLOR_DECL, GL_COLOR_INJECTION);
    }

    //Minimal vertex shader emitting exactly the interface the patched fragment consumes, with placeholder
    //values. Used to prove the contract links before the real geometry-producing vertex shader is written —
    //uniforms are deliberately left unbound, since unbound uniforms link fine and simply read zero, which
    //keeps this test isolated to the varying interface.
    private static final String LINK_TEST_VERTEX = """
            #version 330 core
            flat out int mat;
            out vec2 lmCoord;
            flat out vec3 upVec, sunVec, northVec, eastVec;
            out vec3 normal;
            out vec3 playerPos;
            out float iris_FogFragCoord;
            out vec4 voxy_vertexTint;
            out vec2 voxy_atlasUV;
            void main() {
                mat = 0;
                lmCoord = vec2(0.0);
                upVec = vec3(0.0, 1.0, 0.0);
                sunVec = vec3(0.0, 1.0, 0.0);
                northVec = vec3(0.0, 0.0, 1.0);
                eastVec = vec3(1.0, 0.0, 0.0);
                normal = vec3(0.0, 1.0, 0.0);
                playerPos = vec3(0.0);
                iris_FogFragCoord = 0.0;
                voxy_vertexTint = vec4(1.0);
                voxy_atlasUV = vec2(0.0);
                gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
            }
            """;

    /**
     * Compiles and links the prepared fragment programs against a stub vertex shader to prove the interface
     * holds, logging the outcome. Renders nothing and is safe to fail — voxy keeps using its bundled patch.
     */
    public void verifyLinkage(net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline) {
        tryLink("dh_terrain", this.terrainFragment, pipeline);
        if (this.waterFragment != null) {
            tryLink("dh_water", this.waterFragment, pipeline);
        }
    }

    private static void tryLink(String name, String fragment, net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline) {
        try {
            var builder = net.irisshaders.iris.gl.program.ProgramBuilder.begin(
                    "voxy_" + name, LINK_TEST_VERTEX, null, fragment, pipeline.getFlippedAfterPrepare());
            var program = builder.build();
            Logger.info("[voxy-dh] " + name + ": LINK OK");
            program.destroy();
        } catch (Throwable t) {
            Logger.error("[voxy-dh] " + name + ": LINK FAILED", t);
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
