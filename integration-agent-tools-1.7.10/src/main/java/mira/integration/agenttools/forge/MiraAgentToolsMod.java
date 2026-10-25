package mira.integration.agenttools.forge;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import mira.platform.forge1710.BuildVersion;

@Mod(modid = "minecraftagenttoolsmira", name = "Mira Agent Tools", version = BuildVersion.VERSION,
    acceptableRemoteVersions = "*", dependencies = "required-after:mira;before:minecraftagenttoolsmcp")
public final class MiraAgentToolsMod {
    @SidedProxy(clientSide = "mira.integration.agenttools.forge.client.ClientProxy",
        serverSide = "mira.integration.agenttools.forge.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) { proxy.preInit(event); }
    @Mod.EventHandler public void postInit(FMLPostInitializationEvent event) throws java.io.IOException { proxy.start(); }
}
