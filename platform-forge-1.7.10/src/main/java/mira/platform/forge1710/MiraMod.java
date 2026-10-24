package mira.platform.forge1710;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** Common bootstrap; keep client-only types out of this loading path. */
@Mod(modid = MiraMod.MOD_ID, name = "Mira", version = "0.1.0", acceptedMinecraftVersions = "[1.7.10]")
public final class MiraMod {
    public static final String MOD_ID = "mira";

    @Mod.EventHandler
    public void preInitialize(FMLPreInitializationEvent event) {
        event.getModLog().info("Mira initialized");
    }
}
