package mira.platform.forge1710.client.workspace;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import mira.platform.forge1710.client.InventoryClient;
import mira.platform.forge1710.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import mira.core.jira.Model.*;
import mira.core.jira.BodyValues;
import mira.core.jira.Workflows;
import static mira.platform.forge1710.client.ui.Ui.*;

public final class PinnedTaskHud {
    private static WorkspaceData.Issue pinned;
    private Ui ui;
    static void toggle(WorkspaceData.Issue issue) { pinned = pinned == issue ? null : issue; }
    static boolean isPinned(WorkspaceData.Issue issue) { return pinned == issue; }
    static WorkspaceData.Issue issue() { return pinned; }
    public static boolean active() { return pinned != null; }
    public static void clear() { pinned = null; }
    static void refresh(State state) {
        if (pinned == null || pinned.source == null) return;
        Entity task = state.entities.get(pinned.id);
        if (task == null || task.archived() || state.require(task.get("project"), Kind.PROJECT).archived()) { clear(); return; }
        pinned.source = task; pinned.title = task.get("title"); pinned.description = task.get("description");
        Entity node = Workflows.status(state, Workflows.workflow(state, state.require(task.get("project"), Kind.PROJECT), task.get("type")), task.get("status"));
        pinned.status = new WorkspaceData.Status(node.get("logical"), node.get("name"), node.get("category"), node.get("category").equals("DONE") ? 0xFF286628 : 0xFF2B579A);
        pinned.body = new mira.core.taskbody.TaskBody();
        for (Entity block : RemoteWorkspace.ordered(state.list(Kind.BLOCK, task.workspace))) if (!block.archived() && block.get("task").equals(task.id)) pinned.body.widgets.add(BodyValues.decode(block.get("content")));
    }
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
