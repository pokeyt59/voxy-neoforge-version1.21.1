package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.concurrent.atomic.AtomicInteger;

// Voxy's other ingest paths are all client-side (sodium mesh hooks, ClientChunkCache.drop, etc.).
// In SP/integrated-server, that means chunks generated server-side beyond the player's render
// distance — Chunky pregen, force-loaded chunks, anything not in client view — never reach
// voxy's LoD storage. Hooking ServerLevel chunk loads catches them.
@EventBusSubscriber(modid = "voxy", bus = EventBusSubscriber.Bus.GAME)
public class VoxyServerChunkEvents {
    private static final AtomicInteger ingestCounter = new AtomicInteger(0);

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        if (!(event.getChunk() instanceof LevelChunk lc)) {
            return;
        }
        if (VoxyCommon.getInstance() == null) {
            return;
        }
        VoxelIngestService.tryAutoIngestChunk(lc);
        int n = ingestCounter.incrementAndGet();
        //Report the first one outright. This is the only ingest path that covers chunks generated outside the
        //client's view (Chunky pregen, force-loaded chunks), and with a 256-chunk reporting interval its
        //absence from a log was previously indistinguishable from the hook never firing at all.
        if (n == 1) {
            Logger.trace("[voxy-trace] server-side chunk ingest active (covers Chunky pregen / force-loaded chunks)");
        } else if ((n & 0xFF) == 0) {
            Logger.trace("[voxy-trace] server-side chunk-load ingests: " + n);
        }
    }
}
