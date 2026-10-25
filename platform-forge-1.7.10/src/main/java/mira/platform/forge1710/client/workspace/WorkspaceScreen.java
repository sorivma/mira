package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.ui.Ui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.Loader;
import mira.platform.forge1710.client.NeiBridge;
import mira.platform.forge1710.client.InventoryClient;
import mira.platform.forge1710.inventory.ItemDescriptors;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Minecraft lifecycle adapter. Feature rendering and session actions live in the workspace modules. */
public final class WorkspaceScreen extends GuiContainer {
    private final boolean nei = Loader.isModLoaded("NotEnoughItems");
    private WorkspaceSession session;
    private WorkspaceShell shell;
    private Ui ui;
    private boolean repeatEvents;
    private boolean keyboardActive;
    public WorkspaceScreen() { super(new Container() { public boolean canInteractWith(EntityPlayer player) { return true; } }); }

    @Override
    public void initGui() {
        xSize = Math.min(920, Math.max(164, width - (nei ? 138 : 12)));
        ySize = Math.min(520, height - (nei ? 44 : 12));
        super.initGui();
        guiLeft = nei ? 6 : (width - xSize) / 2;
        guiTop = (height - ySize) / 2;
        // Empty client-only container: normal player inventory remains the server's active window.
        if (mc.thePlayer != null) mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer;
        ui = new Ui(mc, fontRendererObj);
        if (session == null) session = new WorkspaceSession(new WorkspaceData(), mc.thePlayer == null ? "You" : mc.thePlayer.getCommandSenderName(), JiraClient.backend());
        if (shell == null) shell = new WorkspaceShell(session, fontRendererObj, () -> mc.displayGuiScreen(null));
        if (!keyboardActive) repeatEvents = Keyboard.areRepeatEventsEnabled();
        keyboardActive = true;
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        ui.draw(shell.build(xSize, ui), guiLeft, guiTop, xSize, ySize, mouseX, mouseY);
    }
    @Override protected void handleMouseClick(Slot slot, int slotId, int button, int mode) {}
    public boolean workspacePoint(int x, int y) { return ui != null && ui.modal() || x >= guiLeft && x < guiLeft + xSize && y >= guiTop && y < guiTop + ySize; }
    public boolean overlapsWorkspace(int x, int y, int w, int h) { return x < guiLeft + xSize && x + w > guiLeft && y < guiTop + ySize && y + h > guiTop; }
    public boolean dropItem(ItemStack stack, int x, int y) {
        if (ui == null || stack == null || stack.getItem() == null) return false;
        try { ItemDescriptors.describe(stack); }
        catch (RuntimeException invalid) { return false; }
        return ui.dropExternal(stack.copy(), x, y);
    }
    public boolean ownsKeyboard() { return ui != null && (ui.typing() || ui.modal()); }
    public void workspaceKey(char character, int key) { keyTyped(character, key); }
    public void blurWorkspace() { if (ui != null) ui.blur(); }
    public void openPinned() { if (session != null && PinnedTaskHud.issue() != null) session.openIssue(PinnedTaskHud.issue().key); }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (nei && !ui.modal() && NeiBridge.mouseDown(this, mouseX, mouseY, button)) return;
        if (button == 0) ui.click(mouseX, mouseY);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (nei) NeiBridge.dragged(this, mouseX, mouseY, button, elapsed);
        if (button == 0) ui.drag(mouseX, mouseY);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (nei) NeiBridge.mouseUp(this, mouseX, mouseY, button);
        if (button == 0) ui.release(mouseX, mouseY);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) ui.wheel(Mouse.getEventX() * width / mc.displayWidth,
            height - Mouse.getEventY() * height / mc.displayHeight - 1, wheel);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (ui.handleDragKey(key)) return;
        if (ui.handleMenuKey(character, key)) return;
        if (key == Keyboard.KEY_ESCAPE) {
            ui.blur();
            if (!shell.back()) mc.displayGuiScreen(null);
        } else if (isCtrlKeyDown() && key == Keyboard.KEY_F) {
            shell.focusSearch(ui);
        } else if (isCtrlKeyDown() && key == Keyboard.KEY_Z && !ui.typing()) {
            session.undo();
        } else if ((isCtrlKeyDown() && key == Keyboard.KEY_N) || (key == Keyboard.KEY_C && !ui.typing())) {
            shell.create(ui);
        } else if (key == Keyboard.KEY_TAB) {
            ui.focusNext(isShiftKeyDown());
        } else if (key == Keyboard.KEY_SLASH && !ui.typing()) {
            shell.insertBlock(ui);
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (!isCtrlKeyDown() && ui.multilineFocused()) ui.type(character, key);
            else if (!isCtrlKeyDown() && ui.submitFocused()) return;
            else shell.submit(ui, isCtrlKeyDown());
        } else {
            ui.type(character, key);
        }
    }

    @Override
    public void updateScreen() { super.updateScreen(); ui.tick(); InventoryClient.update(); }

    @Override
    public void onGuiClosed() {
        ui.cancelPointer();
        if (nei) NeiBridge.closed();
        Keyboard.enableRepeatEvents(repeatEvents);
        keyboardActive = false;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
