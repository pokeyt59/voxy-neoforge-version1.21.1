package me.cortex.voxy.client.iris;

/** Implemented by ProgramSet so the prepared pack DH programs can be retrieved when the pipeline is built. */
public interface IGetDhProgramSources {
    DhProgramSources voxy$getDhProgramSources();
}
