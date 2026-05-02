package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.Serialization;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("voxy")
public class VoxyCommon {
    public static String MOD_VERSION;
    public static boolean IS_DEDICATED_SERVER;
    public static boolean IS_IN_MINECRAFT;

    static {
        // Defaults; real values populated by the mod constructor once FML finishes loading.
        IS_IN_MINECRAFT = false;
        IS_DEDICATED_SERVER = false;
        MOD_VERSION = "<UNKNOWN>";
    }

    public VoxyCommon(IEventBus modBus) {
        var modOpt = ModList.get().getModContainerById("voxy");
        if (modOpt.isEmpty()) {
            Logger.error("Running voxy without minecraft");
        } else {
            IS_IN_MINECRAFT = true;
            var mod = modOpt.get();
            var version = mod.getModInfo().getVersion().toString();
            var commit  = mod.getModInfo().getModProperties()
                             .getOrDefault("commit", "<unknown>").toString();
            MOD_VERSION = version + "-" + commit;
            IS_DEDICATED_SERVER = FMLEnvironment.dist == Dist.DEDICATED_SERVER;
            Serialization.init();
        }
    }

    public static boolean isVerificationFlagOn(String name) {
        return isVerificationFlagOn(name, false);
    }

    public static boolean isVerificationFlagOn(String name, boolean defaultOn) {
        return System.getProperty("voxy." + name, defaultOn ? "true" : "false").equals("true");
    }

    public static void breakpoint() {
        int breakpoint = 0;
    }

    public interface IInstanceFactory { VoxyInstance create(); }
    private static VoxyInstance INSTANCE;
    private static IInstanceFactory FACTORY = null;

    public static void setInstanceFactory(IInstanceFactory factory) {
        if (FACTORY != null) {
            throw new IllegalStateException("Cannot set instance factory more than once");
        }
        FACTORY = factory;
    }

    public static VoxyInstance getInstance() {
        return INSTANCE;
    }

    public static void shutdownInstance() {
        if (INSTANCE != null) {
            var instance = INSTANCE;
            INSTANCE = null;
            instance.shutdown();
        }
    }

    public static void createInstance() {
        if (FACTORY == null) {
            return;
        }
        if (INSTANCE != null) {
            throw new IllegalStateException("Cannot create multiple instances");
        }
        INSTANCE = FACTORY.create();
    }

    public static boolean isAvailable() {
        return FACTORY != null;
    }

    public static final boolean IS_MINE_IN_ABYSS = false;
}
