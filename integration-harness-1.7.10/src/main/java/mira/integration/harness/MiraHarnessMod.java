package mira.integration.harness;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;

@Mod(modid = "miraharness", name = "Mira Harness Tasks", version = "0.1.0",
    acceptableRemoteVersions = "*", dependencies = "required-after:mira;required-after:minecraftharness")
public final class MiraHarnessMod {
    @SidedProxy(clientSide = "mira.integration.harness.client.ClientProxy", serverSide = "mira.integration.harness.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void init(FMLInitializationEvent event) { proxy.initialize(); }
}
