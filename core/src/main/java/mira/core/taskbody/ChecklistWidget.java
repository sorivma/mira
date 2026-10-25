package mira.core.taskbody;

import java.util.ArrayList;
import java.util.List;

public final class ChecklistWidget extends TaskWidget {
    public static final class Step {
        public String text;
        public boolean checked;
        public Step(String text) { this.text = text; }
    }
    public final List<Step> steps = new ArrayList<>();
    public ChecklistWidget() { super("Checklist"); }
    private ChecklistWidget(String id, String title) { super(id, title); }
    @Override public ChecklistWidget copy() {
        ChecklistWidget result = new ChecklistWidget(id, title);
        for (Step step : steps) { Step value = new Step(step.text); value.checked = step.checked; result.steps.add(value); }
        return result;
    }
    @Override public TaskBody.Progress progress(TaskBody body, InventorySnapshot inventory) {
        return new TaskBody.Progress((int) steps.stream().filter(step -> step.checked).count(), steps.size());
    }
}
