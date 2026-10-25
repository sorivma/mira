package mira.platform.forge1710.client.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Keyboard;

/** Small immediate-mode view tree. Layout, painting and input share the same clipped bounds. */
public final class Ui {
    public static final int TEXT = 0xFF404040, MUTED = 0xFF505050, PANEL = 0xFFC6C6C6;
    public static final int EDGE = 0xFF373737, ACCENT = 0xFF305A8A;
    public static final int ERROR = 0xFF9F2828;
    public static final int SUCCESS = 0xFF387432, PAPER = 0xFFDEDEDE;
    private final Minecraft minecraft;
    private final FontRenderer font;
    private final MinecraftSkin skin;
    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollHit> scrolls = new ArrayList<>();
    private final List<TextInput> fields = new ArrayList<>();
    private int mouseX;
    private int mouseY;
    private int scaleFactor;
    private ChoiceMenu menu;
    private List<String> tooltip;
    private Box paintClip;
    private ScrollDrag dragging;
    private final DragAndDrop cardDrag = new DragAndDrop();
    private boolean paintingPreview;
    private boolean paintInteractive = true;
    private TextInput pendingFocus;
    private String pendingAnchor;

    public Ui(Minecraft minecraft, FontRenderer font) {
        this.minecraft = minecraft;
        this.font = font;
        skin = new MinecraftSkin(minecraft, font);
    }

    public static Node row(int gap) { return new Node(true, gap); }
    public static Node column(int gap) { return new Node(false, gap); }
    /** A content-sized group for forms inside a scrolling column. */
    public static Node stack(int gap, Node... children) { return column(gap).add(children).fitHeight(); }
    public static Node labelled(String label, Node control) { return stack(3, text(label, MUTED), control); }

    /** Quiet, left-aligned navigation, distinct from a command button. */
    public static Node navigation(String label, boolean selected, Runnable action) {
        return new Node(false, 0).height(23).click(action).hint(label).paint((ui, box) -> {
            if (selected || ui.hover(box)) Gui.drawRect(box.x, box.y, box.x + box.w, box.y + box.h,
                selected ? PAPER : 0x33FFFFFF);
            if (selected) Gui.drawRect(box.x, box.y + 3, box.x + 3, box.y + box.h - 3, ACCENT);
            ui.font.drawString(ui.fit((selected ? EnumChatFormatting.BOLD : "") + label, box.w - 14),
                box.x + 8, box.y + (box.h - 8) / 2, selected ? ACCENT : TEXT);
        });
    }

    /** A selectable two-line record. Its state is also expressed in text. */
    public static Node choiceRow(String title, String detail, boolean selected, Runnable action) {
        return new Node(false, 0).height(34).click(action).hint(title, detail).paint((ui, box) -> {
            if (selected || ui.hover(box)) Gui.drawRect(box.x, box.y, box.x + box.w, box.y + box.h,
                selected ? PAPER : 0x33FFFFFF);
            if (selected) Gui.drawRect(box.x, box.y + 4, box.x + 3, box.y + box.h - 4, ACCENT);
            ui.font.drawString(ui.fit(title, box.w - 16), box.x + 8, box.y + 5, selected ? ACCENT : TEXT);
            ui.font.drawString(ui.fit(detail, box.w - 16), box.x + 8, box.y + 19, MUTED);
        });
    }

    public static Node actionButton(String label, boolean enabled, boolean primary, Runnable action) {
        return new Node(false, 0).height(22).click(enabled ? action : null).hint(label).paint((ui, box) -> {
            boolean hovered = enabled && ui.hover(box);
            ui.skin.button(box.x, box.y, box.w, box.h, hovered, !enabled);
            if (primary && enabled) Gui.drawRect(box.x + 2, box.y + 3, box.x + 4, box.y + box.h - 3, 0xFF91B9E0);
            String value = ui.fit(label, box.w - 12);
            ui.font.drawStringWithShadow(value, box.x + (box.w - ui.font.getStringWidth(value)) / 2,
                box.y + (box.h - 8) / 2, !enabled ? 0xA0A0A0 : hovered ? 0xFFFFA0 : 0xE0E0E0);
        });
    }
    public static Node canvas() { Node n = new Node(false, 0); n.absolute = true; return n; }
    public static final class GraphPoint {
        public final String id, label; public final int x, y, color; public final boolean selected;
        public GraphPoint(String id, String label, int x, int y, int color, boolean selected) {
            this.id = id; this.label = label; this.x = x; this.y = y; this.color = color; this.selected = selected;
        }
    }
    public static final class GraphLink {
        public final String id, from, to;
        public GraphLink(String id, String from, String to) { this.id = id; this.from = from; this.to = to; }
    }
    /** Shared graph layout, arrows, hit targets and drag handling; feature views supply only values/actions. */
    public static Node graph(int width, int height, List<GraphPoint> points, List<GraphLink> links,
            Consumer<String> select, java.util.function.BiConsumer<String, String> connect,
            java.util.function.BiConsumer<String, int[]> move) {
        Node result = canvas().width(width).height(height).inset();
        int extentX = 500, extentY = 220;
        for (GraphPoint p : points) { extentX = Math.max(extentX, p.x + 130); extentY = Math.max(extentY, p.y + 50); }
        final double sx = Math.max(0.01, (width - 12.0) / extentX), sy = Math.max(0.01, (height - 12.0) / extentY);
        int nodeWidth = Math.max(32, Math.min(110, (int) (125 * sx))), nodeHeight = 26;
        Map<String, int[]> positions = new java.util.HashMap<>();
        for (GraphPoint p : points) positions.put(p.id, new int[] {Math.max(5, Math.min(width - nodeWidth - 5, (int) (p.x * sx) + 5)), Math.max(5, Math.min(height - nodeHeight - 5, (int) (p.y * sy) + 5))});
        Node arrows = column(0).width(width).height(height).paint((ui, box) -> {
            for (GraphLink link : links) {
                int[] from = positions.get(link.from), to = positions.get(link.to); if (from == null || to == null) continue;
                int x1 = box.x + from[0] + nodeWidth, y1 = box.y + from[1] + nodeHeight / 2;
                int x2 = box.x + to[0], y2 = box.y + to[1] + nodeHeight / 2;
                if (link.from.equals(link.to)) { int top = y1 - 24; graphLine(x1, y1, x1 + 9, top, MUTED); graphLine(x1 + 9, top, x2 - 8, top, MUTED); graphLine(x2 - 8, top, x2, y2, MUTED); }
                else graphLine(x1, y1, x2, y2, ACCENT);
                graphLine(x2 - 5, y2 - 3, x2, y2, ACCENT); graphLine(x2 - 5, y2 + 3, x2, y2, ACCENT);
            }
        });
        result.add(arrows);
        if (move != null) for (int x = 0; x < width; x += 40) for (int y = 0; y < height; y += 32) {
            final int gx = (int) (x / sx), gy = (int) (y / sy);
            result.add(column(0).width(Math.min(40, width - x)).height(Math.min(32, height - y)).at(x, y)
                .dropTarget(GraphPoint.class, p -> move.accept(p.id, new int[] {gx, gy})));
        }
        for (GraphLink link : links) {
            int[] from = positions.get(link.from), to = positions.get(link.to); if (from == null || to == null) continue;
            result.add(text(">", ACCENT).width(14).height(14).at((from[0] + nodeWidth + to[0]) / 2, (from[1] + to[1]) / 2 + 5)
                .click(() -> select.accept(link.id)).hint("Select transition"));
        }
        for (GraphPoint p : points) {
            int[] position = positions.get(p.id);
            Node node = button(p.label, p.selected, () -> select.accept(p.id)).width(nodeWidth).height(nodeHeight).at(position[0], position[1]).hint(p.label);
            Painter buttonPaint = node.painter;
            node.paint((ui, box) -> {
                buttonPaint.paint(ui, box);
                Gui.drawRect(box.x + 3, box.y + box.h - 4, box.x + box.w - 3, box.y + box.h - 2, p.color);
            });
            if (connect != null) node.draggable(p).dropTarget(GraphPoint.class, source -> connect.accept(source.id, p.id));
            result.add(node);
        }
        return result;
    }
    private static void graphLine(int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) { int x = steps == 0 ? x1 : x1 + (x2 - x1) * i / steps; int y = steps == 0 ? y1 : y1 + (y2 - y1) * i / steps; Gui.drawRect(x, y, x + 1, y + 1, color); }
    }
    public static Node space() { return column(0); }
    public static Node text(String text) { return text(text, TEXT); }
    public static Node heading(String label) { return text(EnumChatFormatting.BOLD + label); }
    public static Node heading(String label, int color) { return text(EnumChatFormatting.BOLD + label, color); }
    public static Node rule() {
        return new Node(false, 0).height(2).paint((ui, box) -> {
            Gui.drawRect(box.x, box.y, box.x + box.w, box.y + 1, 0xFF8B8B8B);
            Gui.drawRect(box.x, box.y + 1, box.x + box.w, box.y + 2, 0xFFFFFFFF);
        });
    }
    public static Node item(ItemStack stack) {
        return new Node(false, 0).width(20).height(20).paint((ui, box) ->
            ui.skin.item(stack, box.x + Math.max(0, (box.w - 16) / 2), box.y + Math.max(0, (box.h - 16) / 2)));
    }
    public static Node player(String name) {
        return new Node(false, 0).height(12).paint((ui, box) -> {
            ui.skin.face(name, box.x, box.y + (box.h - 8) / 2, 8);
            ui.font.drawString(ui.fit(name, box.w - 12), box.x + 12, box.y + (box.h - 8) / 2, TEXT);
        }).hint(name);
    }
    public static Node playerButton(String name, boolean selected, Runnable action) {
        return new Node(false, 0).height(20).click(action).paint((ui, box) -> {
            ui.skin.button(box.x, box.y, box.w, box.h, ui.hover(box), selected);
            ui.skin.face(name, box.x + 4, box.y + (box.h - 16) / 2, 16);
            ui.font.drawStringWithShadow(ui.fit(name, box.w - 29), box.x + 24, box.y + (box.h - 8) / 2,
                selected || ui.hover(box) ? 0xFFFFA0 : 0xE0E0E0);
        }).hint(name);
    }
    public static Node text(String text, int color) {
        return new Node(false, 0).height(12).paint((ui, box) -> {
            ui.font.drawString(ui.fit(text, box.w), box.x, box.y + Math.max(0, (box.h - 8) / 2), color);
            if (ui.hover(box) && ui.font.getStringWidth(text) > box.w) ui.tooltip = Arrays.asList(text);
        });
    }
    public static Node paragraph(String text) {
        return new Node(false, 0).paint((ui, box) -> ui.font.drawSplitString(text, box.x, box.y, Math.max(1, box.w), TEXT));
    }
    public static Node wrappedParagraph(FontRenderer font, String text, int width) {
        int lines = font.listFormattedStringToWidth(text, Math.max(1, width)).size();
        return paragraph(text).height(Math.max(12, lines * font.FONT_HEIGHT + 3));
    }
    public static Node excerpt(String text, int maxLines) {
        return new Node(false, 0).height(maxLines * 10).paint((ui, box) -> {
            List<?> lines = ui.font.listFormattedStringToWidth(text, Math.max(1, box.w));
            for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
                String line = (String) lines.get(i);
                if (i == maxLines - 1 && lines.size() > maxLines)
                    line = ui.font.trimStringToWidth(line, Math.max(0, box.w - ui.font.getStringWidth("..."))) + "...";
                ui.font.drawString(line, box.x, box.y + i * 10, TEXT);
            }
        }).hint(text);
    }
    public static Node progress(long done, long total) {
        return new Node(false, 0).height(3).paint((ui, box) -> {
            Gui.drawRect(box.x, box.y, box.x + box.w, box.y + box.h, 0xFF8B8B8B);
            int filled = total <= 0 ? 0 : (int) (box.w * Math.max(0, Math.min(1, (double) done / total)));
            if (filled > 0) Gui.drawRect(box.x, box.y, box.x + filled, box.y + box.h, done >= total ? 0xFF387432 : ACCENT);
        });
    }
    public static Node insertionPoint(Runnable insert) {
        return new Node(false, 0).height(10).click(insert).hint("Insert a block here").paint((ui, box) -> {
            if (!ui.hover(box)) return;
            ui.font.drawString("+", box.x + 3, box.y + 1, ACCENT);
            Gui.drawRect(box.x + 20, box.y + 5, box.x + box.w, box.y + 6, 0x66305A8A);
        });
    }
    public static Node richText(RichText.Layout layout, Consumer<String> openLink) {
        return new Node(false, 0).height(layout.height).paint((ui, box) -> {
            for (RichText.Run run : layout.runs) {
                int x = box.x + run.x, y = box.y + run.y;
                if ((run.span.style & RichText.CODE) != 0)
                    Gui.drawRect(x, y - 1, x + run.width, y + 10, 0x22373737);
                ui.font.drawString(run.span.formatted(run.text), x, y,
                    run.span.link == null ? run.span.color : ACCENT);
                if (run.span.link != null && !ui.paintingPreview && ui.paintInteractive) {
                    Box target = new Box(x, y, run.width, RichText.LINE_HEIGHT).intersect(ui.paintClip);
                    ui.hits.add(new Hit(target, () -> {
                        if (run.span.link.startsWith("#")) {
                            try { ui.pendingAnchor = java.net.URLDecoder.decode(run.span.link.substring(1), "UTF-8"); }
                            catch (Exception ignored) { ui.pendingAnchor = run.span.link.substring(1); }
                        } else openLink.accept(run.span.link);
                    }));
                    if (target.contains(ui.mouseX, ui.mouseY)) ui.tooltip = Arrays.asList(run.span.link);
                }
            }
        });
    }
    public static Node markdownImage(String source, String alt, int width, Consumer<String> openLink) {
        MarkdownImages.Image image = MarkdownImages.get(source);
        ResourceLocation texture = image.texture(Minecraft.getMinecraft());
        int imageWidth = Math.min(Math.max(1, width), image.width);
        int imageHeight = Math.max(1, imageWidth * image.height / image.width);
        if (imageHeight > 160) { imageWidth = Math.max(1, imageWidth * 160 / imageHeight); imageHeight = 160; }
        final int w = imageWidth, h = imageHeight;
        return new Node(false, 0).height(texture == null ? 24 : h + 15).hint(alt, source).click(() -> openLink.accept(source))
            .paint((ui, box) -> {
                if (texture == null) {
                    ui.font.drawString(ui.fit((image.failed ? "Image unavailable: " : "Loading image: ") + alt, box.w), box.x, box.y + 6, MUTED);
                    return;
                }
                ui.minecraft.getTextureManager().bindTexture(texture);
                GL11.glColor4f(1, 1, 1, 1); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                Tessellator quad = Tessellator.instance;
                quad.startDrawingQuads();
                quad.addVertexWithUV(box.x, box.y + h, 0, 0, 1);
                quad.addVertexWithUV(box.x + w, box.y + h, 0, 1, 1);
                quad.addVertexWithUV(box.x + w, box.y, 0, 1, 0);
                quad.addVertexWithUV(box.x, box.y, 0, 0, 0);
                quad.draw();
                ui.font.drawString(ui.fit(alt, box.w), box.x, box.y + h + 3, MUTED);
            });
    }
    public static Node button(String label, Runnable action) { return button(label, false, action); }
    public static Node button(String label, boolean selected, Runnable action) {
        return new Node(false, 0).height(20)
            .click(action).paint((ui, box) -> {
                boolean hovered = ui.hover(box);
                ui.skin.button(box.x, box.y, box.w, box.h, hovered, selected);
                String value = ui.fit(label, box.w - 8);
                ui.font.drawStringWithShadow(value, box.x + (box.w - ui.font.getStringWidth(value)) / 2,
                    box.y + (box.h - 8) / 2, hovered || selected ? 0xFFFFA0 : 0xE0E0E0);
            }).hint(label);
    }
    public static Node tab(String label, ItemStack icon, boolean selected, Runnable action) {
        return new Node(false, 0).height(28).click(action).paint((ui, box) -> {
            ui.skin.tab(box.x, box.y, box.w, box.h, selected);
            ui.skin.item(icon, box.x + 6, box.y + 7);
            ui.font.drawString(ui.fit(label, box.w - 30), box.x + 26, box.y + 11, TEXT);
        }).hint(label);
    }
    public static Node select(String label, String value, String[] choices, Consumer<String> choose) {
        return selection(label, value, choices, choose, false);
    }
    public static Node playerSelect(String label, String value, String[] choices, Consumer<String> choose) {
        return selection(label, value, choices, choose, true);
    }
    private static Node selection(String label, String value, String[] choices, Consumer<String> choose, boolean players) {
        if (choices.length == 0) return actionButton(value.isEmpty() ? "No options available" : value, false, false, null).height(20).hint(label, "No options available");
        Node node = new Node(false, 0).height(20);
        node.choice = new ChoiceMenu(label, value, choices, choose, players);
        node.painter = (ui, box) -> {
            ui.skin.button(box.x, box.y, box.w, box.h, ui.hover(box), false);
            if (players) ui.skin.face(value, box.x + 4, box.y + (box.h - 16) / 2, 16);
            int textLeft = players ? 24 : 6;
            ui.font.drawStringWithShadow(ui.fit(value, box.w - textLeft - 14), box.x + textLeft, box.y + (box.h - 8) / 2, 0xE0E0E0);
            int arrowX = box.x + box.w - 10, arrowY = box.y + box.h / 2 - 1;
            Gui.drawRect(arrowX, arrowY, arrowX + 5, arrowY + 1, 0xFFE0E0E0);
            Gui.drawRect(arrowX + 1, arrowY + 1, arrowX + 4, arrowY + 2, 0xFFE0E0E0);
            Gui.drawRect(arrowX + 2, arrowY + 2, arrowX + 3, arrowY + 3, 0xFFE0E0E0);
        };
        return node.hint(label + ": " + value);
    }
    public static Node field(TextInput input) {
        return new Node(false, 0).height(input.height()).paint((ui, box) -> {
            input.synchronize();
            if (input.documentStyle) {
                if (input.focused()) Gui.drawRect(box.x, box.y + box.h - 1, box.x + box.w, box.y + box.h, 0x66305A8A);
                input.draw(ui.font, box.x + 2, box.y + 2, Math.max(1, box.w - 4), Math.max(1, box.h - 4));
                return;
            }
            Gui.drawRect(box.x, box.y, box.x + box.w, box.y + box.h, input.focused() ? 0xFFFFFFFF : 0xFFA0A0A0);
            Gui.drawRect(box.x + 1, box.y + 1, box.x + box.w - 1, box.y + box.h - 1, 0xFF000000);
            input.draw(ui.font, box.x + 4, box.y + 4, Math.max(1, box.w - 8), Math.max(1, box.h - 8));
        }).field(input).hint(input.placeholder);
    }

    public void draw(Node root, int x, int y, int width, int height, int mouseX, int mouseY) {
        hits.clear();
        scrolls.clear();
        List<TextInput> previousFields = new ArrayList<>(fields);
        fields.clear();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        cardDrag.beginFrame(mouseX, mouseY);
        tooltip = null;
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        scaleFactor = resolution.getScaleFactor();
        Box screen = new Box(0, 0, resolution.getScaledWidth(), resolution.getScaledHeight());
        if (pendingFocus != null || pendingAnchor != null) {
            reveal(root, new Box(x, y, width, height), pendingFocus, pendingAnchor);
            pendingFocus = null; pendingAnchor = null;
        }
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            render(root, new Box(x, y, width, height), screen);
            if (menu != null) {
                tooltip = null;
                hits.clear();
                scrolls.clear();
                hits.add(new Hit(screen, () -> menu = null, false));
                render(menu.build(() -> menu = null, font), new Box(menu.x, menu.y, menu.width, menu.height), screen);
            } else if (cardDrag.active()) {
                scissor(screen);
                Box preview = cardDrag.previewBounds();
                Gui.drawRect(preview.x + 3, preview.y + 3, preview.x + preview.w + 3, preview.y + preview.h + 3, 0x66000000);
                paintingPreview = true;
                try { render(cardDrag.preview(), preview, screen); }
                finally { paintingPreview = false; }
            } else if (tooltip != null && !cardDrag.pressed()) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                List<String> lines = new ArrayList<>();
                for (String line : tooltip) for (Object wrapped : font.listFormattedStringToWidth(line, Math.max(40, Math.min(240, screen.w - 32))))
                    lines.add((String) wrapped);
                skin.tooltip(lines, mouseX, mouseY, screen.w, screen.h);
            }
        }
        finally {
            GL11.glPopAttrib();
            for (TextInput field : previousFields) if (!fields.contains(field)) field.focus(false);
        }
    }

    public boolean click(int x, int y) {
        mouseX = x;
        mouseY = y;
        cardDrag.cancel();
        dragging = null;
        blur();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.box.contains(x, y)) {
                // An action may replace the entire view. Discard pointer targets until the next draw.
                hits.clear();
                scrolls.clear();
                if (hit.sound) minecraft.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                hit.action.run();
                return true;
            }
        }
        return false;
    }

    public void blur() { pendingFocus = null; for (TextInput field : fields) field.focus(false); }

    public void focus(TextInput field) { blur(); field.focus(true); pendingFocus = field; }
    public void revealAnchor(String anchor) { pendingAnchor = anchor; }
    public void chooseAtFocus(String label, String[] choices, Consumer<String> choose) {
        if (choices.length == 0) return;
        int x = mouseX, y = mouseY, w = 132, h = 0;
        for (TextInput field : fields) if (field.focused()) {
            x = field.field.xPosition; y = field.field.yPosition; w = field.field.width; h = field.field.height; break;
        }
        menu = new ChoiceMenu(label, choices[0], choices, choose, false, true);
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        menu.place(x, y, w, h, resolution.getScaledWidth(), resolution.getScaledHeight());
    }

    public void type(char character, int key) {
        for (TextInput field : fields) if (field.focused()) { field.type(character, key); break; }
    }
    public boolean typing() { return fields.stream().anyMatch(TextInput::focused); }
    public boolean multilineFocused() { return fields.stream().anyMatch(field -> field instanceof TextArea && field.focused()); }
    public boolean submitFocused() {
        for (TextInput field : fields) if (field.focused()) return field.submit();
        return false;
    }
    public boolean modal() { return menu != null || cardDrag.pressed(); }
    public boolean dropExternal(Object payload, int x, int y) {
        if (menu != null || !cardDrag.external(payload, x, y)) return false;
        hits.clear(); scrolls.clear();
        return true;
    }

    public void focusNext(boolean backwards) {
        if (fields.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < fields.size(); i++) if (fields.get(i).focused()) current = i;
        int next = current < 0 ? (backwards ? fields.size() - 1 : 0)
            : (current + (backwards ? fields.size() - 1 : 1)) % fields.size();
        focus(fields.get(next));
    }

    public void tick() {
        for (TextInput field : fields) field.tick();
        cardDrag.tick();
    }

    public boolean handleMenuKey(char character, int key) {
        if (menu == null) return false;
        menu.key(character, key, () -> menu = null);
        hits.clear();
        scrolls.clear();
        return true;
    }

    public void drag(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (dragging != null) dragging.move(y);
        cardDrag.move(x, y);
    }

    public void release(int x, int y) {
        dragging = null;
        if (cardDrag.release(x, y)) {
            minecraft.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            hits.clear();
            scrolls.clear();
        }
    }

    public boolean handleDragKey(int key) {
        if (!cardDrag.pressed()) return false;
        if (key == Keyboard.KEY_ESCAPE) cardDrag.cancel();
        return true;
    }

    public void cancelPointer() { dragging = null; cardDrag.cancel(); }

    public void wheel(int x, int y, int delta) {
        for (int i = scrolls.size() - 1; i >= 0; i--) {
            ScrollHit hit = scrolls.get(i);
            if (hit.box.contains(x, y)) {
                hit.scroll.offset = Math.max(0, Math.min(hit.scroll.max, hit.scroll.offset - Integer.signum(delta) * 28));
                return;
            }
        }
    }

    /** Resolve focused inputs/heading anchors before clipping, including targets below the viewport. */
    private Box reveal(Node node, Box box, TextInput field, String anchor) {
        if ((field != null && node.field == field) || (anchor != null && anchor.equals(node.anchor))) return box;
        if (node.absolute) {
            for (Node child : node.children) { Box found = reveal(child, new Box(box.x + child.left, box.y + child.top, child.width < 0 ? box.w : child.width, child.height < 0 ? box.h : child.height), field, anchor); if (found != null) return found; }
            return null;
        }
        Box inner = box.inset(node.padding);
        if (node.scroll != null) inner = new Box(inner.x, inner.y, inner.w - 8, inner.h);
        int available = node.horizontal ? inner.w : inner.h;
        int fixed = Math.max(0, node.children.size() - 1) * node.gap, flexible = 0;
        for (Node child : node.children) {
            int requested = node.horizontal ? child.width : child.height;
            if (requested < 0) flexible++; else fixed += requested;
        }
        int remaining = Math.max(0, available - fixed), cursor = node.horizontal ? inner.x : inner.y;
        if (node.scroll != null) {
            node.scroll.max = Math.max(0, fixed - available);
            node.scroll.offset = Math.max(0, Math.min(node.scroll.offset, node.scroll.max));
            cursor -= node.scroll.offset;
        }
        for (Node child : node.children) {
            int size = node.horizontal ? child.width : child.height;
            if (size < 0) { size = remaining / flexible; remaining -= size; flexible--; }
            Box childBox = node.horizontal
                ? new Box(cursor, inner.y, size, child.height < 0 ? inner.h : child.height)
                : new Box(inner.x, cursor, child.width < 0 ? inner.w : child.width, size);
            Box target = reveal(child, childBox, field, anchor);
            if (target != null) {
                if (node.scroll != null) {
                    int previous = node.scroll.offset;
                    int delta = target.y < inner.y || target.h > inner.h ? target.y - inner.y
                        : Math.max(0, target.y + target.h - inner.y - inner.h);
                    node.scroll.offset = Math.max(0, Math.min(node.scroll.max, previous + delta));
                    target = new Box(target.x, target.y - node.scroll.offset + previous, target.w, target.h);
                }
                return target;
            }
            cursor += size + node.gap;
        }
        return null;
    }

    private void render(Node node, Box box, Box inheritedClip) { render(node, box, inheritedClip, false, true); }
    private void render(Node node, Box box, Box inheritedClip, boolean parentHovered, boolean enabled) {
        enabled = enabled && node.interactive;
        Box clip = box.intersect(inheritedClip);
        if (clip.w <= 0 || clip.h <= 0) return;
        if (node.revealOnParentHover && !parentHovered && !cardDrag.active()) return;
        boolean scopeHovered = node.hoverScope ? node.selectedScope || clip.contains(mouseX, mouseY) : parentHovered;
        scissor(clip);
        paintClip = clip;
        paintInteractive = enabled;
        if (node.background != 0) Gui.drawRect(box.x, box.y, box.x + box.w, box.y + box.h, node.background);
        if (node.surface != null) skin.surface(node.surface, box.x, box.y, box.w, box.h);
        if (node.action != null && node.choice == null && node.surface != null && clip.contains(mouseX, mouseY))
            Gui.drawRect(box.x + 2, box.y + 2, box.x + box.w - 2, box.y + box.h - 2, 0x33FFFFFF);
        else if (node.action != null && node.painter == null && clip.contains(mouseX, mouseY))
            Gui.drawRect(box.x, box.y, box.x + box.w, box.y + box.h, 0x33FFFFFF);
        if (!paintingPreview && node.hint != null && clip.contains(mouseX, mouseY)) tooltip = node.hint;
        boolean dropHover = enabled && !paintingPreview && node.drop != null
            && cardDrag.target(clip, node.drop.accepts, node.drop.action);
        if (enabled && !paintingPreview && node.choice != null) hits.add(new Hit(clip, () -> {
            menu = node.choice;
            ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
            menu.place(box.x, box.y, box.w, box.h, resolution.getScaledWidth(), resolution.getScaledHeight());
        }));
        if (enabled && !paintingPreview && node.dragPayload != null) {
            hits.add(new Hit(clip, () -> cardDrag.press(node, node.dragPayload, box, clip, mouseX, mouseY, node.action), false));
        } else if (enabled && !paintingPreview && node.action != null) hits.add(new Hit(clip, node.action));
        if (enabled && !paintingPreview && node.field != null) {
            fields.add(node.field);
            hits.add(new Hit(clip, () -> node.field.click(mouseX, mouseY), false));
        }
        if (node.painter != null) node.painter.paint(this, box);
        if (node.absolute) {
            for (Node child : node.children) render(child, new Box(box.x + child.left, box.y + child.top,
                child.width < 0 ? box.w : child.width, child.height < 0 ? box.h : child.height), clip, scopeHovered, enabled);
            return;
        }
        Box inner = box.inset(node.padding);
        if (node.scroll != null) inner = new Box(inner.x, inner.y, inner.w - 8, inner.h);
        int available = node.horizontal ? inner.w : inner.h;
        int fixed = Math.max(0, node.children.size() - 1) * node.gap;
        int flexible = 0;
        for (Node child : node.children) {
            int requested = node.horizontal ? child.width : child.height;
            if (requested < 0) flexible++; else fixed += requested;
        }
        int remaining = Math.max(0, available - fixed);
        int cursor = node.horizontal ? inner.x : inner.y;
        if (node.scroll != null) {
            node.scroll.max = Math.max(0, fixed - available);
            node.scroll.offset = Math.max(0, Math.min(node.scroll.offset, node.scroll.max));
            cursor -= node.scroll.offset;
            if (!paintingPreview) {
                scrolls.add(new ScrollHit(box.inset(node.padding).intersect(clip), node.scroll));
                if (enabled) cardDrag.panZone(node.scroll, inner.intersect(clip));
            }
        }
        Box childClip = inner.intersect(clip);
        for (Node child : node.children) {
            int requested = node.horizontal ? child.width : child.height;
            int size = requested;
            if (size < 0) { size = remaining / flexible; remaining -= size; flexible--; }
            Box childBox = node.horizontal
                ? new Box(cursor, inner.y, size, child.height < 0 ? inner.h : child.height)
                : new Box(inner.x, cursor, child.width < 0 ? inner.w : child.width, size);
            render(child, childBox, childClip, scopeHovered, enabled);
            cursor += size + node.gap;
        }
        if (node.scroll != null && node.scroll.max > 0 && inner.h > 0) {
            scissor(box.inset(node.padding).intersect(clip));
            int thumb = Math.min(inner.h, Math.max(15, inner.h * inner.h / (inner.h + node.scroll.max)));
            int top = inner.y + (inner.h - thumb) * node.scroll.offset / node.scroll.max;
            skin.surface(MinecraftSkin.Surface.INSET, inner.x + inner.w + 2, inner.y, 6, inner.h);
            skin.thumb(inner.x + inner.w + 2, top, 6, thumb);
            Box track = new Box(inner.x + inner.w + 1, inner.y, 7, inner.h);
            if (!paintingPreview) hits.add(new Hit(track.intersect(clip), () -> {
                int grab = mouseY >= top && mouseY < top + thumb ? mouseY - top : thumb / 2;
                dragging = new ScrollDrag(node.scroll, track, thumb, grab);
                dragging.move(mouseY);
            }, false));
        }
        if (!paintingPreview && cardDrag.isSource(node.dragPayload)) {
            scissor(clip);
            Gui.drawRect(box.x, box.y, box.x + box.w, box.y + box.h, 0x88909090);
        }
        if (dropHover && !cardDrag.isSource(node.dragPayload)) {
            scissor(clip);
            int color = 0xFF38842A;
            Gui.drawRect(box.x, box.y, box.x + box.w, box.y + 2, color);
            if (!node.drop.before) {
                Gui.drawRect(box.x, box.y, box.x + 2, box.y + box.h, color);
                Gui.drawRect(box.x + box.w - 2, box.y, box.x + box.w, box.y + box.h, color);
                Gui.drawRect(box.x, box.y + box.h - 2, box.x + box.w, box.y + box.h, color);
            }
        }
    }

    private void scissor(Box box) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(box.x * scaleFactor, minecraft.displayHeight - (box.y + box.h) * scaleFactor,
            Math.max(0, box.w * scaleFactor), Math.max(0, box.h * scaleFactor));
    }

    private boolean hover(Box box) { return paintInteractive && box.contains(mouseX, mouseY) && paintClip.contains(mouseX, mouseY); }
    private String fit(String text, int width) {
        if (width <= 0) return "";
        return font.getStringWidth(text) <= width ? text : font.trimStringToWidth(text, Math.max(0, width - font.getStringWidth("..."))) + "...";
    }

    public static final class Scroll {
        private int offset;
        private int max;
        public void reset() { offset = 0; }
        public void end() { offset = Integer.MAX_VALUE; }
        void nudge(int delta) { offset = Math.max(0, Math.min(max, offset + delta)); }
        void reveal(int top, int bottom, int viewport) {
            if (top < offset) offset = top;
            else if (bottom > offset + viewport) offset = bottom - viewport;
        }
    }

    public static final class Node {
        private final boolean horizontal;
        private final int gap;
        private int width = -1, height = -1, padding, background;
        private int left, top;
        private boolean absolute, interactive = true;
        private Painter painter;
        private Runnable action;
        private Scroll scroll;
        private TextInput field;
        private MinecraftSkin.Surface surface;
        private List<String> hint;
        private ChoiceMenu choice;
        private Object dragPayload;
        private Drop drop;
        private String anchor;
        private boolean hoverScope, selectedScope, revealOnParentHover;
        private final List<Node> children = new ArrayList<>();
        private Node(boolean horizontal, int gap) { this.horizontal = horizontal; this.gap = gap; }
        public Node width(int value) { width = value; return this; }
        public Node height(int value) { height = value; return this; }
        public Node interactive(boolean value) { interactive = value; return this; }
        public Node at(int x, int y) { left = x; top = y; return this; }
        public int preferredHeight() { return height; }
        /** Call after composing children; layout calculations remain in the shared UI module. */
        public Node fitHeight() {
            int size = horizontal ? 0 : Math.max(0, children.size() - 1) * gap;
            for (Node child : children) {
                int childHeight = Math.max(0, child.height);
                size = horizontal ? Math.max(size, childHeight) : size + childHeight;
            }
            height = padding * 2 + size;
            return this;
        }
        public Node pad(int value) { padding = value; return this; }
        public Node background(int color) { background = color; return this; }
        public Node panel() { surface = MinecraftSkin.Surface.PANEL; return this; }
        public Node sheet() { surface = MinecraftSkin.Surface.SHEET; return this; }
        public Node inset() { surface = MinecraftSkin.Surface.INSET; return this; }
        public Node slot() { surface = MinecraftSkin.Surface.SLOT; return this; }
        public Node hint(String... lines) { hint = Arrays.asList(lines); return this; }
        public Node click(Runnable value) { action = value; return this; }
        public Node anchor(String value) { anchor = value; return this; }
        public Node hoverScope(boolean selected) { hoverScope = true; selectedScope = selected; return this; }
        public Node revealOnParentHover() { revealOnParentHover = true; return this; }
        public Node draggable(Object payload) { dragPayload = payload; return this; }
        public <T> Node dropTarget(Class<T> type, Consumer<T> action) {
            drop = new Drop(type::isInstance, payload -> action.accept(type.cast(payload)), false);
            return this;
        }
        public <T> Node dropBefore(Class<T> type, Consumer<T> action) {
            drop = new Drop(type::isInstance, payload -> action.accept(type.cast(payload)), true);
            return this;
        }
        public Node scroll(Scroll value) { scroll = value; return this; }
        public Node add(Node... values) { for (Node value : values) children.add(value); return this; }
        private Node paint(Painter value) { painter = value; return this; }
        private Node field(TextInput value) { field = value; return this; }
    }

    private interface Painter { void paint(Ui ui, Box box); }
    private static final class Drop {
        final Predicate<Object> accepts;
        final Consumer<Object> action;
        final boolean before;
        Drop(Predicate<Object> accepts, Consumer<Object> action, boolean before) {
            this.accepts = accepts; this.action = action; this.before = before;
        }
    }
    private static class Hit {
        final Box box;
        final Runnable action;
        final boolean sound;
        Hit(Box box, Runnable action) { this(box, action, true); }
        Hit(Box box, Runnable action, boolean sound) { this.box = box; this.action = action; this.sound = sound; }
    }
    private static final class ScrollHit {
        final Box box;
        final Scroll scroll;
        ScrollHit(Box box, Scroll scroll) { this.box = box; this.scroll = scroll; }
    }
    private static final class ScrollDrag {
        final Scroll scroll;
        final Box track;
        final int thumb, grab;
        ScrollDrag(Scroll scroll, Box track, int thumb, int grab) {
            this.scroll = scroll; this.track = track; this.thumb = thumb; this.grab = grab;
        }
        void move(int mouseY) {
            int travel = track.h - thumb;
            if (travel > 0) scroll.offset = Math.max(0, Math.min(scroll.max, (mouseY - track.y - grab) * scroll.max / travel));
        }
    }
    static final class Box {
        final int x, y, w, h;
        Box(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = Math.max(0, w); this.h = Math.max(0, h); }
        boolean contains(int px, int py) { return px >= x && py >= y && px < x + w && py < y + h; }
        Box inset(int padding) { return new Box(x + padding, y + padding, w - padding * 2, h - padding * 2); }
        Box intersect(Box other) {
            int left = Math.max(x, other.x), top = Math.max(y, other.y);
            return new Box(left, top, Math.min(x + w, other.x + other.w) - left, Math.min(y + h, other.y + other.h) - top);
        }
    }
}
