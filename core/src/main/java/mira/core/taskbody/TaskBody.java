package mira.core.taskbody;

import java.util.ArrayList;
import java.util.List;

public final class TaskBody {
    public final List<TaskWidget> widgets = new ArrayList<>();
    public TaskBody copy() {
        TaskBody result = new TaskBody();
        for (TaskWidget widget : widgets) result.widgets.add(widget.copy());
        return result;
    }
    public void move(TaskWidget widget, int direction) {
        int from = widgets.indexOf(widget), to = from + direction;
        if (from >= 0 && to >= 0 && to < widgets.size()) {
            widgets.remove(from);
            widgets.add(to, widget);
        }
    }
    public static final class Progress {
        public final int done, total;
        public Progress(int done, int total) { this.done = done; this.total = total; }
    }
    public Progress progress(InventorySnapshot inventory) {
        int done = 0, total = 0;
        for (TaskWidget widget : widgets) { Progress value = widget.progress(this, inventory); done += value.done; total += value.total; }
        return new Progress(done, total);
    }
}
