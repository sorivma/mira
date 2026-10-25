package mira.platform.forge1710.client;

import mira.core.taskbody.InventorySnapshot;
import mira.platform.forge1710.inventory.CarriedInventory;
import mira.platform.forge1710.inventory.InventoryNetwork;
import net.minecraft.client.Minecraft;

public final class InventoryClient {
    private static long requested;
    private static InventorySnapshot local = new InventorySnapshot();
    private InventoryClient() {}
    public static void update() {
        Minecraft minecraft = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        if (minecraft.thePlayer == null || now - requested < 500) return;
        requested = now;
        local = CarriedInventory.collect(minecraft.thePlayer);
        InventoryNetwork.channel.sendToServer(new InventoryNetwork.Request());
    }
    public static InventorySnapshot snapshot() {
        InventorySnapshot received = InventoryNetwork.clientSnapshot;
        return received != null && System.currentTimeMillis() - InventoryNetwork.receivedAt < 2000 ? received : local;
    }
    public static void reset() { InventoryNetwork.clientSnapshot = null; requested = 0; local = new InventorySnapshot(); }
}
