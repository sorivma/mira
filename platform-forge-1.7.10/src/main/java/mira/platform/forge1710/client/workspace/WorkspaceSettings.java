package mira.platform.forge1710.client.workspace;

import java.util.*;
import mira.core.jira.Model;
import mira.core.jira.Model.*;
import mira.core.jira.Workflows;

/** Administration actions and local form state, separate from rendering. */
final class WorkspaceSettings {
    static final String[] TABS = {"Workspaces", "Access", "Projects", "Sprints", "Types", "Workflows", "Schemes", "Board", "Archives", "Activity"};
    final WorkspaceSession session;
    String tab = "Workspaces", name = "", key = "", player = "", role = "VIEWER", destination = "Backlog";
    String graphName = "", category = "TODO", x = "0", y = "0", required = "", transitionRole = "EDITOR";
    String mappingType = "*", mappingWorkflow = "", projectScheme = "";
    String connectFrom = "*", connectTo = "";
    String error = "", query = "", archiveKind = "All items", activityActor = "Everyone";
    boolean creating, transferReview;
    private Entity copySource;
    private String returnTo = "", observedProject = "";
    Entity selected, graph;
    final Set<String> columnStatuses = new LinkedHashSet<>();
    private final List<Entity> columnSources = new ArrayList<>();
    final Map<String, String> migrations = new LinkedHashMap<>();
    private String observedWorkspace = "";
    private Role observedRole = Role.NONE;
    private State preview;
    private long previewRevision = -1;
    private String previewKey = "";
    WorkspaceSettings(WorkspaceSession session) {
        this.session = session;
        remote().backend.listen(state -> observe());
    }
    void observe() {
        Role current = remote().role();
        if (!observedWorkspace.equals(remote().workspace) || current.ordinal() < observedRole.ordinal()) {
            tab("Workspaces"); player = graphName = required = ""; mappingType = "*"; mappingWorkflow = "";
        }
        observedWorkspace = remote().workspace; observedRole = current;
        String project = session.project() == null ? "" : session.project().id;
        if (!observedProject.equals(project) && (tab.equals("Sprints") || tab.equals("Board"))) tab(tab);
        observedProject = project;
        if (selected != null && liveSelected() == null) tab(tab);
        if (selected != null && liveSelected() != null) { selected.fields.put("archived", liveSelected().get("archived")); selected.versions.put("archived", liveSelected().version("archived")); }
        if (graph != null) {
            Entity currentGraph = remote().state.entities.get(graph.id);
            if (currentGraph == null || currentGraph.archived()) newStatus();
        }
    }
    RemoteWorkspace remote() { return session.remote; }
    void tab(String value) {
        tab = value; selected = graph = copySource = null; name = key = query = error = "";
        creating = transferReview = false; columnStatuses.clear(); migrations.clear();
        archiveKind = "All items"; activityActor = "Everyone";
        if (value.equals("Workspaces")) {
            Entity current = remote().state.entities.get(remote().workspace);
            if (current != null) select(current);
        } else {
            Kind kind = kind();
            if (kind != null) for (Entity e : candidates(kind)) if (!e.archived()) { select(e); break; }
        }
    }
    Kind kind() {
        switch (tab) {
            case "Workspaces": return Kind.WORKSPACE;
            case "Projects": return Kind.PROJECT;
            case "Sprints": return Kind.SPRINT;
            case "Types": return Kind.ISSUE_TYPE;
            case "Workflows": return Kind.WORKFLOW;
            case "Schemes": return Kind.SCHEME;
            case "Board": return Kind.COLUMN;
            default: return null;
        }
    }
    List<Entity> candidates(Kind kind) {
        List<Entity> result = new ArrayList<>();
        Collection<Entity> values = kind == Kind.WORKSPACE ? remote().state.entities.values() : list(kind);
        for (Entity e : values) if (e.kind == kind && ((kind != Kind.SPRINT && kind != Kind.COLUMN)
            || session.project() != null && e.get("project").equals(session.project().id))) result.add(e);
        if (kind == Kind.COLUMN) return RemoteWorkspace.ordered(result);
        result.sort(Comparator.comparing(Entity::archived).thenComparing(e -> e.get("name"), String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    void beginCreate(boolean copy) {
        returnTo = selected == null ? "" : selected.id; copySource = copy ? selected : null;
        name = copySource == null ? "" : copySource.get("name") + " copy";
        selected = graph = null; key = error = ""; creating = true; columnStatuses.clear(); migrations.clear();
    }
    void cancelCreate() {
        Entity previous = remote().state.entities.get(returnTo);
        if (previous == null) tab(tab); else select(previous);
    }
    void saveForm() {
        if (kind() == null) return;
        if (creating) { if (kind() == Kind.WORKSPACE) createWorkspace(); else create(kind(), copySource != null); }
        else saveName();
    }
    void reload() { if (liveSelected() != null) select(liveSelected()); else tab(tab); }
    boolean dirty() {
        if (creating) return !name.isEmpty() || !key.isEmpty();
        return selected != null && (!name.trim().equals(selected.get("name")) || selected.kind == Kind.COLUMN
            && (!key.equals(selected.get("rank")) || !columnStatuses.equals(new LinkedHashSet<>(Workflows.ids(selected.get("statuses"))))));
    }
    void changeName(String value) { name = value; error = ""; }
    void changeKey(String value) { key = value; error = ""; }
    void choosePlayer(String value) {
        player = value; transferReview = false; error = "";
        String id = remote().playerId(value);
        for (Entity e : list(Kind.MEMBER)) if (!e.archived() && e.get("player").equals(id)) { role = e.get("role"); return; }
        role = "VIEWER";
    }
    String selectedPlayerId() {
        try { return remote().playerId(player); } catch (Rejected unavailable) { return ""; }
    }
    void chooseProject(String value) { session.project(value); observe(); }
    void openWorkspace(Entity value) {
        if (!value.archived()) remote().switchWorkspace(value.id);
        observe(); select(value);
    }
    void archive(Entity value) { remote().archive(value, !value.archived()); }
    void chooseScheme(String id) { projectScheme = id; migrations.clear(); }
    void toggleStatus(String id) { if (!columnStatuses.remove(id)) columnStatuses.add(id); }
    void selectMapping(Entity mapping) { mappingType = mapping.get("type"); mappingWorkflow = mapping.get("workflow"); }
    void toggleRequired(String field) {
        Set<String> fields = new LinkedHashSet<>(Workflows.ids(required));
        if (!fields.remove(field)) fields.add(field); required = String.join(",", fields);
    }
    void select(Entity value) {
        if (value == null) return;
        creating = false; copySource = null; error = ""; transferReview = false;
        selected = value.copy(); graph = null; name = value.get("name"); key = value.kind == Kind.COLUMN ? value.get("rank") : value.get("key");
        graphName = required = ""; category = "TODO"; x = y = "0"; transitionRole = "EDITOR";
        connectFrom = "*"; connectTo = "";
        mappingType = "*"; mappingWorkflow = ""; destination = "Backlog";
        projectScheme = value.kind == Kind.PROJECT ? value.get("scheme") : "";
        columnStatuses.clear(); columnStatuses.addAll(Workflows.ids(value.get("statuses"))); migrations.clear();
        columnSources.clear();
        if (value.kind == Kind.COLUMN) for (Entity column : list(Kind.COLUMN)) if (column.get("project").equals(value.get("project"))) columnSources.add(column.copy());
    }
    List<Entity> list(Kind kind) { return remote().entities(kind); }
    static String label(Entity e) { return (e.get("name").isEmpty() ? e.get("key") : e.get("name")) + (e.archived() ? " (archived)" : ""); }
    static Map<String, Entity> choices(Collection<Entity> values) {
        Map<String, Entity> result = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Entity e : values) counts.merge(label(e), 1, Integer::sum);
        for (Entity e : values) {
            String label = label(e);
            String choice = counts.get(label) > 1 ? label + " [" + e.id.substring(0, 8) + "]" : label;
            if (result.containsKey(choice)) choice = label + " [" + e.id + "]";
            result.put(choice, e);
        }
        return result;
    }
    static String choiceLabel(Map<String, Entity> choices, String id, String empty) {
        for (Map.Entry<String, Entity> choice : choices.entrySet()) if (choice.getValue().id.equals(id)) return choice.getKey();
        return empty;
    }
    void run(Runnable action) { try { error = ""; action.run(); } catch (Rejected | NumberFormatException e) {
        error = e.getMessage() == null ? "Enter a valid number before saving." : e.getMessage(); session.remoteNotice(error);
    } }
    private void requireName() { if (name.trim().isEmpty()) throw new Rejected("Enter a name before saving."); }
    void createWorkspace() { run(() -> {
        requireName();
        Command c = new Command(); c.operation = Operation.CREATE_WORKSPACE; c.workspace = Model.id(); c.arguments.put("name", name.trim());
        remote().submit(c, () -> { remote().switchWorkspace(c.workspace); observe(); tab("Workspaces"); });
    }); }
    void create(Kind kind, boolean copy) { run(() -> {
        requireName();
        if (kind == Kind.PROJECT && !key.trim().toUpperCase(Locale.ROOT).matches("[A-Z][A-Z0-9]{1,11}"))
            throw new Rejected("Use 2-12 letters or digits for the project key, starting with a letter.");
        RemoteWorkspace r = remote(); Command c = r.command(); c.target = Model.id(); c.arguments.put("name", name.trim());
        if (kind == Kind.PROJECT) { c.operation = Operation.CREATE_PROJECT; c.arguments.put("key", key.trim().toUpperCase(Locale.ROOT)); }
        else if (kind == Kind.WORKFLOW || kind == Kind.SCHEME) {
            c.operation = kind == Kind.WORKFLOW ? Operation.CREATE_WORKFLOW : Operation.CREATE_SCHEME;
            Entity source = copySource == null ? selected : copySource;
            if (copy && source != null) c.arguments.put("source", source.id);
        } else {
            Patch p = new Patch(c.target, kind, r.workspace); p.set("name", name.trim(), 0).set("archived", "false", 0);
            if (kind == Kind.SPRINT) p.set("project", session.project().id, 0).set("state", "PLANNED", 0);
            if (kind == Kind.COLUMN) p.set("project", session.project().id, 0).set("statuses", "", 0).set("rank", Double.toString(list(Kind.COLUMN).size() * 1024.0), 0);
            c.patches.add(p);
        }
        r.submit(c, () -> { Entity created = r.state.entities.get(c.target); if (created != null) select(created); });
    }); }
    void saveName() { if (selected == null) return; run(() -> {
        requireName();
        Command c = remote().command(); Patch p = Patch.edit(selected).set(selected, "name", name.trim());
        if (selected.kind == Kind.COLUMN) {
            try { if (!Double.isFinite(Double.parseDouble(key))) throw new NumberFormatException(); }
            catch (NumberFormatException invalid) { throw new Rejected("Enter a number for column order, such as 1024."); }
            p.set(selected, "rank", key); p.set(selected, "statuses", String.join(",", columnStatuses));
            // Moving a status between columns is one transaction, never a temporarily duplicated mapping.
            for (Entity column : columnSources) if (!column.archived() && !column.id.equals(selected.id) && column.get("project").equals(selected.get("project"))) {
                List<String> ids = Workflows.ids(column.get("statuses")); if (ids.removeIf(columnStatuses::contains)) c.patches.add(Patch.edit(column).set(column, "statuses", String.join(",", ids)));
            }
        }
        String target = selected.id; c.patches.add(p); remote().submit(c, () -> select(remote().state.entities.get(target)));
    }); }
    void archiveSelected() { if (selected != null) archive(selected); }
    void grant(boolean remove) { run(() -> {
        RemoteWorkspace r = remote(); String id = r.playerId(player);
        if (id.isEmpty()) throw new Rejected("Select a player");
        if (r.state.require(r.workspace, Kind.WORKSPACE).get("owner").equals(id)) throw new Rejected("Transfer ownership before changing the Owner's access");
        Entity existing = list(Kind.MEMBER).stream().filter(m -> m.get("player").equals(id)).findFirst().orElse(null);
        if (existing == null && remove) return;
        Patch patch = existing == null ? new Patch(Model.id(), Kind.MEMBER, r.workspace).set("player", id, 0).set("role", role, 0).set("archived", "false", 0)
            : Patch.edit(existing).set(existing, "role", role).set(existing, "archived", Boolean.toString(remove));
        Command c = r.command(); c.patches.add(patch); r.submit(c);
    }); }
    void transfer() { run(() -> {
        Command c = remote().command(); c.operation = Operation.TRANSFER; c.arguments.put("player", remote().playerId(player)); remote().submit(c);
    }); }
    void startSprint() { if (selected == null) return; Command c = remote().command(); c.patches.add(Patch.edit(selected).set(selected, "state", "ACTIVE")); remote().submit(c, this::reload); }
    void completeSprint() { run(() -> {
        if (selected == null) return; Command c = remote().command(); c.operation = Operation.COMPLETE_SPRINT; c.target = selected.id;
        c.arguments.put("destination", remote().sprintId(destination)); remote().submit(c, this::reload);
    }); }
    Entity liveSelected() { return selected == null ? null : remote().state.entities.get(selected.id); }
    void graphSelect(String id) {
        if (selected == null) return;
        if (id.equals("*")) { newStatus(); return; }
        for (Entity e : graphEntries()) if (e.id.equals(id) || e.get("logical").equals(id)) {
            graph = e.copy(); graphName = e.get("name"); category = e.get("category"); x = e.get("x"); y = e.get("y");
            required = e.get("required"); transitionRole = e.get("role"); return;
        }
    }
    void newStatus() { graph = null; graphName = required = ""; category = "TODO"; x = y = "0"; }
    void connectSelected() { if (!connectTo.isEmpty()) connect(connectFrom, connectTo); }
    List<Entity> graphEntries() {
        if (selected == null) return Collections.emptyList();
        Entity workflow = liveSelected(); if (workflow == null) return Collections.emptyList();
        List<Entity> result = new ArrayList<>(Workflows.nodes(remote().state, workflow, true));
        result.addAll(Workflows.edges(remote().state, workflow, true)); return result;
    }
    void addStatus() { run(() -> {
        if (selected == null) return;
        if (graphName.trim().isEmpty()) throw new Rejected("Enter a name for the new status.");
        Command c = remote().command(); String logical = Model.id();
        Patch p = new Patch(Model.id(), Kind.STATUS, remote().workspace).set("workflow", selected.id, 0).set("generation", "draft", 0)
            .set("logical", logical, 0).set("name", graphName.trim(), 0)
            .set("category", "TODO", 0).set("x", "100", 0).set("y", "130", 0).set("archived", "false", 0);
        c.patches.add(p); remote().submit(c, () -> graphSelect(logical));
    }); }
    void connect(String from, String to) { run(() -> {
        if (selected == null || from.equals(to) && from.equals("*")) return;
        String destinationName = Workflows.nodes(remote().state, liveSelected(), true).stream()
            .filter(e -> e.get("logical").equals(to)).map(e -> e.get("name")).findFirst().orElse("status");
        Command c = remote().command(); String id = Model.id();
        Patch p = new Patch(id, Kind.TRANSITION, remote().workspace).set("workflow", selected.id, 0).set("generation", "draft", 0)
            .set("logical", Model.id(), 0).set("name", "Move to " + destinationName, 0)
            .set("from", from, 0).set("to", to, 0).set("role", "EDITOR", 0).set("required", "", 0).set("archived", "false", 0);
        c.patches.add(p); remote().submit(c, () -> graphSelect(id));
    }); }
    void moveNode(String logical, int[] position) { run(() -> {
        if (logical.equals("*")) return;
        Entity node = Workflows.nodes(remote().state, liveSelected(), true).stream().filter(e -> e.get("logical").equals(logical)).findFirst().orElse(null);
        if (node == null) return; Command c = remote().command(); c.patches.add(Patch.edit(node).set(node, "x", Integer.toString(position[0])).set(node, "y", Integer.toString(position[1])));
        remote().submit(c, () -> graphSelect(logical));
    }); }
    void saveGraph() { run(() -> {
        if (graph == null) return; Command c = remote().command(); Patch p = Patch.edit(graph);
        if (graphName.trim().isEmpty()) throw new Rejected("Enter a name before saving.");
        if (!graphName.equals(graph.get("name"))) p.set(graph, "name", graphName.trim());
        if (graph.kind == Kind.STATUS) {
            try {
                int graphX = Integer.parseInt(x), graphY = Integer.parseInt(y);
                if (graphX < 0 || graphX > 10000 || graphY < 0 || graphY > 10000) throw new NumberFormatException();
            } catch (NumberFormatException invalid) { throw new Rejected("Graph X and Y must be whole numbers from 0 to 10000."); }
            if (!category.equals(graph.get("category"))) p.set(graph, "category", category);
            if (!x.equals(graph.get("x"))) p.set(graph, "x", x); if (!y.equals(graph.get("y"))) p.set(graph, "y", y);
        } else {
            if (!required.equals(graph.get("required"))) p.set(graph, "required", required.replace(" ", ""));
            if (!transitionRole.equals(graph.get("role"))) p.set(graph, "role", transitionRole);
        }
        if (p.values.isEmpty()) return;
        c.patches.add(p); String id = graph.id; remote().submit(c, () -> graphSelect(id));
    }); }
    void initial() {
        if (graph == null || graph.kind != Kind.STATUS) return; Entity flow = liveSelected(); Command c = remote().command();
        c.patches.add(Patch.edit(flow).set(flow, "draftInitial", graph.get("logical"))); remote().submit(c);
    }
    void removeGraph() {
        if (graph == null) return; Command c = remote().command(); c.patches.add(Patch.edit(graph).set(graph, "archived", "true"));
        if (graph.kind == Kind.STATUS) {
            if (liveSelected().get("draftInitial").equals(graph.get("logical"))) { session.remoteNotice("Choose another initial status before removing this one"); return; }
            for (Entity edge : Workflows.edges(remote().state, liveSelected(), true)) if (edge.get("from").equals(graph.get("logical")) || edge.get("to").equals(graph.get("logical")))
                c.patches.add(Patch.edit(edge).set(edge, "archived", "true"));
        }
        remote().submit(c, () -> graph = null);
    }
    void saveMapping() { run(() -> {
        if (selected == null || mappingWorkflow.isEmpty()) throw new Rejected("Select a workflow");
        Entity old = list(Kind.MAPPING).stream().filter(e -> !e.archived() && e.get("scheme").equals(selected.id) && e.get("generation").equals("draft") && e.get("type").equals(mappingType)).findFirst().orElse(null);
        Patch p = old == null ? new Patch(Model.id(), Kind.MAPPING, remote().workspace).set("scheme", selected.id, 0).set("generation", "draft", 0).set("type", mappingType, 0).set("workflow", mappingWorkflow, 0).set("archived", "false", 0)
            : Patch.edit(old).set(old, "workflow", mappingWorkflow);
        // Mapping endpoints are configurable; graph identity and published generations remain immutable.
        Command c = remote().command(); c.patches.add(p); remote().submit(c);
    }); }
    State publicationPreview() {
        String currentKey = (selected == null ? "" : selected.id) + "/" + projectScheme;
        if (preview != null && previewRevision == remote().state.revision && previewKey.equals(currentKey)) return preview;
        previewRevision = remote().state.revision; previewKey = currentKey;
        preview = remote().state.copy(); Entity root = selected == null ? null : preview.entities.get(selected.id);
        if (root == null) return preview;
        if (root.kind == Kind.WORKFLOW || root.kind == Kind.SCHEME) { root.fields.put("generation", "draft"); if (root.kind == Kind.WORKFLOW) root.fields.put("initial", root.get("draftInitial")); }
        if (root.kind == Kind.PROJECT && !projectScheme.isEmpty()) root.fields.put("scheme", projectScheme);
        return preview;
    }
    List<Entity> migrationTasks() {
        List<Entity> result = new ArrayList<>(); if (selected == null) return result; State preview = publicationPreview();
        for (Entity task : list(Kind.TASK)) {
            Entity flow = Workflows.workflow(preview, preview.require(task.get("project"), Kind.PROJECT), task.get("type"));
            if (Workflows.nodes(preview, flow, false).stream().noneMatch(n -> n.get("logical").equals(task.get("status")))) {
                List<Entity> nodes = Workflows.nodes(preview, flow, false); if (nodes.isEmpty()) throw new Rejected("The draft workflow has no statuses");
                String choice = migrations.get(task.id);
                if (choice == null || nodes.stream().noneMatch(n -> n.get("logical").equals(choice))) migrations.put(task.id, flow.get("initial"));
                result.add(task);
            }
        }
        return result;
    }
    List<Entity> migrationStatuses(Entity task) { State preview = publicationPreview(); return Workflows.nodes(preview, Workflows.workflow(preview, preview.require(task.get("project"), Kind.PROJECT), task.get("type")), false); }
    void publish() { run(() -> {
        if (selected == null) return; Command c = remote().command();
        List<Entity> tasks = migrationTasks();
        if (selected.kind == Kind.PROJECT) {
            Entity project = liveSelected(); c.patches.add(Patch.edit(project).set(project, "scheme", projectScheme));
            for (Entity task : tasks) c.patches.add(Patch.edit(task).set(task, "status", migrations.get(task.id)));
        } else {
            Entity root = liveSelected(); c.operation = Operation.PUBLISH; c.target = root.id;
            c.arguments.put("generationVersion", Long.toString(root.version("generation")));
            c.arguments.put("draftVersion", Long.toString(Workflows.draftVersion(remote().state, root)));
            for (Entity task : tasks) { c.arguments.put("migrate." + task.id, migrations.get(task.id)); c.arguments.put("migrateVersion." + task.id, Long.toString(task.version("status"))); }
        }
        remote().submit(c, () -> { migrations.clear(); if (selected != null) select(remote().state.entities.get(selected.id)); });
    }); }
}
