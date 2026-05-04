package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    // TAIL inject — vanilla setupFog calls RenderSystem.setShaderFogStart/End/Color internally,
    // so we have to override AFTER it returns or our values get clobbered. Color is left to vanilla;
    // we only push the fog distance out to infinity so voxy's LoD doesn't get faded to sky color.
    @Inject(
        method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
        at = @At("TAIL")
    )
    private static void voxy$overrideFog(
        Camera camera,
        FogRenderer.FogMode fogMode,
        float viewDistance,
        boolean thickFog,
        float tickDelta,
        CallbackInfo ci
    ) {
        if (VoxyConfig.CONFIG.renderVanillaFog) return;
        var vrs = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
        if (vrs == null || vrs.getVoxyRenderSystem() == null) return;

        RenderSystem.setShaderFogStart(999999999);
        RenderSystem.setShaderFogEnd(999999999);
    }
}
