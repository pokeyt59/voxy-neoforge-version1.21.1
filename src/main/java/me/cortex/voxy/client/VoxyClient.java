package me.cortex.voxy.client;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.model.bakery.BudgetBufferRenderer;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = "voxy", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class VoxyClient {

    /**
     * Called from MixinRenderSystem once OpenGL is ready — sets up GPU capabilities,
     * index buffers, and installs the instance factory.
     */
    public static void initVoxyClient() {
        Capabilities.init();

        boolean systemSupported = Capabilities.INSTANCE.compute && Capabilities.INSTANCE.indirectParameters;
        if (systemSupported) {
            SharedIndexBuffer.INSTANCE.id();
            BudgetBufferRenderer.init();
            VoxyCommon.setInstanceFactory(VoxyClientInstance::new);

            if (!Capabilities.INSTANCE.subgroup) {
                Logger.warn("GPU does not support subgroup operations, expect some performance degradation");
            }
        } else {
            Logger.error("Voxy is unsupported on your system.");
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Register client-side commands on the GAME bus after setup completes.
        NeoForge.EVENT_BUS.addListener(VoxyClient::onRegisterClientCommands);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (VoxyCommon.isAvailable()) {
            event.getDispatcher().register(VoxyCommands.register());
        }
    }

    // FREX (Fabric Rendering EXtensions) integration removed — Fabric-only API.
    public static boolean isFrexActive() {
        return false;
    }
}
