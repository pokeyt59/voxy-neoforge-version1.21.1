package me.cortex.voxy.client.iris;

/**
 * Publishes voxy's block-model atlas texture so the shaderpack's DH programs can sample it.
 *
 * <p>Those programs are built while iris constructs its rendering pipeline, which happens before voxy's model
 * bakery exists, so the sampler cannot be handed a texture id up front — it is resolved lazily each time the
 * program binds. {@link ModelStore} owns the atlas and publishes it here for its lifetime.
 *
 * <p>Zero is the "no atlas" value and is what GL treats as an unbound texture, so a program that samples it
 * before the bakery is ready reads black rather than sampling a stale or freed texture.
 */
public final class VoxyAtlasBinding {
    private static volatile int atlasTexture = 0;

    private VoxyAtlasBinding() {}

    public static void set(int textureId) {
        atlasTexture = textureId;
    }

    public static void clear() {
        atlasTexture = 0;
    }

    public static int currentAtlasTexture() {
        return atlasTexture;
    }
}
