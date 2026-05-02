package me.cortex.voxy.client.core.rendering.util;

import net.minecraft.client.Minecraft;

import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class LightMapHelper {
    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        glBindTextureUnit(lightingIndex, Minecraft.getInstance().gameRenderer.lightTexture().lightTexture.getId());
    }
}