package mira.platform.forge1710.client.workspace;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import mira.platform.forge1710.client.InventoryClient;
import mira.platform.forge1710.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import static mira.platform.forge1710.client.ui.Ui.*;

public final class PinnedTaskHud {
    private static DemoWorkspace.Issue pinned;
    private Ui ui;
    static void toggle(DemoWorkspace.Issue issue) { pinned = pinned == issue ? null : issue; }
    static boolean isPinned(DemoWorkspace.Issue issue) { return pinned == issue; }
    static DemoWorkspace.Issue issue() { return pinned; }
    public static boolean active() { return pinned != null; }
    public static void clear() { pinned = null; }
    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || pinned == null || minecraft.currentScreen != null) return;
        if (ui == null) ui = new Ui(minecraft, minecraft.fontRenderer);
        mira.core.taskbody.TaskBody.Progress progress = pinned.body.progress(InventoryClient.snapshot());
        Ui.Node card = column(3).pad(6).panel().add(text(pinned.key + " / " + pinned.status.label),
            heading(pinned.title), text(progress.done + "/" + progress.total + " checklist items", MUTED));
        ui.draw(card, 8, 8, 220, 54, -1, -1);
    }
}
