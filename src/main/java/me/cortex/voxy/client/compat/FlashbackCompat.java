package me.cortex.voxy.client.compat;

import java.nio.file.Path;

// Flashback is Fabric-only — all calls become no-ops on NeoForge.
public class FlashbackCompat {
    public static final boolean FLASHBACK_INSTALLED = false;

    public static Path getReplayStoragePath() {
        return null;
    }
}
