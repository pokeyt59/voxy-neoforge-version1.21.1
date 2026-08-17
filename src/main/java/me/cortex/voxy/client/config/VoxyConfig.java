package me.cortex.voxy.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gui.options.storage.OptionStorage;
import net.neoforged.fml.loading.FMLPaths;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public class VoxyConfig implements OptionStorage<VoxyConfig> {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static VoxyConfig CONFIG = loadOrCreate();

    public boolean enabled = true;
    public boolean enableRendering = true;
    public boolean ingestEnabled = true;
    public int sectionRenderDistance = 16;
    public int serviceThreads = (int) Math.max(CpuLayout.CORES.length/1.5, 1);
    public float subDivisionSize = 64;
    public boolean renderVanillaFog = false;
    public boolean renderStatistics = false;
    //When a shaderpack does not ship its own voxy.json patch, fall back to the patch bundled in
    //voxy's assets so LoD still renders through the iris pipeline instead of silently dropping to
    //the non-shader pipeline. Set false to require pack-provided support.
    public boolean useBundledShaderPatch = true;
    //Present voxy's LoD to shaderpacks as Distant Horizons LoD (DISTANT_HORIZONS macro, dhDepthTex*,
    //dhProjection/dhFarPlane/...). Packs have no notion of voxy, but nearly all of them already handle DH,
    //and that is the only route by which a pack's composite passes can resolve geometry past the vanilla far
    //plane instead of fogging it flat. See IrisShaderPatch.IMPERSONATE_DISTANT_HORIZONS.
    public boolean impersonateDistantHorizons = true;

    private static VoxyConfig loadOrCreate() {
        if (VoxyCommon.isAvailable()) {
            var path = getConfigPath();
            if (Files.exists(path)) {
                try (FileReader reader = new FileReader(path.toFile())) {
                    var conf = GSON.fromJson(reader, VoxyConfig.class);
                    if (conf != null) {
                        conf.save();
                        return conf;
                    } else {
                        Logger.error("Failed to load voxy config, resetting");
                    }
                } catch (IOException e) {
                    Logger.error("Could not parse config", e);
                }
            }
            var config = new VoxyConfig();
            config.save();
            return config;
        } else {
            var config = new VoxyConfig();
            config.enabled = false;
            config.enableRendering = false;
            return config;
        }
    }

    public void save() {
        try {
            Files.writeString(getConfigPath(), GSON.toJson(this));
        } catch (IOException e) {
            Logger.error("Failed to write config file", e);
        }
    }

    private static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("voxy-config.json");
    }

    @Override
    public VoxyConfig getData() {
        return this;
    }

    public boolean isRenderingEnabled() {
        return VoxyCommon.isAvailable() && this.enabled && this.enableRendering;
    }
}
