package me.cortex.voxy.client.iris;

import me.cortex.voxy.common.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supplies the voxy iris patch files ({@code voxy.json}, {@code voxy_opaque.glsl}, ...) from voxy's own
 * assets for shaderpacks that do not ship them.
 *
 * <p>Voxy's iris integration is opt-in from the shaderpack side: {@link IrisShaderPatch#makePatch} looks for a
 * {@code voxy.json} inside the pack, and without one voxy falls all the way back to the non-shader pipeline,
 * rendering LoD outside of the pack's knowledge. In practice no public shaderpack ships those files, so that
 * fallback was the only path anyone ever hit. The bundled patch keeps LoD inside the iris pipeline instead.
 *
 * <p>The bundled sources are deliberately <b>self-contained</b> — they must not {@code #include} anything from
 * the shaderpack. Pack-shipped patch files are run through iris's include resolver (which is why they are
 * registered in {@code MixinShaderPackSourceNames}), but these are read straight off voxy's classpath and are
 * appended verbatim into voxy's own {@code #version 460 core} fragment shader, so pack includes would neither
 * resolve nor compile.
 *
 * <p>A pack that <i>does</i> ship its own files always wins; this is only consulted when the pack provides
 * nothing. Per-pack profiles can be added later by resolving a directory other than {@code default}.
 */
public class BundledShaderPatch {
    private static final String ROOT = "/assets/voxy/iris_patches/";
    private static final String DEFAULT_PROFILE = "default";

    //Sentinel so a missing (optional) file is cached as "absent" rather than re-probed on every pipeline build
    private static final String ABSENT = new String("\0absent");

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private BundledShaderPatch() {}

    /**
     * @return the bundled source for {@code fileName}, or null when voxy ships no such file
     */
    public static String get(String fileName) {
        String cached = CACHE.computeIfAbsent(fileName, BundledShaderPatch::read);
        //noinspection StringEquality — ABSENT is an identity sentinel
        return cached == ABSENT ? null : cached;
    }

    private static String read(String fileName) {
        String resource = ROOT + DEFAULT_PROFILE + "/" + fileName;
        //Load through voxy's own class: under NeoForge's module classloading another mod's classloader
        //cannot see voxy's assets, and this is reached from inside iris code.
        try (InputStream is = BundledShaderPatch.class.getResourceAsStream(resource)) {
            if (is == null) {
                return ABSENT;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logger.error("Failed to read bundled iris patch " + resource, e);
            return ABSENT;
        }
    }
}
