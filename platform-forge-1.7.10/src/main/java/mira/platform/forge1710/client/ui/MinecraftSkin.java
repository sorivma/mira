package mira.platform.forge1710.client.ui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Draws from Minecraft's resources, so resource packs also skin Mira. No copied game assets. */
final class MinecraftSkin extends GuiScreen {
    enum Surface { PANEL, SHEET, INSET, SLOT }

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("minecraft", "textures/gui/demo_background.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation TABS = new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("minecraft", "textures/gui/container/inventory.png");
    private final RenderItem items = new RenderItem();

    MinecraftSkin(Minecraft minecraft, FontRenderer font) { mc = minecraft; fontRendererObj = font; }

    void surface(Surface surface, int x, int y, int w, int h) {
        if (surface == Surface.PANEL) nineSlice(PANEL_TEXTURE, x, y, w, h, 0, 0, 248, 166, 4);
        else if (surface == Surface.SHEET) nineSlice(PANEL_TEXTURE, x, y, w, h, 4, 4, 240, 158, 1);
        else nineSlice(INVENTORY, x, y, w, h, 7, 83, 18, 18, 1);
    }

    void button(int x, int y, int w, int h, boolean hovered, boolean selected) {
        nineSlice(WIDGETS, x, y, w, h, 0, selected ? 46 : hovered ? 86 : 66, 200, 20, 2);
    }

    void tab(int x, int y, int w, int h, boolean selected) {
        nineSlice(TABS, x, y, w, h, 0, selected ? 32 : 0, 28, 32, 3);
    }

    void thumb(int x, int y, int w, int h) {
        nineSlice(TABS, x, y, w, h, 232, 0, 12, 15, 2);
    }

    void item(ItemStack stack, int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            items.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    void tooltip(List<String> lines, int x, int y, int screenWidth, int screenHeight) {
        width = screenWidth;
        height = screenHeight;
        drawHoveringText(lines, x, y, fontRendererObj);
    }

    void face(String name, int x, int y, int size) {
        if (PlayerFaces.neutral(name)) {
            int unit = Math.max(1, size / 8);
            drawRect(x + unit * 2, y + unit, x + unit * 6, y + unit * 4, 0xFF777777);
            drawRect(x + unit, y + unit * 5, x + unit * 7, y + unit * 7, 0xFF777777);
            return;
        }
        mc.getTextureManager().bindTexture(PlayerFaces.skin(mc, name));
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int textureWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int textureHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        float v = textureHeight > 0 ? textureWidth / (8.0F * textureHeight) : 0.25F;
        // Both legacy 64x32 and modern/HD skin layouts keep their face in the first head tile.
        faceQuad(x, y, size, 0.125F, v, 0.25F, v * 2);
        faceQuad(x, y, size, 0.625F, v, 0.75F, v * 2);
    }

    private void faceQuad(int x, int y, int size, float u0, float v0, float u1, float v1) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + size, 0, u0, v1);
        tessellator.addVertexWithUV(x + size, y + size, 0, u1, v1);
        tessellator.addVertexWithUV(x + size, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
        tessellator.draw();
    }

    private void nineSlice(ResourceLocation texture, int x, int y, int w, int h,
            int u, int v, int sourceWidth, int sourceHeight, int border) {
        int edge = Math.min(border, Math.min(w, h) / 2);
        if (edge <= 0) return;
        mc.getTextureManager().bindTexture(texture);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int[] targetX = {x, x + edge, x + w - edge, x + w};
        int[] targetY = {y, y + edge, y + h - edge, y + h};
        int[] sourceX = {u, u + border, u + sourceWidth - border, u + sourceWidth};
        int[] sourceY = {v, v + border, v + sourceHeight - border, v + sourceHeight};
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            tessellator.addVertexWithUV(targetX[column], targetY[row + 1], 0, sourceX[column] / 256.0, sourceY[row + 1] / 256.0);
            tessellator.addVertexWithUV(targetX[column + 1], targetY[row + 1], 0, sourceX[column + 1] / 256.0, sourceY[row + 1] / 256.0);
            tessellator.addVertexWithUV(targetX[column + 1], targetY[row], 0, sourceX[column + 1] / 256.0, sourceY[row] / 256.0);
            tessellator.addVertexWithUV(targetX[column], targetY[row], 0, sourceX[column] / 256.0, sourceY[row] / 256.0);
        }
        tessellator.draw();
    }
}
