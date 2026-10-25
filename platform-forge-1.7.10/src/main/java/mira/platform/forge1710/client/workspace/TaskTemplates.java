package mira.platform.forge1710.client.workspace;

import mira.core.taskbody.ChecklistWidget;
import mira.core.taskbody.MaterialsWidget;

final class TaskTemplates {
    static final String[] NAMES = {"Blank", "Build", "Repair", "Production"};
    static void apply(IssueDraft draft, String name) {
        if (draft.template.equals(name)) return;
        draft.template = name;
        if (name.equals("Blank")) return;
        ChecklistWidget checklist = new ChecklistWidget();
        if (name.equals("Build")) {
            draft.type = "Story";
            checklist.steps.add(new ChecklistWidget.Step("Prepare the site"));
            checklist.steps.add(new ChecklistWidget.Step("Build and connect"));
            checklist.steps.add(new ChecklistWidget.Step("Test under load"));
        } else if (name.equals("Repair")) {
            draft.type = "Bug"; draft.priority = "High";
            checklist.steps.add(new ChecklistWidget.Step("Reproduce the failure"));
            checklist.steps.add(new ChecklistWidget.Step("Fix the cause"));
            checklist.steps.add(new ChecklistWidget.Step("Check normal operation"));
        } else {
            checklist.steps.add(new ChecklistWidget.Step("Prepare inputs"));
            checklist.steps.add(new ChecklistWidget.Step("Run production"));
            checklist.steps.add(new ChecklistWidget.Step("Verify output quantity"));
        }
        draft.body.widgets.add(new MaterialsWidget()); draft.body.widgets.add(checklist);
    }
}
