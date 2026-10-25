package mira.platform.forge1710.client.workspace;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import mira.core.jira.BodyValues;
import mira.core.jira.Model;
import mira.core.jira.Model.*;
import mira.core.jira.Workflows;
import mira.core.taskbody.*;
import mira.platform.forge1710.client.workspace.WorkspaceData.*;
import mira.platform.forge1710.jira.DatabaseCodec.Reply;

/** Projects server snapshots into stable UI objects and submits edits against their original field versions. */
final class RemoteWorkspace {
    final WorkspaceBackend backend;
    final WorkspaceSession session;
    private final WorkspaceData data;
    State state = new State();
    String workspace = "";
    boolean saving, conflict;
    private Role previousRole = Role.NONE;
    private final Map<String, Status> statusCache = new HashMap<>();
    private Entity editingComment;
    private Command failed;
    private Runnable failedAccepted;
    RemoteWorkspace(WorkspaceSession session, WorkspaceData data, WorkspaceBackend backend) { this.session = session; this.data = data; this.backend = backend; }
    void connect() { backend.listen(this::refresh); }
    Role role() { return state.role(workspace, backend.player()); }
    boolean owner() { return role() == Role.OWNER; }
    boolean writable() { return backend.ready() && !saving && role().ordinal() >= Role.EDITOR.ordinal(); }
    private void refresh(State snapshot) {
        state = snapshot;
        String previousWorkspace = workspace;
        List<Entity> workspaces = state.entities.values().stream().filter(e -> e.kind == Kind.WORKSPACE && !e.archived()).collect(Collectors.toList());
        if (workspaces.stream().noneMatch(e -> e.id.equals(workspace))) workspace = workspaces.isEmpty() ? "" : workspaces.get(0).id;
        if (!previousWorkspace.equals(workspace)) lostAccess();
        Map<String, Project> oldProjects = new HashMap<>(); for (Project p : data.projects) oldProjects.put(p.id, p);
        Map<String, Issue> oldIssues = new HashMap<>(); for (Issue issue : data.issues) oldIssues.put(issue.id, issue);
        Project previousProject = session.project(); Issue previousIssue = session.selected() == null ? session.documentIssue() : session.selected();
        if (previousProject != null) oldProjects.putIfAbsent(previousProject.id, previousProject);
        data.projects.clear(); data.issues.clear();
        for (Entity p : entities(Kind.PROJECT)) if (!p.archived()) {
            Project project = oldProjects.get(p.id);
            if (project == null) project = new Project(p.id, p.get("name"), p.get("key"));
            project.name = p.get("name"); project.source = p; data.projects.add(project);
        }
        Map<String, Project> projects = new HashMap<>(); for (Project p : data.projects) projects.put(p.id, p);
        if (previousProject != null && !projects.containsKey(previousProject.id) && role().ordinal() >= Role.EDITOR.ordinal()
            && (session.draft() != null || session.documentDraft() != null)) {
            Entity source = state.entities.get(previousProject.id);
            if (source != null && source.workspace.equals(workspace)) { previousProject.source = source; previousProject.name = source.get("name"); projects.put(previousProject.id, previousProject); }
        }
        Map<String, List<Entity>> blocks = children(ordered(entities(Kind.BLOCK))), comments = children(entities(Kind.COMMENT));
        Map<String, List<String>> activities = new HashMap<>();
        java.text.SimpleDateFormat dates = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (Activity a : state.activity) {
            Set<String> tasks = new LinkedHashSet<>();
            for (Difference difference : a.differences) {
                Entity e = state.entities.get(difference.entity); if (e == null) continue;
                if (e.kind == Kind.TASK) tasks.add(e.id); else if (!e.get("task").isEmpty()) tasks.add(e.get("task"));
            }
            String label = dates.format(new Date(a.time)) + " / " + a.name + ": " + a.summary;
            for (String task : tasks) activities.computeIfAbsent(task, id -> new ArrayList<>()).add(label);
        }
        for (Entity task : ordered(entities(Kind.TASK))) {
            Project project = projects.get(task.get("project")); if (project == null || task.archived()) continue;
            Issue issue = oldIssues.get(task.id);
            if (issue == null || issue.project != project) issue = new Issue(task.id, project, task.get("key"), task.get("title"), task.get("description"));
            issue.source = task; issue.title = task.get("title"); issue.description = task.get("description");
            issue.type = name(task.get("type")); issue.priority = task.get("priority"); issue.assignee = playerName(task.get("assignee"));
            issue.sprint = task.get("sprint").isEmpty() ? "Backlog" : name(task.get("sprint"));
            issue.status = status(Workflows.status(state, Workflows.workflow(state, project.source, task.get("type")), task.get("status")));
            issue.epic = task.get("epic").isEmpty() ? "None" : taskName(task.get("epic"));
            List<Entity> currentBlocks = blocks.getOrDefault(task.id, Collections.emptyList());
            boolean sameBody = issue.blocks.size() == currentBlocks.size();
            if (sameBody) for (int i = 0; i < currentBlocks.size(); i++) if (!issue.blocks.get(i).id.equals(currentBlocks.get(i).id) || !issue.blocks.get(i).get("content").equals(currentBlocks.get(i).get("content"))) { sameBody = false; break; }
            issue.blocks.clear(); issue.blocks.addAll(currentBlocks);
            if (!sameBody) issue.body = body(issue.blocks);
            issue.comments.clear(); issue.commentSources.clear(); issue.activity.clear();
            for (Entity comment : comments.getOrDefault(task.id, Collections.emptyList())) {
                issue.commentSources.add(comment); issue.comments.add(playerName(comment.get("author")) + ": " + comment.get("text"));
            }
            issue.activity.addAll(activities.getOrDefault(task.id, Collections.emptyList()));
            issue.location = "No location attached"; List<String> links = new ArrayList<>();
            for (TaskWidget widget : issue.body.widgets) {
                if (widget instanceof LocationWidget) { LocationWidget location = (LocationWidget) widget; issue.location = "Dimension " + location.dimension + ": " + location.x + " / " + location.y + " / " + location.z; }
                if (widget instanceof LinksWidget) links.addAll(((LinksWidget) widget).blockers);
            }
            issue.links = links.isEmpty() ? "No linked issues" : "Blocked by " + String.join(", ", links);
            data.issues.add(issue);
        }
        Project active = previousProject == null ? null : projects.get(previousProject.id);
        if (active == null && !data.projects.isEmpty()) active = data.projects.get(0);
        Issue selected = null;
        if (previousIssue != null) for (Issue i : data.issues) if (i.id.equals(previousIssue.id) && i.project == active) selected = i;
        Role currentRole = role(); boolean lostWrite = previousRole.ordinal() >= Role.EDITOR.ordinal() && currentRole.ordinal() < Role.EDITOR.ordinal();
        session.remoteSelection(active, selected, lostWrite); previousRole = currentRole;
    }
    private static Map<String, List<Entity>> children(List<Entity> entries) {
        Map<String, List<Entity>> result = new HashMap<>();
        for (Entity e : entries) if (!e.archived()) result.computeIfAbsent(e.get("task"), id -> new ArrayList<>()).add(e); return result;
    }
    List<Entity> entities(Kind kind) { return state.list(kind, workspace); }
    static List<Entity> ordered(List<Entity> values) {
        List<Entity> result = new ArrayList<>(values);
        result.sort(Comparator.comparingDouble((Entity e) -> number(e.get("rank"))).thenComparing(e -> e.id)); return result;
    }
    private static double number(String value) { return value.isEmpty() ? 0 : Double.parseDouble(value); }
    private static TaskBody body(List<Entity> blocks) { TaskBody body = new TaskBody(); for (Entity e : blocks) body.widgets.add(BodyValues.decode(e.get("content"))); return body; }
    String name(String id) { Entity e = state.entities.get(id); return e == null ? "Unknown" : e.get("name"); }
    String taskName(String id) { Entity e = state.entities.get(id); return e == null ? "Unknown issue" : e.get("key"); }
    String playerName(String id) {
        if (id.isEmpty()) return "Unassigned";
        String name = state.players.getOrDefault(id, id);
        long count = state.players.values().stream().filter(name::equalsIgnoreCase).count();
        return count > 1 ? name + " [" + id.substring(0, 8) + "]" : name;
    }
    String playerId(String value) {
        if (value.equals("Unassigned") || value.isEmpty()) return "";
        for (String id : state.players.keySet()) if (playerName(id).equals(value)) return id;
        throw new Rejected("Select a player who has joined this world");
    }
    String[] players() { return state.players.keySet().stream().map(this::playerName).sorted(String.CASE_INSENSITIVE_ORDER).toArray(String[]::new); }
    String typeId(String value) { return named(Kind.ISSUE_TYPE, value, ""); }
    private String draftType(IssueDraft d) { return d.source != null && d.type.equals(d.sourceType) ? d.source.get("type") : typeId(d.type); }
    private String draftAssignee(IssueDraft d) { return d.source != null && d.assignee.equals(d.sourceAssignee) ? d.source.get("assignee") : playerId(d.assignee); }
    private String draftSprint(IssueDraft d) { return d.source != null && d.sprint.equals(d.sourceSprint) ? d.source.get("sprint") : sprintId(d.sprint); }
    String sprintId(String value) { return value.equals("Backlog") || value.isEmpty() ? "" : named(Kind.SPRINT, value, session.project().id); }
    private String named(Kind kind, String value, String project) {
        for (Entity e : entities(kind)) if (!e.archived() && e.get("name").equals(value) && (project.isEmpty() || e.get("project").equals(project))) return e.id;
        throw new Rejected("Unknown " + kind + ": " + value);
    }
    String[] types() { return entities(Kind.ISSUE_TYPE).stream().filter(e -> !e.archived()).map(e -> e.get("name")).toArray(String[]::new); }
    String[] sprints() {
        List<String> result = new ArrayList<>(); result.add("Backlog");
        if (session.project() != null) for (Entity e : entities(Kind.SPRINT)) if (!e.archived() && e.get("project").equals(session.project().id)) result.add(e.get("name"));
        return result.toArray(new String[0]);
    }
    String[] planningSprints() {
        List<String> values = new ArrayList<>(); values.add("Backlog");
        if (session.project() != null) for (Entity e : entities(Kind.SPRINT)) if (!e.archived() && !e.get("state").equals("COMPLETED") && e.get("project").equals(session.project().id)) values.add(e.get("name"));
        return values.toArray(new String[0]);
    }
    String[] epics() {
        List<String> values = new ArrayList<>(); values.add("None");
        if (session.selected() != null) for (Issue i : data.issues) if (i.project == session.project() && i != session.selected()) values.add(i.key);
        return values.toArray(new String[0]);
    }
    void epic(String key) { safe(() -> field("epic", key.equals("None") ? "" : data.issues.stream().filter(i -> i.key.equals(key)).map(i -> i.id).findFirst().orElseThrow(() -> new Rejected("Unknown issue")))); }
    Boolean done(String key) {
        for (Entity e : entities(Kind.TASK)) if (e.get("key").equalsIgnoreCase(key)) return Workflows.done(state, e); return null;
    }
    Status initial(String type) {
        Entity workflow = Workflows.workflow(state, session.project().source, typeId(type));
        return status(Workflows.status(state, workflow, workflow.get("initial")));
    }
    private Status status(Entity node) {
        String cacheKey = node.get("logical") + "/" + node.get("name") + "/" + node.get("category");
        return statusCache.computeIfAbsent(cacheKey, key -> new Status(node.get("logical"), node.get("name"), node.get("category"),
            node.get("category").equals("DONE") ? 0xFF286628 : node.get("category").equals("TODO") ? 0xFF404040 : 0xFF2B579A));
    }
    Status[] statuses() {
        if (session.project() == null) return new Status[0];
        Map<String, Status> result = new LinkedHashMap<>();
        for (Entity type : entities(Kind.ISSUE_TYPE)) {
            Entity flow = Workflows.workflow(state, session.project().source, type.id);
            for (Entity node : Workflows.nodes(state, flow, false)) result.putIfAbsent(node.get("logical"), status(node));
        }
        return result.values().toArray(new Status[0]);
    }
    List<BoardColumn> columns() {
        List<BoardColumn> result = new ArrayList<>(); if (session.project() == null) return result;
        List<Status> available = Arrays.asList(statuses()); Set<String> assigned = new HashSet<>();
        for (Entity column : ordered(entities(Kind.COLUMN))) if (!column.archived() && column.get("project").equals(session.project().id)) {
            List<Status> statuses = new ArrayList<>();
            for (String id : Workflows.ids(column.get("statuses"))) for (Status status : available) if (status.id.equals(id) && assigned.add(id)) statuses.add(status);
            result.add(new BoardColumn(column.id, column.get("name"), statuses));
        }
        List<Status> unmapped = available.stream().filter(s -> !assigned.contains(s.id)).collect(Collectors.toList());
        if (!unmapped.isEmpty()) result.add(new BoardColumn("unmapped", "Unmapped", unmapped));
        return result;
    }
    Command command() { Command c = new Command(); c.workspace = workspace; return c; }
    void submit(Command c, Runnable accepted) {
        if (saving) return;
        saving = true; conflict = false; session.remoteNotice("Saving to world...");
        backend.submit(c, reply -> {
            saving = false;
            if (reply.result.equals("OK")) {
                failed = null; failedAccepted = null;
                if (state.role(c.workspace, backend.player()) == Role.NONE) session.remoteNotice("Saved; workspace access has since changed");
                else { session.remoteNotice("Saved to world"); accepted.run(); }
            }
            else { conflict = reply.result.equals("CONFLICT"); if (conflict) { failed = c; failedAccepted = accepted; } session.remoteNotice(reply.message); }
        });
    }
    void submit(Command c) { submit(c, () -> {}); }
    private void safe(Runnable action) { try { action.run(); } catch (Rejected e) { session.remoteNotice(e.getMessage()); } }
    void labelField(String field, String value) { safe(() -> field(field, field.equals("assignee") ? playerId(value) : sprintId(value))); }
    void field(String key, String value) {
        if (!writable() || session.selected() == null) return;
        Entity source = session.selected().source; if (source.get(key).equals(value)) return;
        Command c = command(); c.patches.add(Patch.edit(source).set(source, key, value)); submit(c);
    }
    void changeType(String value) { safe(() -> {
        if (!writable() || session.selected() == null) return;
        Entity source = session.selected().source; String type = typeId(value); if (source.get("type").equals(type)) return;
        Entity workflow = Workflows.workflow(state, session.project().source, type);
        boolean retains = Workflows.nodes(state, workflow, false).stream().anyMatch(e -> e.get("logical").equals(source.get("status")));
        Command c = command(); Patch patch = Patch.edit(source).set(source, "type", type);
        if (!retains) patch.set(source, "status", workflow.get("initial")); c.patches.add(patch); submit(c);
    }); }
    void status(String label) {
        if (!writable() || session.selected() == null) return;
        for (Entity edge : transitions(session.selected())) if (transitionLabel(session.selected(), edge).equals(label)) {
            moveWithTransition(session.selected(), edge, null); return;
        }
        session.remoteNotice("This workflow does not allow that transition");
    }
    List<Entity> transitions(Issue issue) { return Workflows.transitions(state, issue.source, role()); }
    private String transitionLabel(Issue issue, Entity edge) { return edge.get("name") + " -> " + statusLabel(issue, edge.get("to")) + " [" + edge.id.substring(0, 4) + "]"; }
    String[] transitionLabels(Issue issue) { return transitions(issue).stream().map(e -> transitionLabel(issue, e)).toArray(String[]::new); }
    String statusLabel(Issue issue, String id) { return Workflows.status(state, Workflows.workflow(state, issue.project.source, issue.source.get("type")), id).get("name"); }
    void move(Issue issue, Status status, Issue before) {
        if (!writable()) return;
        if (issue.status.id.equals(status.id)) { moveWithTransition(issue, null, before); return; }
        List<Entity> choices = transitions(issue).stream().filter(e -> e.get("to").equals(status.id)).collect(Collectors.toList());
        if (choices.size() != 1) { session.remoteNotice(choices.isEmpty() ? "No allowed transition to this status" : "Choose the transition from issue details"); return; }
        moveWithTransition(issue, choices.get(0), before);
    }
    void moveWithTransition(Issue issue, Entity edge, Issue before) {
        if (!writable()) return;
        Entity source = issue.source; Command c = command(); Patch patch = Patch.edit(source);
        if (edge != null) { patch.set(source, "status", edge.get("to")); c.arguments.put("transition." + source.id, edge.get("logical")); }
        double rank = data.issues.stream().filter(i -> i.project == issue.project).mapToDouble(i -> number(i.source.get("rank"))).max().orElse(0) + 1024;
        if (before != null) {
            double next = number(before.source.get("rank"));
            double previous = data.issues.stream().filter(i -> i != issue && number(i.source.get("rank")) < next).mapToDouble(i -> number(i.source.get("rank"))).max().orElse(next - 2048);
            rank = previous + (next - previous) / 2;
        }
        patch.set(source, "rank", Double.toString(rank)); c.patches.add(patch); submit(c);
    }
    boolean saveDraft(IssueDraft draft, boolean another) {
        if (!writable() || draft == null || another && draft.original != null) return false;
        if (draft.title.trim().isEmpty()) { draft.validation = "Please enter a title."; return false; }
        safe(() -> {
            Command c = command(); Patch task = new Patch(draft.id, Kind.TASK, workspace);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("title", draft.title.trim()); values.put("description", draft.description); values.put("type", draftType(draft));
            values.put("priority", draft.priority); values.put("assignee", draftAssignee(draft)); values.put("sprint", draftSprint(draft));
            if (draft.original == null) {
                values.put("project", draft.project.id); values.put("rank", Double.toString(entities(Kind.TASK).stream().filter(e -> e.get("project").equals(draft.project.id)).mapToDouble(e -> number(e.get("rank"))).max().orElse(-1024) + 1024)); values.put("archived", "false");
            }
            for (Map.Entry<String, String> field : values.entrySet()) if (draft.source == null || !draft.source.get(field.getKey()).equals(field.getValue()))
                task.set(field.getKey(), field.getValue(), draft.source == null ? 0 : draft.source.version(field.getKey()));
            if (draft.source != null && !draft.source.get("type").equals(values.get("type"))) {
                Entity workflow = Workflows.workflow(state, draft.project.source, values.get("type"));
                if (Workflows.nodes(state, workflow, false).stream().noneMatch(n -> n.get("logical").equals(draft.source.get("status")))) task.set(draft.source, "status", workflow.get("initial"));
            }
            if (!task.values.isEmpty()) c.patches.add(task);
            appendBody(c, draft.id, draft.body, draft.blocks);
            submit(c, () -> session.completeRemoteDraft(draft, data.issues.stream().filter(i -> i.id.equals(draft.id)).findFirst().orElse(null), another));
        });
        return false; // The form closes only after the server acknowledges the durable transaction.
    }
    private static void appendBody(Command c, String task, TaskBody body, List<Entity> baseline) {
        Map<String, Entity> old = new LinkedHashMap<>(); for (Entity e : baseline) old.put(e.id, e);
        Set<String> present = new HashSet<>(); int index = 0;
        for (TaskWidget widget : body.widgets) {
            if (!present.add(widget.id)) throw new Rejected("A block occurs more than once");
            Entity source = old.get(widget.id); Patch patch = new Patch(widget.id, Kind.BLOCK, c.workspace);
            String content = BodyValues.encode(widget);
            if (source == null) {
                patch.set("task", task, 0).set("content", content, 0).set("rank", Double.toString(index * 1024.0), 0).set("archived", "false", 0);
            } else {
                if (!source.get("content").equals(content)) patch.set(source, "content", content);
                if (index >= baseline.size() || !baseline.get(index).id.equals(widget.id)) patch.set(source, "rank", Double.toString(index * 1024.0));
                patch.expected.put("archived", source.version("archived"));
            }
            if (!patch.values.isEmpty()) c.patches.add(patch); index++;
        }
        for (Entity source : baseline) if (!present.contains(source.id)) {
            Patch patch = Patch.edit(source).set(source, "archived", "true"); patch.expected.putAll(source.versions); c.patches.add(patch);
        }
    }
    boolean saveDocument(DocumentDraft draft, String originalDescription, long descriptionVersion, List<Entity> baseline) {
        if (!writable() || draft == null || session.selected() == null) return false;
        safe(() -> {
            Entity task = session.selected().source; Command c = command();
            if (!originalDescription.isEmpty()) c.patches.add(Patch.edit(task).set("description", "", descriptionVersion));
            appendBody(c, task.id, draft.body, new ArrayList<>(baseline));
            submit(c, () -> { if (session.documentDraft() == draft) session.cancelDocument(); });
        });
        return false;
    }
    void mutateBody(Runnable action) {
        if (!writable() || session.selected() == null) return;
        mutateBody(session.selected().body, new ArrayList<>(session.selected().blocks), action);
    }
    void mutateBody(TaskBody observed, List<Entity> baseline, Runnable action) {
        if (!writable() || session.selected() == null) return;
        Issue issue = session.selected(); TaskBody original = observed.copy();
        action.run(); TaskBody desired = observed.copy(); if (issue.body == observed) issue.body = original;
        safe(() -> { Command c = command(); appendBody(c, issue.id, desired, baseline); submit(c); });
    }
    void comment(String text) {
        if (!writable() || session.selected() == null || text.trim().isEmpty()) return;
        Command c = command(); Entity edited = editingComment;
        if (edited != null && edited.get("task").equals(session.selected().id)) c.patches.add(Patch.edit(edited).set(edited, "text", text.trim()));
        else { Patch p = new Patch(Model.id(), Kind.COMMENT, workspace); p.set("task", session.selected().id, 0).set("text", text.trim(), 0).set("archived", "false", 0); c.patches.add(p); }
        submit(c, () -> { session.comment(""); editingComment = null; });
    }
    void editComment(Entity source) { editingComment = source.copy(); session.comment(source.get("text")); session.remoteNotice("Editing comment; Post saves the edit"); }
    boolean editingComment() { return editingComment != null; }
    void cancelCommentEdit() { editingComment = null; }
    void lostAccess() { editingComment = null; failed = null; failedAccepted = null; conflict = false; }
    void archive(Entity source, boolean archived) {
        if (saving || !backend.ready() || state.role(source.workspace, backend.player()).ordinal() < Role.EDITOR.ordinal()) return;
        Command c = command(); c.workspace = source.workspace;
        Patch p = Patch.edit(source).set(source, "archived", Boolean.toString(archived)); p.expected.putAll(source.versions); c.patches.add(p); submit(c);
    }
    Activity undoAction() {
        for (int i = state.activity.size() - 1; i >= 0; i--) { Activity a = state.activity.get(i);
            if (a.workspace.equals(workspace) && a.actor.equals(backend.player()) && a.undoable && a.undoneBy.isEmpty()) return a; }
        return null;
    }
    void undo() {
        if (!writable() || session.draft() != null || undoAction() == null) return;
        Command c = command(); c.operation = Operation.UNDO; c.target = undoAction().id; submit(c);
    }
    void switchWorkspace(String id) {
        if (saving || session.draft() != null || session.documentDraft() != null || id.equals(workspace)) return;
        workspace = id; session.remoteSelection(null, null, true); previousRole = Role.NONE; refresh(state);
    }
    void discardConflict() { session.cancelEditor(); session.cancelDocument(); session.comment(""); editingComment = null; failed = null; failedAccepted = null; conflict = false; session.remoteNotice("Showing the latest saved version"); }
    void rebaseConflict() { safe(this::rebase); }
    private void rebase() {
        IssueDraft draft = session.draft();
        if (draft == null && session.documentDraft() == null && editingComment == null && failed != null && failed.operation == Operation.EDIT) {
            Command retry = new Command(); retry.workspace = failed.workspace; retry.arguments.putAll(failed.arguments);
            for (Patch original : failed.patches) {
                Entity latest = state.entities.get(original.id); Patch p = new Patch(original.id, original.kind, original.workspace); p.values.putAll(original.values);
                for (String field : original.expected.keySet()) p.expected.put(field, latest == null ? 0L : latest.version(field)); retry.patches.add(p);
            }
            Runnable accepted = failedAccepted; submit(retry, accepted == null ? () -> {} : accepted); return;
        }
        if (editingComment != null) {
            Entity current = state.entities.get(editingComment.id);
            if (current == null || current.archived()) throw new Rejected("Restore the comment before reapplying this edit");
            editingComment = current.copy();
        }
        if (draft != null && draft.original != null) {
            Entity latest = state.entities.get(draft.id); if (latest == null || latest.archived()) { session.remoteNotice("Restore the issue before reapplying this draft"); return; }
            Entity old = draft.source;
            if (draft.title.trim().equals(old.get("title"))) draft.title = latest.get("title");
            if (draft.description.equals(old.get("description"))) draft.description = latest.get("description");
            if (draftType(draft).equals(old.get("type"))) draft.type = name(latest.get("type"));
            if (draft.priority.equals(old.get("priority"))) draft.priority = latest.get("priority");
            if (draftAssignee(draft).equals(old.get("assignee"))) draft.assignee = playerName(latest.get("assignee"));
            if (draftSprint(draft).equals(old.get("sprint"))) draft.sprint = latest.get("sprint").isEmpty() ? "Backlog" : name(latest.get("sprint"));
            rebaseBody(draft.body, draft.blocks, draft.original.blocks); draft.source = latest.copy();
            draft.sourceType = name(latest.get("type")); draft.sourceAssignee = playerName(latest.get("assignee")); draft.sourceSprint = latest.get("sprint").isEmpty() ? "Backlog" : name(latest.get("sprint"));
        }
        if (session.documentDraft() != null && session.selected() != null) { rebaseBody(session.documentDraft().body, session.documentBlocks, session.selected().blocks); session.rebaseDocumentVersion(); }
        conflict = false; session.remoteNotice("Draft rebased. Review it, then Save to apply your edits.");
    }
    private static void rebaseBody(TaskBody draft, List<Entity> baseline, List<Entity> latest) {
        Map<String, Entity> before = new HashMap<>(), after = new HashMap<>();
        for (Entity e : baseline) before.put(e.id, e); for (Entity e : latest) after.put(e.id, e);
        List<TaskWidget> merged = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (TaskWidget widget : draft.widgets) {
            seen.add(widget.id); Entity old = before.get(widget.id), current = after.get(widget.id);
            boolean changed = old == null || !old.get("content").equals(BodyValues.encode(widget));
            if (changed) merged.add(old != null && current == null ? BodyValues.withNewIdentity(widget) : widget);
            else if (current != null) merged.add(BodyValues.decode(current.get("content")));
        }
        for (Entity e : latest) if (!before.containsKey(e.id) && seen.add(e.id)) merged.add(BodyValues.decode(e.get("content")));
        draft.widgets.clear(); draft.widgets.addAll(merged); baseline.clear(); for (Entity e : latest) baseline.add(e.copy());
    }
}
