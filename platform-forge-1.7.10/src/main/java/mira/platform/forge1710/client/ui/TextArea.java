package mira.platform.forge1710.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

/** Multiline editing with selection, clipboard, wrapped caret navigation and visible focus. */
public final class TextArea extends TextInput {
    private final FontRenderer font;
    private final int limit;
    private int rows;
    private BiConsumer<String, String> paragraph;
    private Runnable slash;
    private String value = "";
    private int caret, anchor, firstLine, ticks;
    private List<Line> lines = new ArrayList<>();
    public TextArea(FontRenderer font, int limit, int rows, String placeholder, Supplier<String> read, Consumer<String> write) {
        super(font, limit, placeholder, read, write);
        this.font = font; this.limit = limit; this.rows = rows;
    }
    @Override int height() { return rows * 11 + 8; }
    @Override void synchronize() {
        String next = read.get();
        if (!value.equals(next)) { value = next; caret = Math.min(caret, value.length()); anchor = Math.min(anchor, value.length()); }
    }
    @Override void tick() { ticks++; }
    /** Wrap the selection, or leave the caret between delimiters when the selection is empty. */
    public void surround(String opening, String closing) {
        synchronize();
        int lo = Math.min(caret, anchor), hi = Math.max(caret, anchor);
        String selected = value.substring(lo, hi);
        replace(opening + selected + closing);
        if (value.length() >= lo + opening.length() + selected.length() + closing.length()) {
            anchor = lo + opening.length(); caret = anchor + selected.length();
        }
        focus(true);
    }
    public void insert(String text) { synchronize(); replace(text); focus(true); }
    public void onParagraph(BiConsumer<String, String> action) { paragraph = action; }
    public void onEmptySlash(Runnable action) { slash = action; }
    public void fitContent(int width) {
        synchronize(); layout(Math.max(1, width));
        rows = Math.max(1, Math.min(12, lines.size()));
    }
    @Override void draw(FontRenderer font, int x, int y, int width, int height) {
        field.xPosition = x; field.yPosition = y; field.width = width; field.height = height;
        layout(width);
        int line = caretLine();
        if (line < firstLine) firstLine = line;
        if (line >= firstLine + rows) firstLine = line - rows + 1;
        for (int i = firstLine; i < Math.min(lines.size(), firstLine + rows); i++) {
            Line current = lines.get(i);
            int top = y + (i - firstLine) * 11;
            int lo = Math.max(current.start, Math.min(caret, anchor)), hi = Math.min(current.end, Math.max(caret, anchor));
            if (hi > lo) Gui.drawRect(x + font.getStringWidth(value.substring(current.start, lo)), top,
                x + font.getStringWidth(value.substring(current.start, hi)), top + 10, 0xFF345C99);
            font.drawString(value.substring(current.start, current.end), x, top, documentStyle ? Ui.TEXT : 0xFFE0E0E0);
            if (documentStyle && hi > lo) font.drawString(value.substring(lo, hi),
                x + font.getStringWidth(value.substring(current.start, lo)), top, 0xFFE0E0E0);
        }
        if (value.isEmpty() && (!focused() || documentStyle)) font.drawString(font.trimStringToWidth(placeholder, width), x, y, documentStyle ? Ui.MUTED : 0xFFAAAAAA);
        if (focused() && ticks / 6 % 2 == 0) {
            Line current = lines.get(line);
            int left = x + font.getStringWidth(value.substring(current.start, caret));
            int top = y + (line - firstLine) * 11;
            Gui.drawRect(left, top, left + 1, top + 9, documentStyle ? Ui.TEXT : 0xFFFFFFFF);
        }
    }
    @Override void click(int x, int y) {
        focus(x >= field.xPosition && y >= field.yPosition && x < field.xPosition + field.width && y < field.yPosition + field.height);
        if (!focused()) return;
        layout(field.width);
        int line = Math.max(0, Math.min(lines.size() - 1, firstLine + (y - field.yPosition) / 11));
        caret = atX(lines.get(line), x - field.xPosition); anchor = caret;
    }
    @Override void type(char character, int key) {
        layout(Math.max(1, field.width));
        boolean control = GuiScreen.isCtrlKeyDown(), shift = GuiScreen.isShiftKeyDown();
        if (!control && character == '/' && slash != null && value.trim().isEmpty()) { slash.run(); return; }
        if (control && key == Keyboard.KEY_A) { anchor = 0; caret = value.length(); return; }
        if (control && (key == Keyboard.KEY_C || key == Keyboard.KEY_X)) {
            GuiScreen.setClipboardString(value.substring(Math.min(caret, anchor), Math.max(caret, anchor)));
            if (key == Keyboard.KEY_X) replace("");
            return;
        }
        if (control && key == Keyboard.KEY_V) { replace(GuiScreen.getClipboardString()); return; }
        if (key == Keyboard.KEY_BACK) { if (caret == anchor && caret > 0) anchor--; replace(""); return; }
        if (key == Keyboard.KEY_DELETE) { if (caret == anchor && caret < value.length()) anchor++; replace(""); return; }
        int line = caretLine();
        if (key == Keyboard.KEY_LEFT) caret = Math.max(0, caret - 1);
        else if (key == Keyboard.KEY_RIGHT) caret = Math.min(value.length(), caret + 1);
        else if (key == Keyboard.KEY_HOME) caret = control ? 0 : lines.get(line).start;
        else if (key == Keyboard.KEY_END) caret = control ? value.length() : lines.get(line).end;
        else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            int next = Math.max(0, Math.min(lines.size() - 1, line + (key == Keyboard.KEY_UP ? -1 : 1)));
            caret = atX(lines.get(next), font.getStringWidth(value.substring(lines.get(line).start, caret)));
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (paragraph != null && !shift && !control)
                paragraph.accept(value.substring(0, Math.min(caret, anchor)), value.substring(Math.max(caret, anchor)));
            else replace("\n");
            return;
        }
        else if (!control && ChatAllowedCharacters.isAllowedCharacter(character)) { replace(String.valueOf(character)); return; }
        if (!shift) anchor = caret;
    }
    private void replace(String text) {
        StringBuilder clean = new StringBuilder();
        for (char character : text.replace("\r\n", "\n").replace('\r', '\n').toCharArray())
            if (character == '\n' || ChatAllowedCharacters.isAllowedCharacter(character)) clean.append(character);
        int lo = Math.min(caret, anchor), hi = Math.max(caret, anchor);
        String insert = clean.substring(0, Math.min(clean.length(), Math.max(0, limit - value.length() + hi - lo)));
        value = value.substring(0, lo) + insert + value.substring(hi);
        caret = anchor = lo + insert.length();
        write.accept(value);
    }
    private void layout(int width) {
        lines = new ArrayList<>();
        int start = 0, end = 0;
        while (end < value.length()) {
            if (value.charAt(end) == '\n') { lines.add(new Line(start, end)); start = ++end; }
            else if (end > start && font.getStringWidth(value.substring(start, end + 1)) > width) { lines.add(new Line(start, end)); start = end; }
            else end++;
        }
        lines.add(new Line(start, end));
    }
    private int caretLine() {
        for (int i = 0; i < lines.size(); i++) if (caret <= lines.get(i).end) return i;
        return lines.size() - 1;
    }
    private int atX(Line line, int x) {
        int position = line.start;
        while (position < line.end && font.getStringWidth(value.substring(line.start, position + 1)) <= x) position++;
        return position;
    }
    private static final class Line { final int start, end; Line(int start, int end) { this.start = start; this.end = end; } }
}
