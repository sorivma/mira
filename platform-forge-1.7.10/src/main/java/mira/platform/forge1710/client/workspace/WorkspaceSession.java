package mira.platform.forge1710.client.workspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import mira.core.taskbody.TaskBody;
import mira.core.taskbody.NoteWidget;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Issue;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Project;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Status;

/** Client-session interactions, independent of Minecraft rendering and input. */
final class WorkspaceSession {
    enum View {
        BOARD("Board"), BACKLOG("Backlog"), ISSUES("Issues");
        final String label;
        View(String label) { this.label = label; }
    }

    private final DemoWorkspace data;
    private final String self;
    private final Map<Project, IssueDraft> createDrafts = new HashMap<>();
    private Project project;
    private View view = View.BOARD;
    private Issue selected;
    private IssueDraft draft;
    private DocumentDraft documentDraft;
    private TaskBody documentSource;
    private String documentDescription;
    private NoteWidget introduction = new NoteWidget();
    private boolean expanded;
    private String query = "", assignee = "Everyone", sprint = "All work", comment = "";
    private int filterRevision;
    private int selectionRevision;
    private String notice = "";
    private final Deque<Undo> history = new ArrayDeque<>();

    WorkspaceSession(DemoWorkspace data) { this(data, "You"); }
    WorkspaceSession(DemoWorkspace data, String self) {
        this.data = data;
        this.self = self;
        project = data.projects.get(0);
        for (Issue issue : data.issues) if ("You".equals(issue.assignee)) issue.assignee = self;
    }

    Project project() { return project; }
    View view() { return view; }
    Issue selected() { return selected; }
    IssueDraft draft() { return draft; }
    DocumentDraft documentDraft() { return documentDraft; }
    TaskBody documentBody() {
        return documentDraft != null ? documentDraft.body : selected == null ? new TaskBody()
            : DocumentDraft.compose(selected.description, selected.body, introduction);
    }
    boolean expanded() { return expanded; }
    String query() { return query; }
    String assignee() { return assignee; }
    String sprint() { return sprint; }
    String comment() { return comment; }
    String self() { return self; }
    String notice() { return notice; }
    boolean canUndo() { return !history.isEmpty(); }
    String undoLabel() { return history.isEmpty() ? "Nothing to undo" : history.peek().label; }
    int filterRevision() { return filterRevision; }
    int selectionRevision() { return selectionRevision; }
    List<Issue> issues() { return data.find(project, query, assignee, sprint); }
    String[] projectNames() { return data.projects.stream().map(value -> value.name).toArray(String[]::new); }

    void query(String value) { if (!query.equals(value)) { query = value; filterRevision++; } }
    void comment(String value) { comment = value; }

    void project(String name) {
        if (draft != null) return;
        Project next = data.projects.stream().filter(value -> value.name.equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown project: " + name));
        if (project == next) return;
        project = next;
        notice = "";
        clearSelection();
        filterRevision++;
    }

    void navigate(View value) {
        if (draft != null) return;
        view = value;
        clearSelection();
        filterRevision++;
    }

    void assigneeFilter(String value) { assignee = value; filterRevision++; }
    void sprintFilter(String value) { sprint = value; filterRevision++; }
    void clearFilters() { query = ""; assignee = "Everyone"; sprint = "All work"; filterRevision++; }

    void select(Issue issue) {
        if (issue.project != project) throw new IllegalArgumentException("Issue belongs to another project");
        cancelDocument();
        introduction = new NoteWidget();
        selected = issue;
        expanded = false;
        comment = "";
        selectionRevision++;
    }

    void expand() { expanded = true; }

    void clearSelection() { cancelDocument(); selected = null; expanded = false; comment = ""; selectionRevision++; }

    void createIssue() {
        createIssue(Status.OPEN);
    }

    void createIssue(Status status) {
        cancelDocument();
        draft = createDrafts.computeIfAbsent(project, key -> {
            IssueDraft value = new IssueDraft(key, null);
            value.status = status;
            if (!"Everyone".equals(assignee)) value.assignee = assignee;
            if (!"All work".equals(sprint)) value.sprint = sprint;
            return value;
        });
        draft.validation = "";
        notice = "";
    }

    void editIssue() {
        cancelDocument();
        if (selected != null) { draft = new IssueDraft(project, selected); notice = ""; }
    }

    void cancelEditor() { draft = null; }

    boolean saveDraft() {
        if (draft == null) return false;
        if (draft.title.trim().isEmpty()) { draft.validation = "Please enter a title."; return false; }
        boolean creating = draft.original == null;
        IssueState previous = creating ? null : new IssueState(draft.original);
        Issue issue = creating ? data.create(draft.project, draft.title, draft.description) : draft.original;
        issue.title = draft.title.trim();
        issue.description = draft.description;
        issue.type = draft.type;
        issue.priority = draft.priority;
        issue.assignee = draft.assignee;
        issue.sprint = draft.sprint;
        issue.body = draft.body.copy();
        if (draft.original != null) {
            issue.activity.add("You edited the issue");
            notice = "Saved " + issue.key;
        } else {
            issue.status = draft.status;
            createDrafts.remove(draft.project);
            notice = "Created " + issue.key;
        }
        draft = null;
        select(issue);
        remember(creating ? "Create " + issue.key : "Edit " + issue.key, () -> {
            if (creating) { data.issues.remove(issue); if (selected == issue) clearSelection(); }
            else previous.restore(issue);
        });
        return true;
    }

    boolean saveAndCreateAnother() {
        if (draft == null || draft.original != null) return false;
        if (!saveDraft()) return false;
        Issue created = selected;
        String createdNotice = notice;
        clearSelection();
        createIssue(created.status);
        notice = createdNotice;
        draft.type = created.type;
        draft.priority = created.priority;
        draft.assignee = created.assignee;
        draft.sprint = created.sprint;
        return true;
    }

    void changeStatus(String label) {
        if (selected == null) return;
        for (Status status : Status.values()) if (status.label.equals(label)) {
            if (selected.status != status) checkpoint(selected, "Change status");
            selected.transition(status); return;
        }
        throw new IllegalArgumentException("Unknown status: " + label);
    }

    /** Board moves do not select/open the issue or change the active view and filters. */
    void moveIssue(Issue issue, Status status) { moveIssue(issue, status, null); }

    void moveIssueBefore(Issue issue, Issue before) {
        if (issue == before) return;
        moveIssue(issue, before.status, before);
    }

    private void moveIssue(Issue issue, Status status, Issue before) {
        if (issue.project != project || !data.issues.contains(issue))
            throw new IllegalArgumentException("Issue does not belong to the active project");
        if (before != null && (before.project != project || !data.issues.contains(before)))
            throw new IllegalArgumentException("Drop target does not belong to the active project");
        List<Issue> order = new ArrayList<>(data.issues);
        IssueState previous = new IssueState(issue);
        remember("Move " + issue.key, () -> { data.issues.clear(); data.issues.addAll(order); previous.restore(issue); });
        issue.transition(status);
        data.issues.remove(issue);
        data.issues.add(before == null ? data.issues.size() : data.issues.indexOf(before), issue);
    }

    void changeAssignee(String value) {
        if (selected != null && !selected.assignee.equals(value)) {
            checkpoint(selected, "Assign " + selected.key);
            selected.assignee = value;
            selected.activity.add("Assigned to " + value);
        }
    }

    void changePriority(String value) {
        if (selected != null && !selected.priority.equals(value)) {
            checkpoint(selected, "Change priority");
            selected.priority = value;
            selected.activity.add("Priority changed to " + value);
        }
    }

    void changeType(String value) {
        if (selected != null && !selected.type.equals(value)) {
            checkpoint(selected, "Change type");
            selected.type = value;
            selected.activity.add("Type changed to " + value);
        }
    }

    void changeSprint(String value) {
        if (selected != null && !selected.sprint.equals(value)) {
            checkpoint(selected, "Change sprint");
            selected.sprint = value;
            selected.activity.add("Moved to " + value);
        }
    }

    void postComment() {
        if (selected != null && !comment.trim().isEmpty()) {
            checkpoint(selected, "Post comment");
            selected.comments.add("You: " + comment.trim());
            selected.activity.add("You added a comment");
            comment = "";
        }
    }

    /** True when Escape navigated within the workspace; false when the game screen should close. */
    boolean back() {
        if (draft != null) { cancelEditor(); return true; }
        if (documentDraft != null) { cancelDocument(); return true; }
        if (selected != null) { clearSelection(); return true; }
        return false;
    }

    void mutateBody(Runnable action) {
        if (selected == null) return;
        checkpoint(selected, "Edit task body");
        action.run();
    }
    void beginDocument(String blockId) {
        if (draft != null || selected == null) return;
        if (documentDraft == null) {
            documentSource = selected.body; documentDescription = selected.description;
            documentDraft = new DocumentDraft(documentBody());
        }
        documentDraft.select(blockId);
    }
    void cancelDocument() { documentDraft = null; documentSource = null; documentDescription = null; }
    boolean saveDocument() {
        if (documentDraft == null) return false;
        if (draft != null || selected == null || selected.body != documentSource || !selected.description.equals(documentDescription)) {
            cancelDocument(); return false;
        }
        checkpoint(selected, "Edit description");
        selected.description = "";
        selected.body = documentDraft.body.copy();
        selected.activity.add("You edited the description");
        cancelDocument();
        return true;
    }
    boolean updateText(String title, String description) {
        if (selected == null || title.trim().isEmpty()) return false;
        checkpoint(selected, "Edit task text");
        selected.title = title.trim(); selected.description = description;
        selected.activity.add("You edited the issue text");
        return true;
    }
    void openIssue(String key) {
        if (draft != null) return;
        Issue issue = data.issues.stream().filter(value -> value.key.equalsIgnoreCase(key)).findFirst().orElse(null);
        if (issue == null) { notice = "Unknown issue: " + key; return; }
        project(issue.project.name); select(issue);
    }
    Boolean issueDone(String key) {
        return data.issues.stream().filter(value -> value.key.equalsIgnoreCase(key)).map(value -> value.status == Status.DONE).findFirst().orElse(null);
    }
    void undo() {
        if (draft != null || history.isEmpty()) return;
        cancelDocument();
        Undo action = history.pop(); action.restore.run(); notice = "Undone: " + action.label;
    }
    private void checkpoint(Issue issue, String label) {
        IssueState state = new IssueState(issue);
        remember(label, () -> state.restore(issue));
    }
    private void remember(String label, Runnable restore) {
        history.push(new Undo(label, restore));
        while (history.size() > 30) history.removeLast();
    }
    private static final class Undo {
        final String label; final Runnable restore;
        Undo(String label, Runnable restore) { this.label = label; this.restore = restore; }
    }
    private static final class IssueState {
        final String title, description, type, priority, assignee, sprint;
        final Status status; final TaskBody body;
        final List<String> activity, comments;
        IssueState(Issue issue) {
            title = issue.title; description = issue.description; type = issue.type; priority = issue.priority;
            assignee = issue.assignee; sprint = issue.sprint; status = issue.status; body = issue.body.copy();
            activity = new ArrayList<>(issue.activity); comments = new ArrayList<>(issue.comments);
        }
        void restore(Issue issue) {
            issue.title = title; issue.description = description; issue.type = type; issue.priority = priority;
            issue.assignee = assignee; issue.sprint = sprint; issue.status = status; issue.body = body.copy();
            issue.activity.clear(); issue.activity.addAll(activity); issue.comments.clear(); issue.comments.addAll(comments);
        }
    }
}
