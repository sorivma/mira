package mira.platform.forge1710.client;

import codechicken.nei.ItemPanels;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import mira.platform.forge1710.client.workspace.WorkspaceScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

/** Only loaded when NEI is installed; it remains a compile-only optional dependency. */
public final class NeiBridge {
    private static ItemStack pressed;
    private static int pressX, pressY;
    private NeiBridge() {}
    public static void closed() {
        pressed = null;
        ItemPanels.itemPanel.draggedStack = null; ItemPanels.bookmarkPanel.draggedStack = null;
        ItemPanels.itemPanel.mouseDownSlot = -1; ItemPanels.bookmarkPanel.mouseDownSlot = -1;
    }
    public static void register() {
        API.registerNEIGuiHandler(new INEIGuiHandler() {
            @Override public boolean handleDragNDrop(GuiContainer gui, int x, int y, ItemStack stack, int button) {
                if (!(gui instanceof WorkspaceScreen)) return false;
                WorkspaceScreen screen = (WorkspaceScreen) gui;
                if (!screen.workspacePoint(x, y)) return false;
                if (screen.dropItem(stack.copy(), x, y)) stack.stackSize = 0;
                return true; // Ghost items over the workspace are never passed to NEI's cheat-item handler.
            }
            @Override public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
                return gui instanceof WorkspaceScreen && ((WorkspaceScreen) gui).overlapsWorkspace(x, y, w, h);
            }
        });
        GuiContainerManager.inputHandlers.addFirst(new IContainerInputHandler() {
            public boolean keyTyped(GuiContainer gui, char character, int key) {
                if (gui instanceof WorkspaceScreen && ((WorkspaceScreen) gui).ownsKeyboard()) {
                    ((WorkspaceScreen) gui).workspaceKey(character, key); return true;
                }
                return false;
            }
            public void onKeyTyped(GuiContainer gui, char character, int key) {}
            public boolean lastKeyTyped(GuiContainer gui, char character, int key) { return false; }
            public boolean mouseClicked(GuiContainer gui, int x, int y, int button) { return false; }
            public void onMouseClicked(GuiContainer gui, int x, int y, int button) {
                if (gui instanceof WorkspaceScreen && !((WorkspaceScreen) gui).workspacePoint(x, y)) ((WorkspaceScreen) gui).blurWorkspace();
            }
            public void onMouseUp(GuiContainer gui, int x, int y, int button) {}
            public boolean mouseScrolled(GuiContainer gui, int x, int y, int delta) { return gui instanceof WorkspaceScreen && ((WorkspaceScreen) gui).workspacePoint(x, y); }
            public void onMouseScrolled(GuiContainer gui, int x, int y, int delta) {}
            public void onMouseDragged(GuiContainer gui, int x, int y, int button, long time) {}
        });
    }
    public static boolean mouseDown(WorkspaceScreen screen, int x, int y, int button) {
        pressed = ItemPanels.itemPanel.getStackMouseOverWithQuantity(x, y);
        if (pressed == null) pressed = ItemPanels.bookmarkPanel.getStackMouseOverWithQuantity(x, y);
        if (pressed != null) pressed = pressed.copy();
        pressX = x; pressY = y;
        return GuiContainerManager.getManager(screen).mouseClicked(x, y, button);
    }
    public static void dragged(WorkspaceScreen screen, int x, int y, int button, long time) {
        GuiContainerManager.getManager(screen).mouseDragged(x, y, button, time);
        if (pressed != null && (x - pressX) * (x - pressX) + (y - pressY) * (y - pressY) >= 25
                && ItemPanels.itemPanel.draggedStack == null && ItemPanels.bookmarkPanel.draggedStack == null) {
            ItemPanels.itemPanel.draggedStack = pressed.copy(); ItemPanels.itemPanel.mouseDownSlot = -1;
        }
    }
    public static void mouseUp(WorkspaceScreen screen, int x, int y, int button) {
        ItemStack ghost = ItemPanels.itemPanel.draggedStack != null ? ItemPanels.itemPanel.draggedStack : ItemPanels.bookmarkPanel.draggedStack;
        if (ghost != null && screen.dropItem(ghost.copy(), x, y)) {
            ItemPanels.itemPanel.draggedStack = null; ItemPanels.bookmarkPanel.draggedStack = null;
        }
        GuiContainerManager manager = GuiContainerManager.getManager(screen);
        if (!manager.overrideMouseUp(x, y, button)) manager.mouseUp(x, y, button);
        pressed = null;
    }
}
