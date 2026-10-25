package mira.integration.agenttools.tasks;

import static dev.minecraftagenttools.protocol.data.Data.map;
import java.util.*;
import java.util.function.BiConsumer;
import mira.core.jira.Model.*;
import mira.core.jira.Workflows;
import mira.integration.agenttools.contract.MiraTools;

/** Tick-thread application adapter. Backend owns authenticated scope and asynchronous server I/O. */
public final class MiraTaskService {
    public interface Backend {
        Map<String, Object> scope();
        State snapshot(String session, int dimension);
        void submit(String session, int dimension, Command command, BiConsumer<String, String> complete);
    }
    private final Backend backend;
    private final Map<String, Receipt> receipts = new LinkedHashMap<>();
    private String session = "";
    private int dimension;
    public MiraTaskService(Backend backend) { this.backend = backend; }

    public Map<String, Object> call(String tool, Map<String, Object> args) {
        MiraTools.validate(tool, args);
        Map<String, Object> scope = backend.scope();
        if (!Objects.equals(session, scope.get("session")) || dimension != number(scope, "dimension", 0)) {
            session = (String) scope.get("session"); dimension = number(scope, "dimension", 0); receipts.clear();
        }
        if (tool.equals("mira_status")) return scope;
        if (!Boolean.TRUE.equals(scope.get("ready"))) throw new IllegalStateException(String.valueOf(scope.get("status")));
        if (!session.equals(args.get("session")) || dimension != number(args, "dimension", 0))
            throw new IllegalStateException("Mira session changed; request mira_status again");
        State state = backend.snapshot(session, dimension);
        String player = (String) scope.get("player");
        if (args.containsKey("revision") && ((Number) args.get("revision")).longValue() != state.revision)
            throw new IllegalStateException("Mira revision changed; restart paging from offset 0");
        Map<String, Object> result;
        switch (tool) {
            case "mira_list_workspaces": result = workspaces(state, player, args); break;
            case "mira_list_projects": result = projects(state, args); break;
            case "mira_list_options": result = options(state, args); break;
            case "mira_list_tasks": result = tasks(state, args); break;
            case "mira_get_task": result = task(state, player, args); break;
            case "mira_get_operation": result = operation(state, player, text(args, "requestId")); break;
            default: result = mutate(state, player, tool, args); break;
        }
        result.put("session", session); result.put("dimension", dimension); result.put("revision", state.revision);
        return result;
    }

    private Map<String, Object> workspaces(State state, String player, Map<String, Object> args) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Entity entity : state.entities.values()) if (entity.kind == Kind.WORKSPACE && !entity.archived())
            entries.add(map("id", entity.id, "name", entity.get("name"), "role", state.role(entity.id, player).name()));
        return page(entries, args);
    }
    private Map<String, Object> projects(State state, Map<String, Object> args) {
        Entity workspace = active(state, text(args, "workspaceId"), Kind.WORKSPACE);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Entity project : state.list(Kind.PROJECT, workspace.id)) if (!project.archived())
            entries.add(map("id", project.id, "workspaceId", workspace.id, "name", project.get("name"), "key", project.get("key")));
        return page(entries, args);
    }
    private Map<String, Object> options(State state, Map<String, Object> args) {
        Entity project = active(state, text(args, "projectId"), Kind.PROJECT);
        List<Map<String, Object>> entries = new ArrayList<>();
        String kind = text(args, "kind");
        if (kind.equals("players")) state.players.forEach((id, name) -> entries.add(map("id", id, "name", name)));
        else for (Entity option : state.list(kind.equals("types") ? Kind.ISSUE_TYPE : Kind.SPRINT, project.workspace)) {
            if (option.archived() || kind.equals("sprints") && (!option.get("project").equals(project.id) || option.get("state").equals("COMPLETED"))) continue;
            entries.add(map("id", option.id, "name", option.get("name"), "state", option.get("state")));
        }
        return page(entries, args);
    }
    private Map<String, Object> tasks(State state, Map<String, Object> args) {
        Entity workspace = active(state, text(args, "workspaceId"), Kind.WORKSPACE);
        String project = text(args, "projectId");
        if (!project.isEmpty() && !active(state, project, Kind.PROJECT).workspace.equals(workspace.id))
            throw new IllegalArgumentException("Project is not in this workspace");
        String query = text(args, "query").toLowerCase(Locale.ROOT), category = text(args, "category");
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Entity> statuses = new HashMap<>();
        int offset = number(args, "offset", 0), limit = number(args, "limit", 20), total = 0;
        for (Entity task : state.list(Kind.TASK, workspace.id)) {
            if (task.archived() || state.require(task.get("project"), Kind.PROJECT).archived()
                    || !project.isEmpty() && !task.get("project").equals(project)) continue;
            if (!query.isEmpty() && !(task.get("key") + " " + task.get("title") + " " + task.get("description")).toLowerCase(Locale.ROOT).contains(query)) continue;
            // Resolve each published workflow/status combination once, and project only this page.
            String statusKey = task.get("project") + ":" + task.get("type") + ":" + task.get("status");
            Entity status = statuses.computeIfAbsent(statusKey, ignored -> taskStatus(state, task));
            if (!category.isEmpty() && !category.equals(status.get("category"))) continue;
            if (total >= offset && entries.size() < limit) entries.add(summary(state, task, status));
            total++;
        }
        long end = (long) offset + entries.size();
        return map("entries", entries, "total", total, "offset", offset, "nextOffset", end < total ? end : null);
    }
    private Map<String, Object> summary(State state, Entity task) {
        return summary(state, task, taskStatus(state, task));
    }
    private static Entity taskStatus(State state, Entity task) {
        Entity project = state.require(task.get("project"), Kind.PROJECT);
        return Workflows.status(state, Workflows.workflow(state, project, task.get("type")), task.get("status"));
    }
    private Map<String, Object> summary(State state, Entity task, Entity status) {
        return map("id", task.id, "workspaceId", task.workspace, "projectId", task.get("project"), "key", task.get("key"),
            "title", task.get("title"), "type", task.get("type"), "priority", task.get("priority"),
            "assignee", task.get("assignee"), "assigneeName", state.players.getOrDefault(task.get("assignee"), "Unassigned"),
            "sprint", task.get("sprint"), "epic", task.get("epic"), "status", task.get("status"),
            "statusName", status.get("name"), "category", status.get("category"),
            "widget", map("id", "mira:tasks-v1", "payload", map("session", session, "dimension", dimension, "taskIds", Collections.singletonList(task.id))));
    }
    private Map<String, Object> task(State state, String player, Map<String, Object> args) {
        Entity task = active(state, text(args, "taskId"), Kind.TASK);
        Map<String, Object> result = summary(state, task);
        result.put("fieldVersions", new LinkedHashMap<>(task.versions));
        String section = (String) args.getOrDefault("section", "description");
        result.put("section", section);
        if (section.equals("transitions")) {
            List<Map<String, Object>> transitions = new ArrayList<>();
            for (Entity edge : Workflows.transitions(state, task, state.role(task.workspace, player)))
                transitions.add(map("transitionId", edge.get("logical"), "name", edge.get("name"), "status", edge.get("to")));
            result.putAll(page(transitions, args));
        } else {
            int offset = number(args, "textOffset", 0);
            TaskText text = new TaskText(offset);
            if (section.equals("description")) text.add(task.get("description"));
            else if (section.equals("body")) text.body(state, task);
            else for (Entity comment : state.list(Kind.COMMENT, task.workspace)) if (!comment.archived() && comment.get("task").equals(task.id)) {
                text.add("[" + comment.id + "] " + state.players.getOrDefault(comment.get("author"), comment.get("author")) + ":\n");
                text.add(comment.get("text")); text.add("\n\n");
            }
            result.put("text", text.text()); result.put("textOffset", offset); result.put("nextTextOffset", text.next());
        }
        return result;
    }

    private Map<String, Object> mutate(State state, String player, String tool, Map<String, Object> args) {
        String request = text(args, "requestId");
        Receipt existing = receipts.get(request);
        if (existing != null) {
            if (!existing.tool.equals(tool) || !existing.arguments.equals(args))
                throw new IllegalArgumentException("requestId was already used with different input; use a fresh UUID");
            return operation(state, player, request);
        }
        for (Activity activity : state.activity) if (activity.id.equals(request)) return operation(state, player, request);
        if (receipts.values().stream().filter(r -> r.result.equals("PENDING")).count() >= 8)
            throw new IllegalStateException("Too many pending Mira writes; poll mira_get_operation before submitting more");
        Entity source = active(state, text(args, tool.equals("mira_create_task") ? "projectId" : "taskId"),
            tool.equals("mira_create_task") ? Kind.PROJECT : Kind.TASK);
        if (state.role(source.workspace, player).ordinal() < Role.EDITOR.ordinal())
            throw new IllegalStateException("Workspace Editor permission is required");
        Command command = new Command(); command.id = request; command.workspace = source.workspace;
        String taskId = source.id;
        switch (tool) {
            case "mira_create_task":
                // Stable identity across an identical retry, including after connection loss.
                taskId = request;
                Patch created = new Patch(taskId, Kind.TASK, source.workspace);
                created.set("project", source.id, 0).set("priority", "Medium", 0).set("description", "", 0)
                    .set("assignee", "", 0).set("sprint", "", 0).set("epic", "", 0).set("archived", "false", 0);
                double rank = 0;
                for (Entity other : state.list(Kind.TASK, source.workspace)) if (other.get("project").equals(source.id))
                    rank = Math.max(rank, Double.parseDouble(other.get("rank")) + 1024);
                created.set("rank", Double.toString(rank), 0);
                for (Map.Entry<String, Object> field : MiraTools.object(args, "fields").entrySet()) created.set(field.getKey(), (String) field.getValue(), 0);
                command.patches.add(created); break;
            case "mira_update_task":
                Patch patch = Patch.edit(source);
                Map<String, Object> expected = MiraTools.object(args, "expected");
                for (Map.Entry<String, Object> field : MiraTools.object(args, "fields").entrySet())
                    patch.set(field.getKey(), (String) field.getValue(), ((Number) expected.get(field.getKey())).longValue());
                command.patches.add(patch); break;
            case "mira_transition_task":
                Entity transition = null;
                for (Entity edge : Workflows.transitions(state, source, state.role(source.workspace, player)))
                    if (edge.get("logical").equals(args.get("transitionId"))) transition = edge;
                if (transition == null) throw new IllegalArgumentException("Transition is unavailable; re-read the task's transitions");
                command.patches.add(Patch.edit(source).set("status", transition.get("to"), ((Number) args.get("expectedStatusVersion")).longValue()));
                command.arguments.put("transition." + source.id, transition.get("logical")); break;
            case "mira_add_comment":
                command.patches.add(new Patch(request, Kind.COMMENT, source.workspace)
                    .set("task", source.id, 0).set("text", text(args, "text"), 0).set("archived", "false", 0)); break;
            default: throw new IllegalArgumentException("Unknown mutation " + tool);
        }
        while (receipts.size() >= 256) {
            String oldest = receipts.entrySet().stream().filter(entry -> !entry.getValue().result.equals("PENDING"))
                .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalStateException("Mira write history is full"));
            receipts.remove(oldest);
        }
        Receipt receipt = new Receipt(tool, args, command.workspace, taskId);
        receipts.put(request, receipt);
        try {
            backend.submit(session, dimension, command, (result, message) -> {
                receipt.result = result; receipt.message = message;
            });
        } catch (RuntimeException error) { receipt.result = "ERROR"; receipt.message = error.getMessage(); }
        return receipt.value(request);
    }

    private Map<String, Object> operation(State state, String player, String request) {
        Receipt receipt = receipts.get(request);
        if (receipt != null) {
            if (state.role(receipt.workspace, player) == Role.NONE) throw new IllegalStateException("Workspace is no longer accessible");
            return receipt.value(request);
        }
        for (Activity activity : state.activity) if (activity.id.equals(request)) {
            if (!activity.actor.equals(player)) throw new IllegalArgumentException("requestId belongs to another player");
            return map("requestId", request, "result", "OK", "savedRevision", activity.revision,
                "message", "Server activity confirms this request was committed");
        }
        return map("requestId", request, "result", "UNKNOWN", "message", "No receipt in this session. Re-read the task; this does not mean a previous write failed.");
    }
    private static Entity active(State state, String id, Kind kind) {
        Entity entity = state.require(id, kind);
        if (entity.archived() || state.require(entity.workspace, Kind.WORKSPACE).archived()
                || kind == Kind.TASK && state.require(entity.get("project"), Kind.PROJECT).archived())
            throw new IllegalArgumentException("Entity is archived; restore it in Mira first");
        return entity;
    }
    private static Map<String, Object> page(List<Map<String, Object>> entries, Map<String, Object> args) {
        int offset = number(args, "offset", 0), limit = number(args, "limit", 20);
        int from = Math.min(offset, entries.size()), end = from + Math.min(limit, entries.size() - from);
        return map("entries", new ArrayList<>(entries.subList(from, end)), "total", entries.size(), "offset", offset,
            "nextOffset", end < entries.size() ? end : null);
    }
    private static String text(Map<String, Object> args, String key) { return (String) args.getOrDefault(key, ""); }
    private static int number(Map<String, Object> args, String key, int fallback) { return ((Number) args.getOrDefault(key, fallback)).intValue(); }
    private static final class Receipt {
        final String tool, workspace, task;
        final Map<String, Object> arguments;
        String result = "PENDING", message = "Queued; poll mira_get_operation for the server's acknowledgment";
        Receipt(String tool, Map<String, Object> arguments, String workspace, String task) {
            this.tool = tool; this.arguments = new LinkedHashMap<>(arguments); this.workspace = workspace; this.task = task;
        }
        Map<String, Object> value(String id) { return map("requestId", id, "taskId", task, "result", result, "message", message); }
    }
}
