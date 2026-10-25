package mira.integration.agenttools.tasks;

import java.util.*;
import mira.core.jira.BodyValues;
import mira.core.jira.Model.*;
import mira.core.taskbody.*;

/** Bounded text projection of every persisted body type. Material counts are requirements only. */
final class TaskText {
    private final int offset;
    private final StringBuilder page = new StringBuilder();
    private long length;
    TaskText(int offset) { this.offset = offset; }
    void add(String text) {
        long start = Math.max(0L, (long) offset - length);
        if (start < text.length() && page.length() < 8192) {
            int from = (int) start;
            int to = from + Math.min(text.length() - from, 8192 - page.length());
            page.append(text, from, to);
        }
        length += text.length();
    }
    String text() { return page.toString(); }
    Object next() { long next = (long) offset + page.length(); return next < length && next <= Integer.MAX_VALUE ? next : null; }

    void body(State state, Entity task) {
        List<Entity> blocks = new ArrayList<>();
        for (Entity block : state.list(Kind.BLOCK, task.workspace)) if (!block.archived() && block.get("task").equals(task.id)) blocks.add(block);
        blocks.sort(Comparator.comparingDouble((Entity block) -> Double.parseDouble(block.get("rank"))).thenComparing(block -> block.id));
        for (Entity block : blocks) {
            TaskWidget widget = BodyValues.decode(block.get("content"));
            add("\n[" + widget.id + "] " + widget.title + "\n");
            if (widget instanceof NoteWidget) add(((NoteWidget) widget).text);
            else if (widget instanceof ChecklistWidget) for (ChecklistWidget.Step step : ((ChecklistWidget) widget).steps) {
                add(step.checked ? "[x] " : "[ ] "); add(step.text); add("\n");
            } else if (widget instanceof MaterialsWidget) for (MaterialsWidget.Material material : ((MaterialsWidget) widget).materials) {
                add("Required " + material.required + "; match=" + material.match + "; ore=" + material.ore
                    + "; includeBags=" + material.includeBags + "; automatic=" + material.automatic
                    + (material.automatic ? "; inventory progress not observed" : "; checked=" + material.checked) + "; alternatives: ");
                for (ItemSpec item : material.alternatives) {
                    add(item.displayName); add(" (" + item.item + ":" + item.damage + ", tagHash=" + item.tagHash + ") ");
                }
                add("\n");
            } else if (widget instanceof LocationWidget) {
                LocationWidget location = (LocationWidget) widget;
                add("Dimension " + location.dimension + ": " + location.x + ", " + location.y + ", " + location.z);
            } else if (widget instanceof LinksWidget) for (String blocker : ((LinksWidget) widget).blockers) add("Blocked by " + blocker + "\n");
            add("\n");
        }
    }
}
