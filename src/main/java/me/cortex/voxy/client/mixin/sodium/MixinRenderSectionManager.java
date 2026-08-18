package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.ICheekyClientChunkManager;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.neoforged.fml.ModList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {
    @Unique
    private static final boolean BOBBY_INSTALLED = ModList.get().isLoaded("bobby");

    @Shadow @Final private ClientLevel level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$resetChunkTracker(ClientLevel level, int renderDistance, CommandList commandList, CallbackInfo ci) {
        if (level.levelRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(level.levelRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.reset();
            }
        }
        this.bottomSectionY = this.level.getMinSection();
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void injectIngest(int x, int z, CallbackInfo ci) {
        //TODO: Am not quite sure if this is right
        if (VoxyConfig.CONFIG.ingestEnabled && !BOBBY_INSTALLED) {
            var cccm = (ICheekyClientChunkManager)this.level.getChunkSource();
            if (cccm != null) {
                var chunk = cccm.voxy$cheekyGetChunk(x, z);
                if (chunk != null) {
                    VoxelIngestService.tryAutoIngestChunk(chunk);
                }
            }
        }
    }

    /*
    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void voxy$trackChunkAdd(int x, int z, CallbackInfo ci) {
        if (this.level.levelRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(this.level.levelRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.addChunk(ChunkPos.toLong(x, z));
            }
        }
    }*/

    /*
    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$trackChunkRemove(int x, int z, CallbackInfo ci) {
        if (this.level.levelRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(this.level.levelRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.removeSection(ChunkPos.toLong(x, z));
            }
        }
    }*/

    @Unique private long cachedChunkPos = -1;
    @Unique private int cachedChunkStatus;
    @Unique private int bottomSectionY;

    @Redirect(method = "updateSectionInfo", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z"))
    private boolean voxy$updateOnUpload(RenderSection instance, BuiltSectionInfo info) {
        boolean wasBuilt = instance.getFlags()!=0;
        int flags = instance.getFlags();
        if (!instance.setInfo(info)) {
            return false;
        }
        if (wasBuilt == (instance.getFlags()!=0)) {//Only want to do stuff on change
            return true;
        }

        flags |= instance.getFlags();
        if (flags == 0)//Only process things with stuff
            return true;

        VoxyRenderSystem system = ((IGetVoxyRenderSystem)(this.level.levelRenderer)).getVoxyRenderSystem();
        if (system == null) {
            return true;
        }
        int x = instance.getChunkX(), y = instance.getChunkY(), z = instance.getChunkZ();

        if (wasBuilt) {
            var tracker = ((AccessorChunkTracker)ChunkTrackerHolder.get(this.level)).getChunkStatus();
            //in theory the cache value could be wrong but is so soso unlikely and at worst means we either duplicate ingest a chunk
            // which... could be bad ;-; or we dont ingest atall which is ok!
            long key = ChunkPos.asLong(x, z);
            if (key != this.cachedChunkPos) {
                this.cachedChunkPos = key;
                this.cachedChunkStatus = tracker.getOrDefault(key, 0);
            }
            // Was: == 3 (FLAG_HAS_BLOCK_DATA | FLAG_HAS_LIGHT_DATA). Edge chunks at the render
            // distance ring often have block data but never reach FLAG_HAS_LIGHT_DATA because light
            // propagation requires the outer neighbor, which never loads — so they got meshed but
            // never ingested, leaving black voids in the LoD ring. Accept any non-zero status; the
            // null light copy is already handled below.
            if (this.cachedChunkStatus == 0) {
                Logger.trace("[voxy-trace] gate skip status=0 sec=("+x+","+y+","+z+")");
            } else if (this.cachedChunkStatus != 3) {
                Logger.trace("[voxy-trace] gate loose-accept status="+this.cachedChunkStatus+" sec=("+x+","+y+","+z+")");
            }
            if (this.cachedChunkStatus != 0) {
                var section = this.level.getChunk(x,z).getSection(y-this.bottomSectionY);
                var lp = this.level.getLightEngine();

                var csp = SectionPos.of(x,y,z);
                var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);

                //Note: we dont do this check and just blindly ingest, it shouldbe ok :tm:
                //if (blp != null || slp != null)
                    VoxelIngestService.rawIngest(system.getEngine(), section, x,y,z, blp==null?null:blp.copy(), slp==null?null:slp.copy());
            }
        }

        //Do some very cheeky stuff for MiB
        if (VoxyCommon.IS_MINE_IN_ABYSS) {
            int sector = (x+512)>>10;
            x-=sector<<10;
            y+=16+(256-32-sector*30);
        }
        long pos = SectionPos.asLong(x,y,z);
        if (wasBuilt) {//Remove
            //TODO: on chunk remove do ingest if is surrounded by built chunks (or when the tracker says is ok)

            // Defer the depth-bound release until VoxelIngestService finishes the async
            // ingest for this section. Otherwise we drop the mask while LoD has no data,
            // producing the void/floating-island ring at the vanilla→LoD frontier.
            var ingestSvc = (system.getEngine().instanceIn != null)
                    ? system.getEngine().instanceIn.getIngestService()
                    : null;
            Logger.trace("[voxy-trace] depth-bound REMOVE sec=("+x+","+y+","+z+") (wasStatus="+this.cachedChunkStatus+", deferring="+(ingestSvc!=null && ingestSvc.isIngestPending(pos))+")");
            system.chunkBoundRenderer.deferRemoveSection(pos, ingestSvc);
        } else {//Add
            Logger.trace("[voxy-trace] depth-bound ADD sec=("+x+","+y+","+z+")");
            system.chunkBoundRenderer.addSection(pos);
        }
        return true;
    }
}
