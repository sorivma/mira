package mira.platform.forge1710.client.widgets;

import mira.core.taskbody.ChecklistWidget;
import mira.platform.forge1710.client.ui.Ui;
import static mira.platform.forge1710.client.ui.Ui.*;

final class ChecklistRenderer implements WidgetRenderer<ChecklistWidget> {
    public Class<ChecklistWidget> type() { return ChecklistWidget.class; }
    public String name() { return "Checklist"; }
    public ChecklistWidget create() { return new ChecklistWidget(); }
    public View build(ChecklistWidget widget, WidgetContext context) {
        Ui.Node rows = column(4);
        int height = 0;
        for (ChecklistWidget.Step step : widget.steps) {
            Ui.Node label = Ui.wrappedParagraph(context.font, (step.checked ? "\u00a7m" : "") + step.text, context.width - 24);
            int rowHeight = context.editing ? 20 : Math.max(20, label.preferredHeight());
            Ui.Node row = row(4).height(rowHeight).add(button(step.checked ? "x" : " ", () -> context.change(() -> step.checked = !step.checked)).width(20)
                .hint(step.checked ? "Mark incomplete" : "Mark complete"));
            if (context.editing) row.add(Ui.field(context.input(step, 300, "Step", () -> step.text, value -> step.text = value)),
                button("-", () -> context.change(() -> widget.steps.remove(step))).width(18));
            else row.add(label.height(rowHeight).hint(step.text));
            rows.add(row); height += rowHeight + 4;
        }
        String key = widget.id + "/add";
        Runnable add = () -> {
            String text = context.value(key).trim();
            if (!text.isEmpty()) context.change(() -> { widget.steps.add(new ChecklistWidget.Step(text)); context.clear(key); });
        };
        Runnable submit = () -> {
            if (context.value(key).trim().isEmpty() && context.nextParagraph != null) context.nextParagraph.run();
            else add.run();
        };
        if (context.editing) { rows.add(row(4).height(20).add(Ui.field(context.buffer(key, "New checklist step").onSubmit(submit)), button("+", add).width(20))); height += 24; }
        else if (widget.steps.isEmpty()) { rows.add(text("Click to add checklist steps.", MUTED).height(16)); height += 20; }
        return new View(rows, Math.max(0, height - 4));
    }
}
