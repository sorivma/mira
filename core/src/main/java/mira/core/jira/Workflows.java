package mira.core.jira;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mira.core.jira.Model.*;
import mira.core.taskbody.*;

/** Workflow schemes and transition checks, shared by board moves and every other task mutation. */
public final class Workflows {
    private Workflows() {}
    public static boolean active(Entity e) { return !e.archived(); }
    public static long draftVersion(State s, Entity root) {
        long result = root.version("draftInitial");
        for (Entity e : s.entities.values()) if (e.workspace.equals(root.workspace) && e.get("generation").equals("draft")
            && (e.get("workflow").equals(root.id) || e.get("scheme").equals(root.id)))
            for (long version : e.versions.values()) result = Math.max(result, version);
        return result;
    }
    public static List<String> ids(String value) {
        return value.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(value.split(",", -1)));
    }
    public static Entity workflow(State s, Entity project, String type) {
        Entity scheme = s.require(project.get("scheme"), Kind.SCHEME);
        String workflow = "";
        for (Entity mapping : s.list(Kind.MAPPING, project.workspace)) if (active(mapping)
                && mapping.get("scheme").equals(scheme.id) && mapping.get("generation").equals(scheme.get("generation"))) {
            if (mapping.get("type").equals(type)) return s.require(mapping.get("workflow"), Kind.WORKFLOW);
            if (mapping.get("type").equals("*")) workflow = mapping.get("workflow");
        }
        return s.require(workflow, Kind.WORKFLOW);
    }
    public static List<Entity> nodes(State s, Entity workflow, boolean draft) {
        return graph(s, workflow, Kind.STATUS, draft);
    }
    public static List<Entity> edges(State s, Entity workflow, boolean draft) {
        return graph(s, workflow, Kind.TRANSITION, draft);
    }
    private static List<Entity> graph(State s, Entity workflow, Kind kind, boolean draft) {
        List<Entity> result = new ArrayList<>();
        String generation = draft ? "draft" : workflow.get("generation");
        for (Entity e : s.list(kind, workflow.workspace)) if (active(e) && e.get("workflow").equals(workflow.id)
                && e.get("generation").equals(generation)) result.add(e);
        return result;
    }
    public static Entity status(State s, Entity workflow, String logical) {
        for (Entity e : nodes(s, workflow, false)) if (e.get("logical").equals(logical)) return e;
        throw new Rejected("Status is not in the issue's published workflow");
    }
    public static boolean done(State s, Entity task) {
        Entity p = s.require(task.get("project"), Kind.PROJECT);
        return "DONE".equals(status(s, workflow(s, p, task.get("type")), task.get("status")).get("category"));
    }
    public static List<Entity> transitions(State s, Entity task, Role role) {
        Entity workflow = workflow(s, s.require(task.get("project"), Kind.PROJECT), task.get("type"));
        List<Entity> result = new ArrayList<>();
        for (Entity e : edges(s, workflow, false)) if ((e.get("from").equals("*") || e.get("from").equals(task.get("status")))
                && role.ordinal() >= Role.valueOf(e.get("role")).ordinal() && requirements(s, task, e)) result.add(e);
        return result;
    }
    private static boolean requirements(State s, Entity task, Entity edge) {
        for (String field : ids(edge.get("required"))) if (task.get(field).trim().isEmpty()) {
            if (field.equals("description") && s.list(Kind.BLOCK, task.workspace).stream().anyMatch(e -> !e.archived() && e.get("task").equals(task.id) && content(BodyValues.decode(e.get("content"))))) continue;
            return false;
        }
        return true;
    }
    private static boolean content(TaskWidget widget) {
        if (widget instanceof NoteWidget) return !((NoteWidget) widget).text.trim().isEmpty();
        if (widget instanceof ChecklistWidget) return !((ChecklistWidget) widget).steps.isEmpty();
        if (widget instanceof MaterialsWidget) return !((MaterialsWidget) widget).materials.isEmpty();
        if (widget instanceof LinksWidget) return !((LinksWidget) widget).blockers.isEmpty();
        return widget instanceof LocationWidget;
    }
    public static void transition(State s, Entity before, Entity after, Role role, String transition) {
        if (before.get("status").equals(after.get("status")) && transition.isEmpty()) return;
        // Requirements are evaluated against the proposed fields, but the edge starts at the saved status.
        Entity candidate = after.copy(); candidate.fields.put("status", before.get("status"));
        List<Entity> choices = new ArrayList<>();
        for (Entity e : transitions(s, candidate, role)) if (e.get("to").equals(after.get("status"))) choices.add(e);
        if (!transition.isEmpty()) choices.removeIf(e -> !e.get("logical").equals(transition));
        if (choices.isEmpty()) throw new Rejected("Transition is not allowed, or its required fields are missing");
        if (choices.size() > 1) throw new Rejected("Choose which transition to perform");
    }
}
