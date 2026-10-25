package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.workspace.DemoWorkspace.Issue;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Project;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Status;
import mira.core.taskbody.TaskBody;
import mira.core.taskbody.NoteWidget;

/** Detached form values: cancelling Edit cannot mutate an issue. */
final class IssueDraft {
    final Project project;
    final Issue original;
    String title = "", description = "", type = "Task", priority = "Medium";
    String assignee = "Unassigned", sprint = "Backlog", validation = "";
    Status status = Status.OPEN;
    TaskBody body = new TaskBody();
    String template = "Blank";
    private DocumentDraft document;

    DocumentDraft document() {
        if (document == null) {
            document = new DocumentDraft(DocumentDraft.compose(description, body, new NoteWidget()));
            description = "";
            body = document.body;
        }
        return document;
    }

    IssueDraft(Project project, Issue original) {
        this.project = project;
        this.original = original;
        if (original != null) {
            title = original.title;
            description = original.description;
            type = original.type;
            priority = original.priority;
            assignee = original.assignee;
            sprint = original.sprint;
            status = original.status;
            body = original.body.copy();
        }
    }
}
