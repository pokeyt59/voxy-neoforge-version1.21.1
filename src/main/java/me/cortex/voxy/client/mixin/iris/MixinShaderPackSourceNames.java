package me.cortex.voxy.client.mixin.iris;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.shaderpack.include.ShaderPackSourceNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ShaderPackSourceNames.class, remap = false)
public class MixinShaderPackSourceNames {
    @WrapOperation(method = "findPotentialStarts", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;builder()Lcom/google/common/collect/ImmutableList$Builder;"))
    private static ImmutableList.Builder<String> voxy$injectVoxyShaderPatch(Operation<ImmutableList.Builder<String>> original){
        var builder = original.call();
        builder.add("voxy.json");
        builder.add("voxy_opaque.glsl");
        builder.add("voxy_translucent.glsl");
        //IrisShaderPatch.makePatch also reads voxy_taa.glsl; without it listed here a pack shipping one would
        //never have it include-processed, so the lookup always came back null.
        builder.add("voxy_taa.glsl");
        return builder;
    }
}
