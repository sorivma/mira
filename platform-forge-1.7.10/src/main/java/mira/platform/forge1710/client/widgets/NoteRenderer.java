package mira.platform.forge1710.client.widgets;

import mira.core.taskbody.NoteWidget;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Markdown;
import mira.platform.forge1710.client.ui.MarkdownEditor;
import static mira.platform.forge1710.client.ui.Ui.*;

final class NoteRenderer implements WidgetRenderer<NoteWidget> {
    public Class<NoteWidget> type() { return NoteWidget.class; }
    public String name() { return "Text"; }
    public NoteWidget create() { return new NoteWidget(); }
    public boolean textBlock() { return true; }
    public View build(NoteWidget widget, WidgetContext context) {
        Markdown.View view;
        if (context.editing) {
            MarkdownEditor editor = context.markdown(widget, "Write text, or / to insert a block", () -> widget.text, value -> widget.text = value);
            view = context.sharedTextTools ? editor.body(context.width, context.openIssue, true) : editor.build(context.ui, context.width, context.openIssue);
        } else view = Markdown.build(context.font, widget.text.trim().isEmpty() ? "*Write text or insert a block.*" : widget.text, context.width, context.openIssue);
        return new View(view.node, view.height);
    }
}
