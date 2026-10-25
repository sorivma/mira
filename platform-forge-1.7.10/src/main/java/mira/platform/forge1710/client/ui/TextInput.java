package mira.platform.forge1710.client.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** Native text editing bound to presentation state. Caret, selection and clipboard stay vanilla. */
public class TextInput {
    final GuiTextField field;
    final String placeholder;
    protected final Supplier<String> read;
    protected final Consumer<String> write;
    private Runnable submit;
    private boolean preserveEditing, synchronizedOnce;
    boolean documentStyle;

    public TextInput(FontRenderer font, int limit, String placeholder, Supplier<String> read, Consumer<String> write) {
        this(font, limit, placeholder, read, write, false);
    }

    TextInput(FontRenderer font, int limit, String placeholder, Supplier<String> read, Consumer<String> write, boolean separateTextEvents) {
        field = new GuiTextField(font, 0, 0, 100, 20);
        field.setMaxStringLength(limit);
        field.setEnableBackgroundDrawing(false);
        this.placeholder = placeholder;
        this.read = read;
        this.write = write;
    }

    void synchronize() {
        if (preserveEditing && synchronizedOnce && focused()) return;
        String value = read.get();
        if (!field.getText().equals(value)) field.setText(value);
        synchronizedOnce = true;
    }

    void type(char character, int key) {
        if (field.textboxKeyTyped(character, key)) write.accept(field.getText());
    }
    int height() { return 20; }
    public TextInput onSubmit(Runnable action) { submit = action; return this; }
    /** Permit intermediate numeric edits (such as clearing a value) until focus leaves the field. */
    public TextInput preserveEditing() { preserveEditing = true; return this; }
    boolean submit() { if (submit == null) return false; submit.run(); return true; }
    void click(int x, int y) { field.mouseClicked(x, y, 0); }
    void tick() { field.updateCursorCounter(); }
    void draw(FontRenderer font, int x, int y, int width, int height) {
        field.xPosition = x; field.yPosition = y + (height - font.FONT_HEIGHT) / 2;
        field.width = Math.max(1, width); field.height = height;
        field.drawTextBox();
        if (field.getText().isEmpty() && !focused()) font.drawString(font.trimStringToWidth(placeholder, width), x, field.yPosition, 0xFFAAAAAA);
    }

    public boolean focused() { return field.isFocused(); }
    public void documentStyle() { documentStyle = true; }
    public void focus(boolean value) { field.setFocused(value); }
}
