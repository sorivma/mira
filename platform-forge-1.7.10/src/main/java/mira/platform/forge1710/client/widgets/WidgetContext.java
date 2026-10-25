package mira.platform.forge1710.client.widgets;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import mira.core.taskbody.InventorySnapshot;
import mira.core.taskbody.MaterialsWidget.Material;
import mira.core.taskbody.TaskBody;
import mira.platform.forge1710.client.ui.TextInput;
import mira.platform.forge1710.client.ui.TextArea;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.MarkdownEditor;
import net.minecraft.client.gui.FontRenderer;

public final class WidgetContext {
    public static final class Inputs {
        final Map<Object, TextInput> fields = new LinkedHashMap<>();
        final Map<Object, String> values = new HashMap<>();
        final Map<Object, MarkdownEditor> markdown = new HashMap<>();
    }
    public final boolean editing;
    public final TaskBody body;
    public final InventorySnapshot inventory;
    public final Map<Material, Long> allocated;
    public final FontRenderer font;
    public final Ui ui;
    public final int width;
    public final Consumer<String> openIssue;
    public boolean sharedTextTools;
    public Runnable nextParagraph;
    public final Function<String, Boolean> issueDone;
    private final Consumer<Runnable> mutate;
    private final Inputs inputs;
    private final List<TextInput> activeInputs = new ArrayList<>();
    public WidgetContext(TaskBody body, boolean editing, InventorySnapshot inventory, FontRenderer font, Ui ui, int width,
            Inputs inputs, Consumer<Runnable> mutate, Consumer<String> openIssue, Function<String, Boolean> issueDone) {
        this.body = body; this.editing = editing; this.inventory = inventory; this.font = font; this.ui = ui;
        this.width = width;
        this.inputs = inputs; this.mutate = mutate; this.openIssue = openIssue; this.issueDone = issueDone;
        allocated = inventory.allocate(body);
    }
    public void change(Runnable action) { mutate.accept(action); }
    public void focusFirstInput() {
        if (!activeInputs.isEmpty()) ui.focus(activeInputs.get(0));
    }
    public boolean focusedInput() { return activeInputs.stream().anyMatch(TextInput::focused); }
    private TextInput active(TextInput field) { if (!activeInputs.contains(field)) activeInputs.add(field); return field; }
    public TextInput input(Object key, int limit, String hint, Supplier<String> read, Consumer<String> write) {
        return active(inputs.fields.computeIfAbsent(key, ignored -> new TextInput(font, limit, hint, read, value -> change(() -> write.accept(value)))));
    }
    public TextInput buffer(Object key, String hint) {
        return active(inputs.fields.computeIfAbsent(key, ignored -> new TextInput(font, 160, hint, () -> value(key), value -> inputs.values.put(key, value))));
    }
    public TextInput area(Object key, String hint, Supplier<String> read, Consumer<String> write) {
        return active(inputs.fields.computeIfAbsent(key, ignored -> new TextArea(font, 4000, 4, hint, read, value -> change(() -> write.accept(value)))));
    }
    public MarkdownEditor markdown(Object key, String hint, Supplier<String> read, Consumer<String> write) {
        MarkdownEditor editor = inputs.markdown.computeIfAbsent(key, ignored -> new MarkdownEditor(font, 6, hint, read,
            value -> change(() -> write.accept(value))));
        inputs.fields.putIfAbsent(key, editor.input);
        active(editor.input);
        return editor;
    }
    public MarkdownEditor firstMarkdown() {
        return inputs.markdown.isEmpty() ? null : inputs.markdown.values().iterator().next();
    }
    public String value(Object key) { return inputs.values.getOrDefault(key, ""); }
    public void clear(Object key) { inputs.values.put(key, ""); }
}
