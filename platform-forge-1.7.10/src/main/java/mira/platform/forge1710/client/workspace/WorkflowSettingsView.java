package mira.platform.forge1710.client.workspace;

import java.util.*;
import java.util.stream.Collectors;
import mira.core.jira.Model.*;
import mira.core.jira.Workflows;
import mira.platform.forge1710.client.ui.TextInput;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import net.minecraft.client.gui.FontRenderer;
import static mira.platform.forge1710.client.ui.Ui.*;

/** Workflow graph, inspector, mappings and publication review composed from shared controls. */
final class WorkflowSettingsView {
    private final WorkspaceSettings actions;
    private final TextInput name, x, y;

    WorkflowSettingsView(WorkspaceSettings actions, FontRenderer font) {
        this.actions = actions;
        name = new TextInput(font, 80, "Name", () -> actions.graphName, value -> actions.graphName = value)
            .onSubmit(() -> { if (actions.graph == null) actions.addStatus(); else actions.saveGraph(); });
        x = new TextInput(font, 5, "0", () -> actions.x, value -> actions.x = value);
        y = new TextInput(font, 5, "0", () -> actions.y, value -> actions.y = value);
    }

    void workflow(Node content, SettingsLayout layout, Ui ui) {
        java.util.function.Consumer<String> selectGraph = id -> { actions.graphSelect(id); ui.revealAnchor("workflow-inspector"); };
        Entity workflow = actions.liveSelected();
        List<Entity> statuses = Workflows.nodes(actions.remote().state, workflow, true);
        List<Entity> transitions = Workflows.edges(actions.remote().state, workflow, true);
        content.add(rule(), layout.section("Workflow draft", "Draft changes are saved as you apply them. Publish when the workflow is ready for your team's issues."));
        if (layout.width >= 320) {
            List<Ui.GraphPoint> points = new ArrayList<>();
            points.add(new Ui.GraphPoint("*", "Any status", 0, 0, MUTED, false));
            for (Entity e : statuses) points.add(new Ui.GraphPoint(e.get("logical"), e.get("name")
                + (e.get("logical").equals(workflow.get("draftInitial")) ? " (start)" : ""),
                Integer.parseInt(e.get("x")), Integer.parseInt(e.get("y")), color(e.get("category")), actions.graph != null && actions.graph.id.equals(e.id)));
            List<Ui.GraphLink> links = new ArrayList<>();
            for (Entity e : transitions) links.add(new Ui.GraphLink(e.id, e.get("from"), e.get("to")));
            content.add(Ui.graph(layout.width, 190, points, links, selectGraph, actions::connect, actions::moveNode),
                layout.copy("Drag between statuses to connect them. Drag into empty space to move a status. Select a status or transition to edit it below."));
        } else content.add(layout.copy("Select a status to edit it. Use Connect statuses below to add a transition."));

        content.add(heading("Statuses (" + statuses.size() + ")"));
        for (Entity e : statuses) {
            boolean initial = e.get("logical").equals(workflow.get("draftInitial"));
            content.add(choiceRow(e.get("name"), category(e.get("category")) + (initial ? " / Starting status" : ""),
                actions.graph != null && actions.graph.id.equals(e.id), () -> selectGraph.accept(e.id)));
        }
        content.add(layout.secondary("New status", true, () -> { actions.newStatus(); ui.focus(name); }));
        inspector(content, layout, workflow);

        content.add(rule(), layout.section("Transitions (" + transitions.size() + ")", "A transition controls who can move an issue between two statuses."));
        for (Entity e : transitions) content.add(choiceRow(e.get("name"), statusName(statuses, e.get("from")) + " > " + statusName(statuses, e.get("to")),
            actions.graph != null && actions.graph.id.equals(e.id), () -> selectGraph.accept(e.id)).hint(e.get("name"), "From " + statusName(statuses, e.get("from")),
                "To " + statusName(statuses, e.get("to")), "Minimum role: " + SettingsLayout.sentence(e.get("role"))));
        if (transitions.isEmpty()) content.add(layout.copy("No transitions yet. Connect statuses so issues can move through this workflow."));
        Map<String, String> from = statusChoices(statuses), to = statusChoices(statuses);
        from.put(unusedLabel(from, "Any status"), "*");
        content.add(layout.section("Connect statuses", "Use Any status to allow a transition from anywhere in the workflow.",
            layout.pair(labelled("From", select("From status", labelFor(from, actions.connectFrom, "Choose a status"), from.keySet().toArray(new String[0]), value -> actions.connectFrom = from.get(value))),
                labelled("To", select("To status", labelFor(to, actions.connectTo, "Choose a status"), to.keySet().toArray(new String[0]), value -> actions.connectTo = to.get(value)))),
            layout.secondary("Add transition", !actions.connectTo.isEmpty(), () -> { actions.connectSelected(); ui.revealAnchor("workflow-inspector"); })));
        publication(content, layout, "Publish workflow");
    }

    private void inspector(Node content, SettingsLayout layout, Entity workflow) {
        Entity selected = actions.graph;
        content.add(rule(), heading(selected == null ? "New status" : selected.kind == Kind.STATUS ? "Edit status" : "Edit transition").anchor("workflow-inspector"),
            labelled("Name", Ui.field(name)));
        if (selected == null) {
            content.add(layout.primary("Add status", !actions.graphName.trim().isEmpty(), actions::addStatus));
            return;
        }
        if (selected.kind == Kind.STATUS) {
            content.add(labelled("Progress category", select("Progress category", category(actions.category), new String[] {"To do", "In progress", "Done"},
                value -> actions.category = value.equals("To do") ? "TODO" : value.equals("Done") ? "DONE" : "IN_PROGRESS")),
                layout.pair(labelled("Graph X", Ui.field(x)), labelled("Graph Y", Ui.field(y))));
            boolean initial = selected.get("logical").equals(workflow.get("draftInitial"));
            content.add(layout.copy(initial ? "New issues start in this status." : "Make this the starting status to use it for newly created issues."),
                layout.secondary(initial ? "Starting status" : "Set as starting status", !initial, actions::initial));
        } else {
            content.add(labelled("Who can use this transition", select("Minimum role", SettingsLayout.sentence(actions.transitionRole), new String[] {"Editor", "Owner"},
                value -> actions.transitionRole = value.toUpperCase(Locale.ROOT))),
                layout.section("Required fields", "Issues must have these fields filled in before moving."));
            Set<String> required = new HashSet<>(Workflows.ids(actions.required));
            for (String field : new String[] {"title", "description", "assignee", "priority", "sprint", "epic"}) {
                boolean checked = required.contains(field);
                content.add(Ui.navigation((checked ? "[x] " : "[ ] ") + SettingsLayout.sentence(field), checked, () -> actions.toggleRequired(field)));
            }
        }
        boolean removable = selected.kind != Kind.STATUS || !selected.get("logical").equals(workflow.get("draftInitial"));
        content.add(layout.actions(layout.primary("Save " + (selected.kind == Kind.STATUS ? "status" : "transition"), !actions.graphName.trim().isEmpty(), actions::saveGraph),
            layout.secondary("Remove", removable, actions::removeGraph)));
    }

    void scheme(Node content, SettingsLayout layout) {
        Entity scheme = actions.liveSelected();
        content.add(rule(), layout.section("Workflow assignments", "The default workflow covers every issue type. Add an exception when a type needs a different path."));
        int count = 0;
        for (Entity e : actions.list(Kind.MAPPING)) if (!e.archived() && e.get("scheme").equals(scheme.id) && e.get("generation").equals("draft")) {
            count++;
            content.add(choiceRow(e.get("type").equals("*") ? "Default for all issue types" : actions.remote().name(e.get("type")),
                actions.remote().name(e.get("workflow")), e.get("type").equals(actions.mappingType), () -> actions.selectMapping(e)));
            if (!e.get("type").equals("*")) content.add(layout.secondary("Remove exception", true, () -> actions.archive(e)));
        }
        if (count == 0) content.add(layout.empty("Choose a default workflow", "Every scheme needs a default before it can be published."));
        Map<String, Entity> typeChoices = WorkspaceSettings.choices(actions.list(Kind.ISSUE_TYPE).stream().filter(e -> !e.archived()).collect(Collectors.toList()));
        Map<String, String> types = new LinkedHashMap<>(); types.put(unusedLabel(typeChoices, "Default for all types"), "*");
        for (Map.Entry<String, Entity> entry : typeChoices.entrySet()) types.put(entry.getKey(), entry.getValue().id);
        Map<String, Entity> flows = WorkspaceSettings.choices(actions.list(Kind.WORKFLOW).stream().filter(e -> !e.archived()).collect(Collectors.toList()));
        content.add(rule(), layout.section("Assign a workflow", "Save the assignment to this draft, then publish the scheme to apply it.",
            labelled("Issue type", select("Issue type", labelFor(types, actions.mappingType, "Choose an issue type"), types.keySet().toArray(new String[0]), value -> actions.mappingType = types.get(value))),
            labelled("Workflow", select("Workflow", WorkspaceSettings.choiceLabel(flows, actions.mappingWorkflow, "Choose a workflow"), flows.keySet().toArray(new String[0]), value -> actions.mappingWorkflow = flows.get(value).id)),
            layout.primary("Save assignment", !actions.mappingWorkflow.isEmpty(), actions::saveMapping)));
        publication(content, layout, "Publish scheme");
    }

    void publication(Node content, SettingsLayout layout, String action) {
        content.add(rule(), heading("Review & apply"));
        try {
            List<Entity> tasks = actions.migrationTasks();
            if (tasks.isEmpty()) content.add(layout.notice("Current statuses can stay", "No issues need a replacement status for this change.", SUCCESS));
            else {
                content.add(layout.notice(tasks.size() + " issues need a new status", "Review each replacement below. The configuration and status changes are saved together.", ACCENT));
                for (Entity task : tasks) {
                    Map<String, String> statuses = statusChoices(actions.migrationStatuses(task));
                    content.add(stack(3, text(task.get("key") + "  " + task.get("title")).hint(task.get("title")),
                        select("Replacement status for " + task.get("key"), labelFor(statuses, actions.migrations.get(task.id), "Choose a status"),
                            statuses.keySet().toArray(new String[0]), value -> actions.migrations.put(task.id, statuses.get(value)))));
                }
            }
            content.add(layout.primary(action, true, actions::publish));
        } catch (Rejected invalid) {
            content.add(layout.notice("Draft needs attention", invalid.getMessage(), ERROR), layout.primary(action, false, actions::publish));
        }
    }

    private static Map<String, String> statusChoices(List<Entity> statuses) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Entity> e : WorkspaceSettings.choices(statuses).entrySet()) result.put(e.getKey(), e.getValue().get("logical"));
        return result;
    }
    private static String unusedLabel(Map<String, ?> choices, String label) {
        while (choices.containsKey(label)) label += " (all)";
        return label;
    }
    private static String labelFor(Map<String, String> choices, String value, String empty) {
        for (Map.Entry<String, String> entry : choices.entrySet()) if (entry.getValue().equals(value)) return entry.getKey();
        return empty;
    }
    private static String statusName(List<Entity> statuses, String id) {
        if (id.equals("*")) return "Any status";
        for (Entity e : statuses) if (e.get("logical").equals(id)) return e.get("name");
        return "Removed status";
    }
    private static String category(String value) { return value.equals("TODO") ? "To do" : value.equals("DONE") ? "Done" : "In progress"; }
    private static int color(String value) { return value.equals("DONE") ? SUCCESS : value.equals("TODO") ? MUTED : ACCENT; }
}
