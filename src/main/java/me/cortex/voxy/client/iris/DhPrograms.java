package me.cortex.voxy.client.iris;

import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;

/**
 * The shaderpack's own Distant Horizons programs, compiled against voxy's LoD vertex shader and ready to draw
 * with. Owning these is what lets voxy's LoD be lit by the pack itself rather than by voxy's approximation.
 *
 * <p>Bound in place of voxy's own terrain shader in {@code MDICSectionRenderer}; everything else about the
 * draw — vertex array, buffer bindings, the indirect call — is unchanged, since the geometry is identical and
 * only the shading differs.
 */
public class DhPrograms {
    private final Program terrain;
    private final Program water;
    private final CustomUniforms customUniforms;

    DhPrograms(Program terrain, Program water, CustomUniforms customUniforms) {
        this.terrain = terrain;
        this.water = water;
        this.customUniforms = customUniforms;
    }

    /** @return true when opaque LoD can be drawn with the pack's program */
    public boolean hasTerrain() {
        return this.terrain != null;
    }

    /** @return true when translucent LoD has a dedicated program; otherwise the terrain one is reused */
    public boolean hasWater() {
        return this.water != null;
    }

    public void useTerrain() {
        use(this.terrain);
    }

    /** Falls back to the terrain program when the pack ships no dh_water. */
    public void useWater() {
        use(this.water != null ? this.water : this.terrain);
    }

    private void use(Program program) {
        program.use();
        //Custom uniforms are per-pass state and have to be pushed after the program is bound, the same way
        //iris drives its own programs; without this the pack's custom uniforms hold whatever the last pass left.
        this.customUniforms.push(program);
    }

    public void destroy() {
        try {
            if (this.terrain != null) this.terrain.destroy();
            if (this.water != null) this.water.destroy();
        } catch (Throwable t) {
            Logger.error("[voxy-dh] failed to destroy DH programs", t);
        }
    }
}
