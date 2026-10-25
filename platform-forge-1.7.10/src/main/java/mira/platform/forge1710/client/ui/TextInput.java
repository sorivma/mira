package mira.platform.forge1710.client.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

/** Native text editing bound to presentation state. Caret, selection and clipboard stay vanilla. */
public class TextInput {
    final GuiTextField field;
    final String placeholder;
    protected final Supplier<String> read;
    protected final Consumer<String> write;
    private Runnable submit;
    private boolean preserveEditing, synchronizedOnce;
    private final boolean separateTextEvents;
    boolean documentStyle;

    public TextInput(FontRenderer font, int limit, String placeholder, Supplier<String> read, Consumer<String> write) {
        this(font, limit, placeholder, read, write, hasSeparateTextEvents());
    }

    TextInput(FontRenderer font, int limit, String placeholder, Supplier<String> read, Consumer<String> write, boolean separateTextEvents) {
        field = new GuiTextField(font, 0, 0, 100, 20);
        field.setMaxStringLength(limit);
        field.setEnableBackgroundDrawing(false);
        this.placeholder = placeholder;
        this.read = read;
        this.write = write;
        this.separateTextEvents = separateTextEvents;
    }

    static boolean hasSeparateTextEvents() {
        ModContainer lwjgl3ify = Loader.instance().getIndexedModList().get("lwjgl3ify");
        return lwjgl3ify != null && lwjgl3ify.getVersion().startsWith("3.");
    }

    protected final char textCharacter(char character, int key) {
        // lwjgl3ify 3 / SDL emits both a physical key with a naive character and
        // a KEY_NONE committed-text event. Only the latter inserts printable text.
        // Keep control characters and key codes for navigation/clipboard shortcuts.
        return separateTextEvents && key != Keyboard.KEY_NONE && ChatAllowedCharacters.isAllowedCharacter(character)
            ? '\0' : character;
    }

    void synchronize() {
        if (preserveEditing && synchronizedOnce && focused()) return;
        String value = read.get();
        if (!field.getText().equals(value)) field.setText(value);
        synchronizedOnce = true;
    }

    void type(char character, int key) {
        if (field.textboxKeyTyped(textCharacter(character, key), key)) write.accept(field.getText());
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
