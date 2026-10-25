package mira.platform.forge1710.client.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.FontRenderer;
import static mira.platform.forge1710.client.ui.Ui.*;

/** Shared Markdown source/preview control for descriptions and interleaved text blocks. */
public final class MarkdownEditor {
    public final TextArea input;
    private final FontRenderer font;
    private final Supplier<String> read;
    private boolean preview;
    public MarkdownEditor(FontRenderer font, int rows, String hint, Supplier<String> read, Consumer<String> write) {
        this.font = font; this.read = read;
        input = new TextArea(font, 16000, rows, hint, read, write);
    }
    public void edit() { preview = false; }
    public Markdown.View build(Ui ui, int width, Consumer<String> openIssue) {
        Markdown.View tools = toolbar(ui, width), content = body(width, openIssue, false);
        return new Markdown.View(column(4).add(tools.node, content.node), tools.height + content.height + 4);
    }
    public Markdown.View toolbar(Ui ui, int width) {
        Ui.Node tools = row(3).height(20).add(
            button(preview ? "Write" : "Preview", () -> { preview = !preview; if (!preview) ui.focus(input); }).width(54));
        Ui.Node formatting = !preview && width < 200 ? row(3).height(20) : tools;
        if (!preview) {
            formatting.add(button("\u00a7lB", () -> wrap(ui, "**", "**")).width(20).hint("Bold"),
                button("\u00a7oI", () -> wrap(ui, "*", "*")).width(20).hint("Italic"),
                button("Link", () -> wrap(ui, "[", "](https://)")).width(32).hint("Link", "Use an issue key such as BASE-1 as the destination"));
            tools.add(select("Format text", "Format", new String[] {"Heading", "List", "Task list", "Quote", "Code block", "Table", "Image", "Strikethrough", "Rule"},
                value -> insertFormat(ui, value)).width(52));
            if (width >= 280) tools.add(button("List", () -> insert(ui, "\n- ")).width(32),
                button("Code", () -> wrap(ui, "`", "`")).width(36));
        }
        Ui.Node toolbar = column(4).add(tools);
        int toolbarHeight = 20;
        if (formatting != tools) { toolbar.add(formatting); toolbarHeight += 24; }
        return new Markdown.View(toolbar, toolbarHeight);
    }
    public Markdown.View body(int width, Consumer<String> openIssue, boolean compact) {
        if (preview) return Markdown.build(font, read.get().isEmpty() ? "*Nothing to preview yet.*" : read.get(), width, openIssue);
        if (compact) { input.documentStyle(); input.fitContent(width - 4); }
        return new Markdown.View(Ui.field(input), input.height());
    }
    private void wrap(Ui ui, String opening, String closing) { input.surround(opening, closing); ui.focus(input); }
    private void insert(Ui ui, String text) { input.insert(text); ui.focus(input); }
    private void insertFormat(Ui ui, String format) {
        switch (format) {
            case "Heading": insert(ui, "\n## Heading\n"); break;
            case "List": insert(ui, "\n- "); break;
            case "Task list": insert(ui, "\n- [ ] "); break;
            case "Quote": insert(ui, "\n> "); break;
            case "Code block": wrap(ui, "\n```\n", "\n```\n"); break;
            case "Table": insert(ui, "\n| Column | Column |\n| --- | --- |\n| Value | Value |\n"); break;
            case "Image": wrap(ui, "![", "](https://)"); break;
            case "Strikethrough": wrap(ui, "~~", "~~"); break;
            case "Rule": insert(ui, "\n\n---\n\n"); break;
            default: break;
        }
    }
}
