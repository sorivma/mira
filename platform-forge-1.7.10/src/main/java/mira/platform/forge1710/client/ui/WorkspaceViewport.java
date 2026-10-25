package mira.platform.forge1710.client.ui;

/** Workspace bounds and the empty container footprint used by inventory overlays. */
public final class WorkspaceViewport {
    public final int x, y, width, height, containerWidth;

    public WorkspaceViewport(int screenWidth, int screenHeight, boolean items) {
        int gutter = items ? Math.min(132, Math.max(0, (screenWidth - 176) / 2)) : 0;
        width = Math.max(1, Math.min(920, screenWidth - (items ? gutter + 6 : 12)));
        height = Math.max(1, Math.min(520, screenHeight - (items ? 44 : 12)));
        x = items ? 6 : (screenWidth - width) / 2;
        y = (screenHeight - height) / 2;
        // NEI calculates panel width from a centered container, ignoring guiLeft.
        // The container has no slots; its footprint can differ from the workspace.
        containerWidth = items ? screenWidth - gutter * 2 : width;
    }

    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean overlaps(int px, int py, int w, int h) {
        return px < x + width && px + w > x && py < y + height && py + h > y;
    }
}
