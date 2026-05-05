package me.cortex.voxy.common.world.other;

import static me.cortex.voxy.common.world.other.Mapper.withLight;

//Mipper for data
public class Mipper {
    //TODO: compute the opacity of the block then mip w.r.t those blocks
    // as distant horizons done


    //TODO: also pass in the level its mipping from, cause at lower levels you want to preserve block details
    // but at higher details you want more air
    public static long mip(long I000, long I100, long I001, long I101,
                           long I010, long I110, long I011, long I111,
                          Mapper mapper) {
        //TODO: do a stable sort on all the entires, w.r.t the opacity and maybe light as a secondary???
        // then select the highest value
        // UPDATE, dumbass, the highest value _is_ the max/min



        int max = -1;

        //TODO: mip with respect to all the variables, what that means is take whatever has the highest count and return that
        //TODO: also average out the light level and set that as the new light level
        //For now just take the most top corner

        //TODO: i think it needs to compute the _max_ light level, since e.g. if a point is bright irl
        // you can see it from really really damn far away.
        // it could be a heavily weighted average with a huge preference to the top most lighting value

        //Selection key layout (12 bits): [opacity:8..11][skyLight:4..7][corner:0..2].
        //Higher opacity always wins; ties on opacity break by sky-light (the corner most
        //exposed to the sky); final tie-break is corner index (preserves the old I111 bias).
        //Without the sky-light tier, deep-underground 2x2x2 groups (all stone, all sky=0)
        //inherited sky=0 lighting via I111 and rendered pitch-black at LoD — visible as
        //large black blobs anywhere underground stone got exposed at the LoD horizon.
        if (!Mapper.isAir(I111)) {
            max = (mapper.getBlockStateOpacity(I111)<<8)|((Mapper.getLightId(I111)&0x0F)<<4)|0b111;
        }
        if (!Mapper.isAir(I110)) {
            max = Math.max((mapper.getBlockStateOpacity(I110)<<8)|((Mapper.getLightId(I110)&0x0F)<<4)|0b110, max);
        }
        if (!Mapper.isAir(I011)) {
            max = Math.max((mapper.getBlockStateOpacity(I011)<<8)|((Mapper.getLightId(I011)&0x0F)<<4)|0b011, max);
        }
        if (!Mapper.isAir(I010)) {
            max = Math.max((mapper.getBlockStateOpacity(I010)<<8)|((Mapper.getLightId(I010)&0x0F)<<4)|0b010, max);
        }
        if (!Mapper.isAir(I101)) {
            max = Math.max((mapper.getBlockStateOpacity(I101)<<8)|((Mapper.getLightId(I101)&0x0F)<<4)|0b101, max);
        }
        if (!Mapper.isAir(I100)) {
            max = Math.max((mapper.getBlockStateOpacity(I100)<<8)|((Mapper.getLightId(I100)&0x0F)<<4)|0b100, max);
        }
        if (!Mapper.isAir(I001)) {
            max = Math.max((mapper.getBlockStateOpacity(I001)<<8)|((Mapper.getLightId(I001)&0x0F)<<4)|0b001, max);
        }
        if (!Mapper.isAir(I000)) {
            max = Math.max((mapper.getBlockStateOpacity(I000)<<8)|((Mapper.getLightId(I000)&0x0F)<<4), max);
        }

        if (max != -1) {
            long picked = switch (max&0b111) {
                case 0 -> I000;
                case 1 -> I001;
                case 2 -> I010;
                case 3 -> I011;
                case 4 -> I100;
                case 5 -> I101;
                case 6 -> I110;
                case 7 -> I111;
                default -> throw new IllegalStateException("Unexpected value: " + (max&0b111));
            };
            //Visibility override: force sky-light to 15 on the picked corner. At LoD
            //distance, accuracy of per-voxel shading matters less than terrain being
            //visible at all. Without this, solid voxels whose every corner has sky=0
            //(forest floor under dense canopy, exposed underground stone at cliff
            //bases, dune walls in deep deserts) inherit sky=0 and render pitch-black
            //via the lightmap, making LoD look like it's full of voids. Forcing sky=15
            //matches the VoxelIngestService ring-chunk default and the cave-fill below.
            //Block-light is preserved so torches/glowstone still tint the voxel correctly.
            int packedLight = (Mapper.getLightId(picked) & 0xF0) | 0x0F;
            return withLight(picked, packedLight);
        } else {
            int blockLight = (Mapper.getLightId(I000) & 0xF0) + (Mapper.getLightId(I001) & 0xF0) + (Mapper.getLightId(I010) & 0xF0) + (Mapper.getLightId(I011) & 0xF0) +
                    (Mapper.getLightId(I100) & 0xF0) + (Mapper.getLightId(I101) & 0xF0) + (Mapper.getLightId(I110) & 0xF0) + (Mapper.getLightId(I111) & 0xF0);
            int skyLight = (Mapper.getLightId(I000) & 0x0F) + (Mapper.getLightId(I001) & 0x0F) + (Mapper.getLightId(I010) & 0x0F) + (Mapper.getLightId(I011) & 0x0F) +
                    (Mapper.getLightId(I100) & 0x0F) + (Mapper.getLightId(I101) & 0x0F) + (Mapper.getLightId(I110) & 0x0F) + (Mapper.getLightId(I111) & 0x0F);
            blockLight = blockLight / 8;
            skyLight = (int) Math.ceil((double) skyLight / 8);

            //Cave-interior fill: when every corner has zero sky-light, this 2x2x2 group sits
            //entirely below an unbroken ceiling (no light path to sky). Returning air here
            //makes large sealed caves/ravines bleed through the LoD surface as small black
            //voids ("x-ray effect"). Substituting a synthetic stone block keeps the LoD
            //opaque from the outside; voxels with any sky-light remain air so cave entrances,
            //ravines open to sky, and the sky itself still render correctly.
            //
            //Light value: pack sky=15 so the synthetic stone renders at full daylight via the
            //time-of-day-modulated lightmap — same convention the ring-chunk default uses in
            //VoxelIngestService.getLightingSupplier. Inheriting the original sky=0 cave
            //lighting would render the filler as pitch-black voids, just trading "x-ray hole"
            //for "black blob". The cave is invisible from outside anyway, so daylight-bright
            //stone matches the surrounding LoD surface and disappears.
            int maxSky = Math.max(Math.max(Math.max(Mapper.getLightId(I000) & 0x0F, Mapper.getLightId(I001) & 0x0F),
                                           Math.max(Mapper.getLightId(I010) & 0x0F, Mapper.getLightId(I011) & 0x0F)),
                                  Math.max(Math.max(Mapper.getLightId(I100) & 0x0F, Mapper.getLightId(I101) & 0x0F),
                                           Math.max(Mapper.getLightId(I110) & 0x0F, Mapper.getLightId(I111) & 0x0F)));
            if (maxSky == 0) {
                return Mapper.composeMappingId((byte) 0x0F, mapper.getCaveFillerId(), 0);
            }

            return withLight(I111, (blockLight << 4) | skyLight);
        }
    }
}
