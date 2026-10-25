package mira.integration.agenttools.forge;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** No client classes or network listener on a dedicated server. */
public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {}
    public void start() throws java.io.IOException {}
}
