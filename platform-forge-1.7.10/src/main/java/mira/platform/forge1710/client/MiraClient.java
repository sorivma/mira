package mira.platform.forge1710.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import mira.platform.forge1710.CommonProxy;
import mira.platform.forge1710.client.workspace.WorkspaceScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import mira.platform.forge1710.client.workspace.PinnedTaskHud;

/** The only lifecycle path that resolves Minecraft client classes. */
public final class MiraClient extends CommonProxy {
    private final KeyBinding open = new KeyBinding("key.mira.open", Keyboard.KEY_J, "key.categories.mira");
    private WorkspaceScreen workspace;

    @Override
    public void initialize() {
        ClientRegistry.registerKeyBinding(open);
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(new PinnedTaskHud());
        if (Loader.isModLoaded("NotEnoughItems")) NeiBridge.register();
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (open.isPressed() && minecraft.theWorld != null && minecraft.currentScreen == null) {
            if (workspace == null) workspace = new WorkspaceScreen();
            minecraft.displayGuiScreen(workspace);
            if (PinnedTaskHud.active()) workspace.openPinned();
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && PinnedTaskHud.active()) InventoryClient.update();
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        InventoryClient.reset(); PinnedTaskHud.clear(); workspace = null;
    }
}
