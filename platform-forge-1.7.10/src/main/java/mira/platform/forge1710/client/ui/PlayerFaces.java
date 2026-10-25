package mira.platform.forge1710.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

/** Reuses skins already known to the game; never performs profile/network lookups while drawing. */
final class PlayerFaces {
    private PlayerFaces() {}

    static boolean neutral(String name) { return "Unassigned".equals(name) || "Everyone".equals(name); }

    static ResourceLocation skin(Minecraft minecraft, String name) {
        if (minecraft.thePlayer != null && ("You".equals(name) || minecraft.thePlayer.getCommandSenderName().equalsIgnoreCase(name)))
            return minecraft.thePlayer.getLocationSkin();
        if (minecraft.theWorld != null) for (Object value : minecraft.theWorld.playerEntities) {
            if (value instanceof AbstractClientPlayer) {
                AbstractClientPlayer player = (AbstractClientPlayer) value;
                if (player.getCommandSenderName().equalsIgnoreCase(name)) return player.getLocationSkin();
            }
        }
        return AbstractClientPlayer.locationStevePng;
    }
}
