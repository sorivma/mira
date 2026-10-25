package mira.platform.forge1710.client.widgets;

import mira.core.taskbody.LinksWidget;
import mira.platform.forge1710.client.ui.Ui;
import static mira.platform.forge1710.client.ui.Ui.*;

final class LinksRenderer implements WidgetRenderer<LinksWidget> {
    public Class<LinksWidget> type() { return LinksWidget.class; }
    public String name() { return "Blocked by"; }
    public LinksWidget create() { return new LinksWidget(); }
    public View build(LinksWidget widget, WidgetContext context) {
        Ui.Node rows = column(4);
        for (String key : widget.blockers) {
            Boolean done = context.issueDone.apply(key);
            Ui.Node row = row(4).height(20).add(text(done == null ? "?" : done ? "Done" : "Blocked", Boolean.TRUE.equals(done) ? 0xFF286628 : ERROR).width(44),
                text(key, ACCENT).click(() -> context.openIssue.accept(key)).hint("Open " + key));
            if (context.editing) row.add(button("-", () -> context.change(() -> widget.blockers.remove(key))).width(18));
            rows.add(row);
        }
        String input = widget.id + "/link";
        Runnable add = () -> {
            String key = context.value(input).trim().toUpperCase(java.util.Locale.ROOT);
            if (!key.isEmpty() && !widget.blockers.contains(key)) context.change(() -> { widget.blockers.add(key); context.clear(input); });
        };
        Runnable submit = () -> {
            if (context.value(input).trim().isEmpty() && context.nextParagraph != null) context.nextParagraph.run();
            else add.run();
        };
        if (context.editing) rows.add(row(4).height(20).add(Ui.field(context.buffer(input, "Issue key, e.g. BASE-1").onSubmit(submit)), button("+", add).width(20)));
        if (widget.blockers.isEmpty() && !context.editing) rows.add(text("Click to link a blocker.", MUTED).height(12));
        return new View(rows, Math.max(12, (widget.blockers.size() + (context.editing ? 1 : 0)) * 24 - 4));
    }
}
