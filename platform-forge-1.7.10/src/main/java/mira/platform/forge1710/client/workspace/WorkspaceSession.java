package mira.platform.forge1710.client.workspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Arrays;
import mira.core.jira.Model;
import mira.core.taskbody.TaskBody;
import mira.core.taskbody.NoteWidget;
import mira.platform.forge1710.client.workspace.WorkspaceData.Issue;
import mira.platform.forge1710.client.workspace.WorkspaceData.Project;
import mira.platform.forge1710.client.workspace.WorkspaceData.Status;

/** Client-session interactions, independent of Minecraft rendering and input. */
final class WorkspaceSession {
    enum View {
        BOARD("Board"), BACKLOG("Backlog"), ISSUES("Issues");
        final String label;
        View(String label) { this.label = label; }
    }

    private final WorkspaceData data;
    final RemoteWorkspace remote;
    private final String self;
    private final Map<Project, IssueDraft> createDrafts = new HashMap<>();
    private Project project;
    private View view = View.BOARD;
    private Issue selected;
    private IssueDraft draft;
    private DocumentDraft documentDraft;
    private Issue documentIssue;
    private TaskBody documentSource;
    private String documentDescription;
    private long documentDescriptionVersion;
    private NoteWidget introduction = new NoteWidget();
    private boolean expanded;
    private String query = "", assignee = "Everyone", sprint = "All work", comment = "";
    private int filterRevision;
    private int selectionRevision;
    private String notice = "";
    private final Deque<Undo> history = new ArrayDeque<>();
    final List<Model.Entity> documentBlocks = new ArrayList<>();

    WorkspaceSession(WorkspaceData data) { this(data, "You"); }
    WorkspaceSession(WorkspaceData data, String self) { this(data, self, null); }
    WorkspaceSession(WorkspaceData data, String self, WorkspaceBackend backend) {
        this.data = data;
        this.self = self;
        project = data.projects.isEmpty() ? null : data.projects.get(0);
        if (backend == null) for (Issue issue : data.issues) if ("You".equals(issue.assignee)) issue.assignee = self;
        remote = backend == null ? null : new RemoteWorkspace(this, data, backend);
        if (remote != null) remote.connect();
    }

    Project project() { return project; }
    View view() { return view; }
    Issue selected() { return selected; }
    IssueDraft draft() { return draft; }
    DocumentDraft documentDraft() { return documentDraft; }
    Issue documentIssue() { return documentIssue; }
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
    boolean canUndo() { return remote == null ? !history.isEmpty() : remote.undoAction() != null && writable(); }
    String undoLabel() { return remote == null ? history.isEmpty() ? "Nothing to undo" : history.peek().label : "Undo your last saved action"; }
    boolean writable() { return remote == null || remote.writable(); }
    boolean saving() { return remote != null && remote.saving; }
    Status[] statuses() { return remote == null ? Status.values() : remote.statuses(); }
    String[] types() { return remote == null ? WorkspaceData.TYPES : remote.types(); }
    String[] sprints() { return remote == null ? WorkspaceData.SPRINTS : remote.sprints(); }
    String[] planningSprints() { return remote == null ? sprints() : remote.planningSprints(); }
    String[] sprintFilters() { List<String> values = new ArrayList<>(); values.add("All work"); values.addAll(Arrays.asList(sprints())); return values.toArray(new String[0]); }
    List<WorkspaceData.BoardColumn> columns() {
        if (remote != null) return remote.columns();
        List<WorkspaceData.BoardColumn> result = new ArrayList<>();
        for (Status s : statuses()) result.add(new WorkspaceData.BoardColumn(s.id, s.label, Arrays.asList(s))); return result;
    }
    void remoteNotice(String value) { notice = value; }
    void remoteSelection(Project next, Issue issue, boolean lostWrite) {
        if (lostWrite) { draft = null; createDrafts.clear(); cancelDocument(); comment = ""; history.clear(); if (remote != null) remote.lostAccess(); }
        if (project != next) { project = next; draft = null; createDrafts.clear(); cancelDocument(); selected = null; filterRevision++; }
        if (selected != issue) { selected = issue; selectionRevision++; comment = ""; if (remote != null) remote.cancelCommentEdit(); }
    }
    void completeRemoteDraft(IssueDraft saved, Issue issue, boolean another) {
        if (draft != saved) return;
        draft = null; createDrafts.remove(saved.project); if (issue != null) select(issue);
        if (another) { clearSelection(); createIssue(); if (draft != null) { draftType(saved.type); draft.priority = saved.priority; draft.assignee = saved.assignee; draft.sprint = saved.sprint; } }
    }
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
        if (remote != null) remote.cancelCommentEdit();
        expanded = false;
        comment = "";
        selectionRevision++;
    }

    void expand() { expanded = true; }

    void clearSelection() { cancelDocument(); selected = null; expanded = false; comment = ""; if (remote != null) remote.cancelCommentEdit(); selectionRevision++; }

    void createIssue() {
        createIssue(Status.OPEN);
    }

    void createIssue(Status status) {
        if (!writable() || project == null) return;
        cancelDocument();
        draft = createDrafts.computeIfAbsent(project, key -> {
            IssueDraft value = new IssueDraft(key, null);
            if (remote != null && !Arrays.asList(types()).contains(value.type)) value.type = types()[0];
            value.status = remote == null ? status : remote.initial(value.type);
            if (!"Everyone".equals(assignee)) value.assignee = assignee;
            if (!"All work".equals(sprint)) value.sprint = sprint;
            return value;
        });
        draft.validation = "";
        notice = "";
    }

    void editIssue() {
        if (!writable()) return;
        cancelDocument();
        if (selected != null) { draft = new IssueDraft(project, selected); notice = ""; }
    }

    void cancelEditor() { draft = null; }
    void draftType(String value) {
        if (draft == null) return; draft.type = value;
        if (remote != null && draft.original == null) try { draft.status = remote.initial(value); }
        catch (Model.Rejected e) { notice = e.getMessage(); }
    }

    boolean saveDraft() {
        if (remote != null) return remote.saveDraft(draft, false);
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
        if (remote != null) return remote.saveDraft(draft, true);
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
        if (remote != null) { remote.status(label); return; }
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
        if (remote != null) { remote.move(issue, status, before); return; }
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
        if (remote != null) { remote.labelField("assignee", value); return; }
        if (selected != null && !selected.assignee.equals(value)) {
            checkpoint(selected, "Assign " + selected.key);
            selected.assignee = value;
            selected.activity.add("Assigned to " + value);
        }
    }

    void changePriority(String value) {
        if (remote != null) { remote.field("priority", value); return; }
        if (selected != null && !selected.priority.equals(value)) {
            checkpoint(selected, "Change priority");
            selected.priority = value;
            selected.activity.add("Priority changed to " + value);
        }
    }

    void changeType(String value) {
        if (remote != null) { remote.changeType(value); return; }
        if (selected != null && !selected.type.equals(value)) {
            checkpoint(selected, "Change type");
            selected.type = value;
            selected.activity.add("Type changed to " + value);
        }
    }

    void changeSprint(String value) {
        if (remote != null) { remote.labelField("sprint", value); return; }
        if (selected != null && !selected.sprint.equals(value)) {
            checkpoint(selected, "Change sprint");
            selected.sprint = value;
            selected.activity.add("Moved to " + value);
        }
    }

    void postComment() {
        if (remote != null) { remote.comment(comment); return; }
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
        if (remote != null) { remote.mutateBody(action); return; }
        if (selected == null) return;
        checkpoint(selected, "Edit task body");
        action.run();
    }
    void mutateBody(Issue observedIssue, TaskBody observed, List<Model.Entity> blocks, Runnable action) {
        if (remote == null) { mutateBody(action); return; }
        if (selected != observedIssue) return;
        remote.mutateBody(observed, blocks, action);
    }
    void beginDocument(String blockId) {
        if (!writable()) return;
        if (draft != null || selected == null) return;
        if (documentDraft == null) {
            documentIssue = selected;
            documentSource = selected.body; documentDescription = selected.description;
            documentDescriptionVersion = selected.source == null ? 0 : selected.source.version("description");
            documentBlocks.clear(); for (Model.Entity block : selected.blocks) documentBlocks.add(block.copy());
            documentDraft = new DocumentDraft(documentBody());
        }
        documentDraft.select(blockId);
    }
    void cancelDocument() { documentDraft = null; documentIssue = null; documentSource = null; documentDescription = null; documentBlocks.clear(); }
    void rebaseDocumentVersion() { if (selected != null && selected.source != null) { documentDescription = selected.description; documentDescriptionVersion = selected.source.version("description"); } }
    boolean saveDocument() {
        if (remote != null) return remote.saveDocument(documentDraft, documentDescription, documentDescriptionVersion, documentBlocks);
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
        if (remote != null) { if (title.trim().isEmpty()) return false; remote.field("title", title.trim()); return true; }
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
    void openTask(String id) {
        if (draft != null || documentDraft != null || saving()) { notice = "Finish the current edit before opening another task"; return; }
        if (remote == null) return;
        Model.Entity task = remote.state.require(id, Model.Kind.TASK);
        remote.switchWorkspace(task.workspace);
        Issue issue = data.issues.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
        if (issue == null) { notice = "Task is unavailable or archived"; return; }
        project(issue.project.name); select(issue);
    }
    Boolean issueDone(String key) {
        if (remote != null) return remote.done(key);
        return data.issues.stream().filter(value -> value.key.equalsIgnoreCase(key)).map(value -> value.status.done()).findFirst().orElse(null);
    }
    void undo() {
        if (remote != null) { remote.undo(); return; }
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
