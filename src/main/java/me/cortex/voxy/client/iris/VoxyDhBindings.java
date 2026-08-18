package me.cortex.voxy.client.iris;

/**
 * Publishes the voxy-owned textures that the shaderpack's DH programs sample, for lazy per-bind resolution.
 *
 * <p>Those programs are built while iris constructs its rendering pipeline, which happens before voxy's model
 * bakery exists and before any viewport has been set up, so neither texture id can be handed over up front.
 * The samplers are registered with suppliers that read from here instead.
 *
 * <ul>
 *   <li>{@code atlas} — voxy's block-model atlas, owned by ModelStore for its lifetime. The pack's DH
 *       programs shade a flat vertex colour because DH LoD is untextured; voxy's is textured, so the atlas is
 *       fed in through the injected glColor.</li>
 *   <li>{@code depthBound} — the current viewport's depth-bound buffer, republished each frame. The pack's
 *       program has no equivalent of voxy's depth-bound discard, so without this LoD draws over vanilla
 *       terrain.</li>
 * </ul>
 *
 * <p>Zero is the "not ready" value and is what GL treats as an unbound texture, so a program that samples
 * before these exist reads black rather than touching a stale or freed texture.
 */
public final class VoxyDhBindings {
    private static volatile int atlas = 0;
    private static volatile int depthBound = 0;

    private VoxyDhBindings() {}

    public static void setAtlas(int textureId) {
        me.cortex.voxy.common.Logger.info("[voxy-dh] atlas published: " + textureId);
        atlas = textureId;
    }

    /**
     * Clears only if this is still the published atlas. Voxy can build a replacement ModelStore before
     * freeing the old one, and an unconditional clear would then zero out the live atlas and leave the DH
     * programs sampling nothing.
     */
    public static void clearAtlas(int textureId) {
        if (atlas == textureId) {
            me.cortex.voxy.common.Logger.info("[voxy-dh] atlas cleared: " + textureId);
            atlas = 0;
        } else {
            me.cortex.voxy.common.Logger.info("[voxy-dh] stale atlas " + textureId + " freed, keeping live " + atlas);
        }
    }

    public static int currentAtlas() {
        return atlas;
    }

    public static void setDepthBound(int textureId) {
        depthBound = textureId;
    }

    public static int currentDepthBound() {
        return depthBound;
    }
}
