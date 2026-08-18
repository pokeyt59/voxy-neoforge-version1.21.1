package me.cortex.voxy.client.iris;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.voxy.common.Logger;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classifies every block state into a Distant Horizons material id, so the shaderpack's DH programs take their
 * per-material branches for voxy's LoD.
 *
 * <p>The pack reaches these through {@code mat}, which voxy feeds from {@code BlockModel.customId} — the model
 * bakery already stores a per-state int there and defaults it to 0, which conveniently is DH's "unknown".
 * Repointing that mapping is therefore enough; no GPU struct changes are needed.
 *
 * <p>Only the handful of ids a pack actually branches on need to be right. Complementary Reimagined tests
 * exactly five — leaves, lava, water, grass and illuminated — and everything else falls through to 0 and is
 * shaded as ordinary terrain, which is the correct outcome rather than a gap.
 */
public class DhMaterialMap {
    private DhMaterialMap() {}

    /**
     * @return every state that maps to a non-zero DH material. States are omitted rather than mapped to 0,
     *         since the bakery already writes 0 for anything missing.
     */
    public static Object2IntMap<BlockState> build() {
        var map = new Object2IntOpenHashMap<BlockState>();
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            int material = classify(state);
            if (material != 0) {
                map.put(state, material);
            }
        }
        Logger.info("[voxy-dh] classified " + map.size() + " block states into DH materials");
        return map;
    }

    private static int classify(BlockState state) {
        //Fluids first, and keyed off the block rather than getFluidState(): a waterlogged stair reports a
        //water fluid state but is modelled and shaded as a stair, so keying off the fluid would misclassify
        //every waterlogged block in the world. Lava also has to precede the emission test, since it glows.
        if (state.getBlock() instanceof LiquidBlock) {
            return state.getFluidState().is(FluidTags.LAVA)
                    ? DhProgramSources.DH_BLOCK_LAVA
                    : DhProgramSources.DH_BLOCK_WATER;
        }

        if (state.getLightEmission() > 0) {
            return DhProgramSources.DH_BLOCK_ILLUMINATED;
        }

        if (state.is(BlockTags.LEAVES)) {
            return DhProgramSources.DH_BLOCK_LEAVES;
        }

        //Sound type stands in for the material family, which is close to how DH classifies. It has to come
        //after the leaves check, since leaves also sound like grass.
        if (state.getSoundType() == SoundType.GRASS) {
            return DhProgramSources.DH_BLOCK_GRASS;
        }

        return 0;
    }
}
