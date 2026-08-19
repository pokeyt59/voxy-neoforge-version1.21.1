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
    //Vanilla's default Mipmap Levels, used only for the window between program build and ModelStore
    //creation. ModelStore overwrites it with the real value as soon as it publishes the atlas.
    private static volatile int atlasMaxLod = 4;

    private VoxyDhBindings() {}

    public static void setAtlas(int textureId) {
        me.cortex.voxy.common.Logger.trace("[voxy-dh] atlas published: " + textureId);
        atlas = textureId;
    }

    /**
     * Clears only if this is still the published atlas. Voxy can build a replacement ModelStore before
     * freeing the old one, and an unconditional clear would then zero out the live atlas and leave the DH
     * programs sampling nothing.
     */
    public static void clearAtlas(int textureId) {
        if (atlas == textureId) {
            me.cortex.voxy.common.Logger.trace("[voxy-dh] atlas cleared: " + textureId);
            atlas = 0;
        } else {
            me.cortex.voxy.common.Logger.trace("[voxy-dh] stale atlas " + textureId + " freed, keeping live " + atlas);
        }
    }

    /**
     * The mip level voxy actually populates its atlas up to, matching the clamp ModelStore puts on its own
     * sampler. Iris samples through a sampler allocation that never receives that clamp, so the shader has to
     * reapply the bound itself -- and it cannot be baked into the source, because the programs are built
     * before Minecraft's texture manager exists, let alone the block atlas.
     */
    public static void setAtlasMaxLod(int maxLod) {
        atlasMaxLod = maxLod;
    }

    public static int currentAtlasMaxLod() {
        return atlasMaxLod;
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
