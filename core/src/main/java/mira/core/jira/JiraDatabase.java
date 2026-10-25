package mira.core.jira;

import java.io.IOException;
import java.util.*;
import mira.core.jira.Model.*;

/** Synchronized transaction API. Durable writes complete before state or events become visible. */
public final class JiraDatabase {
    public interface Store { void write(State snapshot) throws IOException; }
    private State state;
    private final Store store;
    public JiraDatabase(State restored, Store store) { Rules.validate(restored); this.state = restored.copy(); this.store = store; }

    public synchronized State snapshot() { return state.copy(); }
    public synchronized long revision() { return state.revision; }
    public synchronized State view(String player) {
        State view = new State(); view.revision = state.revision; view.players.putAll(state.players);
        for (Entity e : state.entities.values()) {
            Role role = state.role(e.workspace, player);
            if (role == Role.NONE) continue;
            if ((e.kind == Kind.STATUS || e.kind == Kind.TRANSITION || e.kind == Kind.MAPPING)
                    && e.get("generation").equals("draft") && role != Role.OWNER) continue;
            Entity copy = e.copy();
            if (role != Role.OWNER) { copy.fields.remove("draftInitial"); copy.versions.remove("draftInitial"); }
            view.entities.put(copy.id, copy);
        }
        for (Activity a : state.activity) if (state.role(a.workspace, player) != Role.NONE) {
            Activity copy = a.copy();
            // Draft contents and previous permission/configuration values are owner-only.
            if (state.role(a.workspace, player) != Role.OWNER) copy.differences.removeIf(d -> {
                Entity e = state.entities.get(d.entity); return e == null || Rules.ownerOnly(e.kind);
            });
            view.activity.add(copy);
        }
        return view;
    }
    public synchronized void rememberPlayer(String player, String name) {
        Rules.uuid(player); Rules.text(name, 1, 80);
        if (name.equals(state.players.get(player))) return;
        if (!state.players.containsKey(player) && state.players.size() >= 100000) throw new Rejected("World player directory limit reached");
        State next = state.copy(); next.players.put(player, name); next.revision++;
        persist(next);
    }
    public synchronized State execute(String actor, String actorName, boolean operator, Command command) {
        Rules.uuid(actor); Rules.uuid(command.id); Rules.knownPlayer(state, actor);
        // Retries of a successful request are idempotent, including after a restart.
        for (Activity a : state.activity) if (a.id.equals(command.id)) {
            if (!a.actor.equals(actor)) throw new Rejected("Request identifier belongs to another player");
            return view(actor);
        }
        if (command.patches.size() > 512 || command.arguments.size() > 1024) throw new Rejected("Transaction is too large");
        State next = state.copy();
        Role role = state.role(command.workspace, actor);
        if (command.operation != Operation.CREATE_WORKSPACE && command.operation != Operation.ADMIN_ROLE) {
            if (role.ordinal() < Role.EDITOR.ordinal()) throw new Rejected("Workspace write access is required");
            if (state.require(command.workspace, Kind.WORKSPACE).archived() && !(role == Role.OWNER && command.operation == Operation.EDIT
                && command.patches.size() == 1 && command.patches.get(0).id.equals(command.workspace) && command.patches.get(0).values.size() == 1
                && "false".equals(command.patches.get(0).values.get("archived")))) throw new Rejected("Workspace is archived");
        }
        if (command.operation == Operation.CREATE_WORKSPACE) {
            if (!command.patches.isEmpty()) throw new Rejected("Unexpected workspace creation patches");
            createWorkspace(next, actor, command);
        } else {
            for (Patch patch : command.patches) apply(next, state, actor, role, command.workspace, patch);
            switch (command.operation) {
                case CREATE_PROJECT: createProject(next, command.workspace, command.target, command.arguments.get("name"), command.arguments.get("key"), command.arguments.getOrDefault("scheme", "")); break;
                case CREATE_WORKFLOW:
                    owner(role); createWorkflow(next, command.workspace, command.target, command.arguments.get("name"), command.arguments.getOrDefault("source", "")); break;
                case CREATE_SCHEME:
                    owner(role); createScheme(next, command.workspace, command.target, command.arguments.get("name"), command.arguments.getOrDefault("source", "")); break;
                case PUBLISH: owner(role); publish(next, command); break;
                case COMPLETE_SPRINT: completeSprint(next, command); break;
                case TRANSFER: owner(role); transfer(next, command, actor); break;
                case ADMIN_ROLE:
                    if (!operator || !command.patches.isEmpty()) throw new Rejected("Operator permission is required");
                    adminRole(next, command); break;
                case UNDO:
                    if (!command.patches.isEmpty()) throw new Rejected("Undo cannot contain edits"); undo(next, actor, role, command); break;
                case EDIT: break;
                default: throw new Rejected("Unsupported operation");
            }
        }
        prepareTasks(next, command);
        normalizeColumns(next, command);
        validateEdits(next, actor, role, command);
        Rules.validate(next);
        Activity activity = differences(state, next, actor, actorName, command);
        if (activity.differences.isEmpty()) return view(actor);
        next.revision = state.revision + 1;
        activity.revision = next.revision;
        for (Difference d : activity.differences) {
            d.version = next.revision;
            next.entities.get(d.entity).versions.put(d.field, next.revision);
        }
        next.activity.add(activity);
        persist(next);
        return view(actor);
    }
    private void persist(State next) {
        try { store.write(next.copy()); }
        catch (IOException e) { throw new Rejected("World database could not be saved; no change was accepted: " + e.getMessage()); }
        state = next;
    }
    private static void owner(Role role) { if (role != Role.OWNER) throw new Rejected("Workspace Owner permission is required"); }
    private static void apply(State next, State old, String actor, Role role, String workspace, Patch patch) {
        if (!patch.workspace.equals(workspace)) throw new Rejected("A transaction must stay inside its workspace");
        if (Rules.ownerOnly(patch.kind)) owner(role);
        Entity existing = old.entities.get(patch.id), target = next.entities.get(patch.id);
        if (existing != null && (existing.kind != patch.kind || !existing.workspace.equals(workspace))) throw new Rejected("Identity cannot be changed");
        if (target != null && (target.kind != patch.kind || !target.workspace.equals(workspace))) throw new Rejected("Identity cannot be changed inside a transaction");
        for (Map.Entry<String, Long> expected : patch.expected.entrySet()) if (expected.getValue() != (existing == null ? 0L : existing.version(expected.getKey())))
            throw new Conflict("Another player changed " + patch.kind + "." + expected.getKey() + "; your draft has been kept");
        if (existing != null && existing.archived() && !"false".equals(patch.values.get("archived"))) throw new Conflict("This entity was archived; restore it before editing");
        if (target == null) { target = new Entity(patch.id, patch.kind, workspace); next.entities.put(target.id, target); }
        if (existing == null && (patch.kind == Kind.WORKSPACE || patch.kind == Kind.PROJECT || patch.kind == Kind.WORKFLOW || patch.kind == Kind.SCHEME))
            throw new Rejected("Use the creation operation for this entity");
        if (patch.kind == Kind.COMMENT && existing != null && !existing.get("author").equals(actor) && role != Role.OWNER)
            throw new Rejected("Only the author or Owner may moderate a comment");
        if ((patch.kind == Kind.STATUS || patch.kind == Kind.TRANSITION || patch.kind == Kind.MAPPING)
                && !(existing == null ? patch.values.getOrDefault("generation", "") : existing.get("generation")).equals("draft"))
            throw new Rejected("Published configuration is immutable; edit its draft");
        for (Map.Entry<String, String> field : patch.values.entrySet()) {
            String key = field.getKey(), value = field.getValue();
            if (!Rules.field(patch.kind, key) || value == null) throw new Rejected("Unsupported field: " + key);
            if (key.equals("owner") || key.equals("sequence") || key.equals("key")
                    || key.equals("initial") || key.equals("generation") && patch.kind != Kind.STATUS && patch.kind != Kind.TRANSITION && patch.kind != Kind.MAPPING)
                throw new Rejected("Field is managed by the server: " + key);
            if (existing != null && Rules.immutable(patch.kind, key) && !value.equals(existing.get(key))) throw new Rejected("Reference is immutable: " + key);
            long actual = existing == null ? 0 : existing.version(key);
            if (!patch.expected.containsKey(key) || patch.expected.get(key) != actual)
                throw new Conflict("Another player changed " + patch.kind + "." + key + "; your draft has been kept");
            if (existing == null && actual == 0 && patch.kind == Kind.COMMENT && key.equals("author") && !value.equals(actor))
                throw new Rejected("Comment author must be the sending player");
            target.fields.put(key, value);
        }
        if (existing == null && patch.kind == Kind.COMMENT) target.fields.put("author", actor);
    }
    private static Entity add(State s, Kind kind, String workspace, String id, String... fields) {
        Rules.uuid(id); if (s.entities.containsKey(id)) throw new Rejected("Identifier already exists");
        Entity e = new Entity(id, kind, workspace); s.entities.put(id, e);
        for (int i = 0; i < fields.length; i += 2) e.fields.put(fields[i], fields[i + 1]);
        e.fields.putIfAbsent("archived", "false"); return e;
    }
    private static Entity generated(State s, Kind kind, String workspace, String... fields) { return add(s, kind, workspace, Model.id(), fields); }
    private static void createWorkspace(State s, String actor, Command c) {
        add(s, Kind.WORKSPACE, c.workspace, c.workspace, "name", c.arguments.get("name"), "owner", actor);
        for (String name : Arrays.asList("Task", "Story", "Bug", "Epic", "Subtask")) generated(s, Kind.ISSUE_TYPE, c.workspace, "name", name);
        String workflow = Model.id(), scheme = Model.id();
        createWorkflow(s, c.workspace, workflow, "Standard workflow", "");
        createScheme(s, c.workspace, scheme, "Default scheme", "");
        createProject(s, c.workspace, Model.id(), "Main project", "MAIN", scheme);
    }
    private static void createWorkflow(State s, String workspace, String id, String name, String source) {
        Entity flow = add(s, Kind.WORKFLOW, workspace, id, "name", name, "generation", "initial");
        if (!source.isEmpty()) {
            Entity original = s.require(source, Kind.WORKFLOW);
            if (!original.workspace.equals(workspace)) throw new Rejected("Cross-workspace workflow");
            Map<String, String> identities = new HashMap<>();
            for (Entity node : Workflows.nodes(s, original, false)) identities.put(node.get("logical"), Model.id());
            for (Entity edge : Workflows.edges(s, original, false)) identities.put(edge.get("logical"), Model.id());
            flow.fields.put("initial", identities.get(original.get("initial"))); flow.fields.put("draftInitial", identities.get(original.get("initial")));
            for (Kind kind : Arrays.asList(Kind.STATUS, Kind.TRANSITION)) {
                List<Entity> entries = kind == Kind.STATUS ? Workflows.nodes(s, original, false) : Workflows.edges(s, original, false);
                for (Entity entry : entries) for (String generation : Arrays.asList("initial", "draft")) {
                    Entity copy = add(s, kind, workspace, Model.id(), new String[0]); copy.fields.putAll(entry.fields);
                    copy.fields.put("workflow", id); copy.fields.put("generation", generation);
                    copy.fields.put("logical", identities.get(entry.get("logical")));
                    if (kind == Kind.TRANSITION) { copy.fields.put("from", entry.get("from").equals("*") ? "*" : identities.get(entry.get("from"))); copy.fields.put("to", identities.get(entry.get("to"))); }
                }
            }
            return;
        }
        String[] nodes = {Model.id(), Model.id(), Model.id(), Model.id()};
        String[] names = {"Open", "In progress", "Review", "Done"}; String[] categories = {"TODO", "IN_PROGRESS", "IN_PROGRESS", "DONE"};
        flow.fields.put("initial", nodes[0]); flow.fields.put("draftInitial", nodes[0]);
        for (String generation : Arrays.asList("initial", "draft")) {
            for (int i = 0; i < nodes.length; i++) generated(s, Kind.STATUS, workspace, "workflow", id, "generation", generation,
                "logical", nodes[i], "name", names[i], "category", categories[i], "x", Integer.toString(i * 150), "y", "50");
            for (int i = 0; i < nodes.length; i++) generated(s, Kind.TRANSITION, workspace, "workflow", id, "generation", generation,
                "logical", Model.id(), "name", "Move to " + names[i], "from", "*", "to", nodes[i], "role", "EDITOR", "required", "");
        }
    }
    private static void createScheme(State s, String workspace, String id, String name, String source) {
        add(s, Kind.SCHEME, workspace, id, "name", name, "generation", "initial");
        List<Entity> mappings = new ArrayList<>();
        if (!source.isEmpty()) {
            Entity original = s.require(source, Kind.SCHEME);
            if (!original.workspace.equals(workspace)) throw new Rejected("Cross-workspace scheme");
            for (Entity e : s.list(Kind.MAPPING, workspace)) if (!e.archived() && e.get("scheme").equals(source) && e.get("generation").equals(original.get("generation"))) mappings.add(e);
        }
        for (String generation : Arrays.asList("initial", "draft")) {
            if (mappings.isEmpty()) generated(s, Kind.MAPPING, workspace, "scheme", id, "generation", generation, "type", "*", "workflow", s.list(Kind.WORKFLOW, workspace).stream().filter(e -> !e.archived()).findFirst().orElseThrow(() -> new Rejected("Create or restore a workflow first")).id);
            else for (Entity mapping : mappings) generated(s, Kind.MAPPING, workspace, "scheme", id, "generation", generation, "type", mapping.get("type"), "workflow", mapping.get("workflow"));
        }
    }
    private static void createProject(State s, String workspace, String id, String name, String key, String scheme) {
        if (scheme.isEmpty()) scheme = s.list(Kind.SCHEME, workspace).stream().filter(e -> !e.archived()).findFirst().orElseThrow(() -> new Rejected("Create or restore a workflow scheme first")).id;
        Entity project = add(s, Kind.PROJECT, workspace, id, "name", name, "key", key, "scheme", scheme, "sequence", "0");
        Entity workflow = Workflows.workflow(s, project, "*"); int rank = 0;
        for (Entity node : Workflows.nodes(s, workflow, false)) generated(s, Kind.COLUMN, workspace, "project", id, "name", node.get("name"), "statuses", node.get("logical"), "rank", Integer.toString(rank++ * 1024));
    }
    private static void publish(State s, Command command) {
        Entity root = s.entities.get(command.target);
        if (root == null || !root.workspace.equals(command.workspace) || root.kind != Kind.WORKFLOW && root.kind != Kind.SCHEME) throw new Rejected("Choose a workflow or scheme to publish");
        if (root.archived()) throw new Rejected("Restore the configuration before publishing");
        if (!Long.toString(root.version("generation")).equals(command.arguments.get("generationVersion"))
            || !Long.toString(Workflows.draftVersion(s, root)).equals(command.arguments.get("draftVersion"))) throw new Conflict("Workflow configuration changed before publication; review the new draft");
        List<Entity> drafts = new ArrayList<>();
        for (Entity e : s.entities.values()) if (e.workspace.equals(root.workspace) && !e.archived() && e.get("generation").equals("draft")
                && (root.kind == Kind.WORKFLOW && e.get("workflow").equals(root.id) && (e.kind == Kind.STATUS || e.kind == Kind.TRANSITION)
                    || root.kind == Kind.SCHEME && e.kind == Kind.MAPPING && e.get("scheme").equals(root.id))) drafts.add(e);
        String generation = Long.toString(s.revision + 1);
        for (Entity draft : drafts) { Entity copy = add(s, draft.kind, draft.workspace, Model.id(), new String[0]); copy.fields.putAll(draft.fields); copy.fields.put("generation", generation); }
        root.fields.put("generation", generation);
        if (root.kind == Kind.WORKFLOW) root.fields.put("initial", root.get("draftInitial"));
        for (Map.Entry<String, String> migration : command.arguments.entrySet()) if (migration.getKey().startsWith("migrate.")) {
            Entity task = s.require(migration.getKey().substring(8), Kind.TASK);
            if (!task.workspace.equals(command.workspace)) throw new Rejected("Cross-workspace migration");
            if (!Long.toString(task.version("status")).equals(command.arguments.get("migrateVersion." + task.id))) throw new Conflict("A task changed status before migration; review the migration");
            task.fields.put("status", migration.getValue());
        }
    }
    private static void completeSprint(State s, Command command) {
        Entity sprint = s.require(command.target, Kind.SPRINT);
        if (!sprint.workspace.equals(command.workspace) || sprint.archived() || !sprint.get("state").equals("ACTIVE") || s.require(sprint.get("project"), Kind.PROJECT).archived()) throw new Rejected("Only an active sprint in an active project can be completed");
        String destination = command.arguments.getOrDefault("destination", "");
        if (!destination.isEmpty()) {
            Entity next = s.require(destination, Kind.SPRINT);
            if (!next.workspace.equals(command.workspace) || !next.get("project").equals(sprint.get("project")) || !next.get("state").equals("PLANNED") || next.archived())
                throw new Rejected("Choose a planned sprint in the same project, or the backlog");
        }
        for (Entity task : s.list(Kind.TASK, command.workspace)) if (!task.archived() && task.get("sprint").equals(sprint.id) && !Workflows.done(s, task)) task.fields.put("sprint", destination);
        sprint.fields.put("state", "COMPLETED");
    }
    private static void transfer(State s, Command command, String previous) {
        String player = command.arguments.get("player"); Rules.knownPlayer(s, player);
        Entity workspace = s.require(command.workspace, Kind.WORKSPACE);
        workspace.fields.put("owner", player);
        if (!player.equals(previous)) member(s, workspace.id, previous, "EDITOR");
        for (Entity e : s.list(Kind.MEMBER, workspace.id)) if (e.get("player").equals(player)) e.fields.put("archived", "true");
    }
    private static void member(State s, String workspace, String player, String role) {
        Rules.knownPlayer(s, player);
        for (Entity e : s.list(Kind.MEMBER, workspace)) if (e.get("player").equals(player)) { e.fields.put("role", role); e.fields.put("archived", "false"); return; }
        generated(s, Kind.MEMBER, workspace, "player", player, "role", role);
    }
    private static void adminRole(State s, Command command) {
        Entity workspace = s.require(command.workspace, Kind.WORKSPACE);
        String player = command.arguments.get("player"), role = command.arguments.get("role"); Rules.knownPlayer(s, player);
        if ("OWNER".equals(role)) transfer(s, command, workspace.get("owner"));
        else if ("NONE".equals(role)) {
            if (workspace.get("owner").equals(player)) throw new Rejected("Transfer ownership before removing the Owner");
            for (Entity e : s.list(Kind.MEMBER, workspace.id)) if (e.get("player").equals(player)) e.fields.put("archived", "true");
        } else if ("EDITOR".equals(role) || "VIEWER".equals(role)) member(s, workspace.id, player, role);
        else throw new Rejected("Unknown role");
    }
    private void prepareTasks(State next, Command command) {
        for (Entity task : next.list(Kind.TASK, command.workspace)) if (!state.entities.containsKey(task.id)) {
            Entity project = next.require(task.get("project"), Kind.PROJECT);
            long number;
            try { number = Math.addExact(Long.parseLong(project.get("sequence")), 1); }
            catch (ArithmeticException | NumberFormatException e) { throw new Rejected("Issue number limit reached"); }
            project.fields.put("sequence", Long.toString(number)); task.fields.put("key", project.get("key") + "-" + number);
            if (task.get("status").isEmpty()) task.fields.put("status", Workflows.workflow(next, project, task.get("type")).get("initial"));
        }
    }
    private void normalizeColumns(State next, Command command) {
        for (Entity project : next.list(Kind.PROJECT, command.workspace)) {
            Entity old = state.entities.get(project.id);
            if (command.operation != Operation.PUBLISH && (old == null || old.get("scheme").equals(project.get("scheme")))) continue;
            Set<String> statuses = new HashSet<>(), workflows = new HashSet<>();
            for (Entity type : next.list(Kind.ISSUE_TYPE, command.workspace)) {
                Entity workflow = Workflows.workflow(next, project, type.id);
                if (workflows.add(workflow.id)) for (Entity node : Workflows.nodes(next, workflow, false)) statuses.add(node.get("logical"));
            }
            for (Entity column : next.list(Kind.COLUMN, command.workspace)) if (column.get("project").equals(project.id)) {
                List<String> ids = Workflows.ids(column.get("statuses"));
                if (ids.removeIf(id -> !statuses.contains(id))) column.fields.put("statuses", String.join(",", ids));
            }
        }
    }
    private void validateEdits(State next, String actor, Role role, Command command) {
        for (Entity e : next.entities.values()) {
            Entity old = state.entities.get(e.id);
            if (old != null && old.fields.equals(e.fields)) continue;
            if (!e.workspace.equals(command.workspace)) throw new Rejected("Cross-workspace transaction");
            if (Rules.ownerOnly(e.kind) && command.operation != Operation.CREATE_WORKSPACE && command.operation != Operation.ADMIN_ROLE) owner(role);
            if (old != null && e.kind == Kind.WORKSPACE && command.operation != Operation.TRANSFER && command.operation != Operation.ADMIN_ROLE && !e.get("owner").equals(old.get("owner")))
                throw new Rejected("Ownership must be transferred explicitly");
            if (e.kind == Kind.PROJECT && old != null && !e.get("scheme").equals(old.get("scheme"))) owner(role);
            if ((e.kind == Kind.SPRINT || e.kind == Kind.COLUMN) && !e.archived() && next.require(e.get("project"), Kind.PROJECT).archived() && command.operation != Operation.PUBLISH) throw new Rejected("Restore the project before changing its configuration");
            if ((e.kind == Kind.STATUS || e.kind == Kind.TRANSITION) && !e.archived() && next.require(e.get("workflow"), Kind.WORKFLOW).archived()) throw new Rejected("Restore the workflow before editing its draft");
            if (e.kind == Kind.MAPPING && !e.archived() && next.require(e.get("scheme"), Kind.SCHEME).archived()) throw new Rejected("Restore the scheme before editing its draft");
            if (e.kind == Kind.SPRINT && old != null && !e.get("state").equals(old.get("state"))) {
                boolean start = old.get("state").equals("PLANNED") && e.get("state").equals("ACTIVE");
                boolean finish = command.operation == Operation.COMPLETE_SPRINT && command.target.equals(e.id);
                if (!start && !finish && command.operation != Operation.UNDO) throw new Rejected("Use sprint start/complete actions");
            }
            if (e.kind == Kind.SPRINT && old == null && !e.get("state").equals("PLANNED")) throw new Rejected("New sprints must be planned");
            if (e.kind == Kind.TASK) {
                Entity project = next.require(e.get("project"), Kind.PROJECT);
                if (project.archived() && command.operation != Operation.PUBLISH && !(old != null && role == Role.OWNER && !state.require(project.id, Kind.PROJECT).get("scheme").equals(project.get("scheme")))) throw new Rejected("Restore the project before changing its tasks");
                if ((old == null || !old.get("type").equals(e.get("type"))) && next.require(e.get("type"), Kind.ISSUE_TYPE).archived()) throw new Rejected("Issue type is archived");
                if (old == null && !e.get("status").equals(Workflows.workflow(next, project, e.get("type")).get("initial"))) throw new Rejected("New tasks must start in their workflow's initial status");
                if (old != null) {
                    boolean migration = command.operation == Operation.PUBLISH || !state.require(old.get("project"), Kind.PROJECT).get("scheme").equals(project.get("scheme"));
                    Entity workflow = Workflows.workflow(next, project, e.get("type"));
                    boolean oldStatusValid = Workflows.nodes(next, workflow, false).stream().anyMatch(n -> n.get("logical").equals(old.get("status")));
                    if (migration) owner(role);
                    else if (!old.get("type").equals(e.get("type")) && !oldStatusValid) {
                        if (!e.get("status").equals(workflow.get("initial"))) throw new Rejected("A type change must use the new workflow's initial status when its old status is absent");
                    } else Workflows.transition(next, old, e, role, command.arguments.getOrDefault("transition." + e.id, ""));
                }
                if ((old == null || !old.get("sprint").equals(e.get("sprint"))) && !e.get("sprint").isEmpty()) {
                    Entity sprint = next.require(e.get("sprint"), Kind.SPRINT);
                    if (sprint.archived() || sprint.get("state").equals("COMPLETED")) throw new Rejected("Cannot assign work to a closed sprint");
                }
            }
            if (e.kind == Kind.BLOCK) {
                Entity task = next.require(e.get("task"), Kind.TASK);
                if ((next.require(task.get("project"), Kind.PROJECT).archived() || task.archived()) && !e.archived()) throw new Rejected("Restore the task/project before editing blocks");
                if (!BodyValues.decode(e.get("content")).id.equals(e.id)) throw new Rejected("Block identity does not match its contents");
            }
            if (e.kind == Kind.COMMENT && !e.archived() && (next.require(e.get("task"), Kind.TASK).archived() || next.require(next.require(e.get("task"), Kind.TASK).get("project"), Kind.PROJECT).archived()))
                throw new Rejected("Restore the task/project before commenting");
            if (e.kind == Kind.COMMENT && old != null && !old.get("author").equals(actor) && role != Role.OWNER) throw new Rejected("Only the author or Owner may moderate a comment");
        }
    }
    private static Activity differences(State before, State after, String actor, String name, Command command) {
        Activity a = new Activity(); a.id = command.id; a.workspace = command.workspace; a.actor = actor; a.name = name;
        a.time = System.currentTimeMillis(); a.summary = command.operation.name();
        a.undoable = command.operation == Operation.EDIT || command.operation == Operation.CREATE_PROJECT;
        for (Entity e : after.entities.values()) {
            Entity old = before.entities.get(e.id);
            for (Map.Entry<String, String> field : e.fields.entrySet()) if (old == null || !Objects.equals(old.fields.get(field.getKey()), field.getValue())) {
                Difference d = new Difference(); d.entity = e.id; d.field = field.getKey(); d.before = old == null ? null : old.fields.get(field.getKey());
                d.after = field.getValue(); d.created = old == null; a.differences.add(d);
            }
        }
        if (command.operation == Operation.EDIT) {
            Set<String> descriptions = new LinkedHashSet<>();
            for (Difference d : a.differences) {
                Entity e = after.entities.get(d.entity); if (d.field.equals("sequence")) continue;
                String identity = e.kind == Kind.TASK ? e.get("key") : e.kind == Kind.BLOCK || e.kind == Kind.COMMENT ? after.entities.get(e.get("task")).get("key") : e.get("name");
                descriptions.add(e.kind + " " + identity + ": " + (d.field.equals("content") ? "description block" : d.field));
            }
            String summary = String.join("; ", descriptions); a.summary = summary.length() > 400 ? summary.substring(0, 397) + "..." : summary;
        }
        return a;
    }
    private static void undo(State next, String actor, Role role, Command command) {
        Activity target = null;
        for (int i = next.activity.size() - 1; i >= 0; i--) {
            Activity a = next.activity.get(i);
            if (a.workspace.equals(command.workspace) && a.actor.equals(actor) && a.undoable && a.undoneBy.isEmpty()) { target = a; break; }
        }
        if (target == null || !target.id.equals(command.target)) throw new Rejected("Only your latest eligible saved action can be undone");
        Set<String> created = new HashSet<>();
        for (Difference d : target.differences) {
            Entity e = next.entities.get(d.entity);
            if (d.field.equals("sequence")) continue;
            if (e == null || e.version(d.field) != d.version) throw new Conflict("Undo would overwrite a later change");
            if (Rules.ownerOnly(e.kind)) owner(role);
            if (d.created) {
                for (long version : e.versions.values()) if (version > target.revision) throw new Conflict("Someone changed the created entity");
                created.add(e.id);
            }
        }
        for (Difference d : target.differences) {
            Entity e = next.entities.get(d.entity);
            if (d.created) e.fields.put("archived", "true");
            else if (d.before == null) e.fields.put(d.field, "");
            else if (!d.field.equals("sequence")) e.fields.put(d.field, d.before);
        }
        // A newly created project cannot be hidden over work another player added later.
        for (Entity e : next.entities.values()) if ((created.contains(e.get("project")) || created.contains(e.get("task"))) && !created.contains(e.id))
            throw new Conflict("Undo would hide work added after your action");
        target.undoneBy = command.id;
    }
}
