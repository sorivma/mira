package mira.integration.harness;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import mira.platform.forge1710.BuildVersion;

@Mod(modid = "miraharness", name = "Mira Harness Tasks", version = BuildVersion.VERSION,
    acceptableRemoteVersions = "*", dependencies = "required-after:mira;required-after:minecraftharness")
public final class MiraHarnessMod {
    @SidedProxy(clientSide = "mira.integration.harness.client.ClientProxy", serverSide = "mira.integration.harness.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void init(FMLInitializationEvent event) { proxy.initialize(); }
}
