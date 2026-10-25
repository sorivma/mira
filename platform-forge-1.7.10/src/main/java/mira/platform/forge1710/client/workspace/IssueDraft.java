package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.workspace.WorkspaceData.Issue;
import mira.platform.forge1710.client.workspace.WorkspaceData.Project;
import mira.platform.forge1710.client.workspace.WorkspaceData.Status;
import mira.core.taskbody.TaskBody;
import mira.core.taskbody.NoteWidget;
import mira.core.jira.Model;
import java.util.ArrayList;
import java.util.List;

/** Detached form values: cancelling Edit cannot mutate an issue. */
final class IssueDraft {
    final Project project;
    final Issue original;
    final String id;
    Model.Entity source;
    String sourceType = "", sourceAssignee = "", sourceSprint = "";
    final List<Model.Entity> blocks = new ArrayList<>();
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
        this.id = original == null ? Model.id() : original.id;
        if (original != null) {
            source = original.source == null ? null : original.source.copy();
            for (Model.Entity block : original.blocks) blocks.add(block.copy());
            title = original.title;
            description = original.description;
            type = original.type;
            priority = original.priority;
            assignee = original.assignee;
            sprint = original.sprint;
            sourceType = type; sourceAssignee = assignee; sourceSprint = sprint;
            status = original.status;
            body = original.body.copy();
        }
    }
}
