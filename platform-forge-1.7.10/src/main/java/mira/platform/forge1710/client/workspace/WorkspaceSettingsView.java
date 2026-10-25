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

/** Settings navigation and composition; administration actions belong to WorkspaceSettings. */
final class WorkspaceSettingsView {
    private final WorkspaceSettings actions;
    private final FontRenderer font;
    private final TextInput name, key, search;
    private final WorkflowSettingsView workflows;
    private final Ui.Scroll scroll = new Ui.Scroll(), navigationScroll = new Ui.Scroll();
    private SettingsLayout layout;
    private Ui ui;
    private String page = "";

    WorkspaceSettingsView(WorkspaceSession session, FontRenderer font) {
        this.font = font;
        actions = new WorkspaceSettings(session);
        name = new TextInput(font, 80, "Give it a clear name", () -> actions.name, actions::changeName).onSubmit(actions::saveForm);
        key = new TextInput(font, 32, "", () -> actions.key, actions::changeKey).onSubmit(actions::saveForm);
        search = new TextInput(font, 128, "Search this section", () -> actions.query, value -> { actions.query = value; scroll.reset(); });
        workflows = new WorkflowSettingsView(actions, font);
    }

    Node build(int width, Runnable back, Ui ui) {
        this.ui = ui;
        actions.observe();
        String identity = actions.remote().workspace + "/" + actions.tab;
        if (!page.equals(identity)) { scroll.reset(); page = identity; }
        boolean wide = width >= 510;
        int contentWidth = wide ? width - 138 : width;
        layout = new SettingsLayout(font, Math.max(1, Math.min(620, contentWidth - 8)));
        Node root = column(7);
        if (!wide) root.add(row(5).height(22).add(button("Back", back).width(40), sectionPicker()));
        Node body = row(10);
        if (wide) body.add(navigation(back).width(128));
        Node main = column(7);
        if (wide) main.add(stack(3, heading(SettingsLayout.title(actions.tab)), text(context(), MUTED)));
        if (needsProject()) main.add(select("Project", actions.session.project() == null ? "No projects" : actions.session.project().name,
            actions.session.projectNames(), actions::chooseProject));
        boolean available = available();
        if (available && actions.kind() != null && actions.kind() != Kind.WORKSPACE) main.add(entityPicker());
        Node content = column(10).scroll(scroll).width(Math.min(628, contentWidth));
        if (available && (actions.tab.equals("Archives") || actions.tab.equals("Activity"))) {
            if (wide) main.add(historyFilters()); else content.add(historyFilters());
        }
        if (!available) unavailable(content);
        else if (actions.creating) createForm(content);
        else if (actions.tab.equals("Workspaces")) workspaces(content);
        else if (actions.tab.equals("Access")) access(content);
        else if (actions.tab.equals("Archives")) archives(content);
        else if (actions.tab.equals("Activity")) activity(content);
        else if (actions.selected == null) noSelection(content);
        else {
            entityForm(content);
            if (!actions.selected.archived()) {
                if (actions.tab.equals("Projects")) projects(content);
                else if (actions.tab.equals("Sprints")) sprints(content);
                else if (actions.tab.equals("Workflows")) workflows.workflow(content, layout, ui);
                else if (actions.tab.equals("Schemes")) workflows.scheme(content, layout);
                else if (actions.tab.equals("Board")) board(content);
            }
        }
        main.add(content);
        if (!actions.error.isEmpty()) main.add(text(actions.error, ERROR).hint(actions.error));
        if (available && actions.kind() != null && editable() && (actions.creating || actions.selected != null && !actions.selected.archived())) main.add(formFooter());
        return root.add(body.add(main));
    }

    private Node navigation(Runnable back) {
        Node rail = column(7).add(button(actions.session.project() == null ? "Close settings" : "Back to workspace", back).height(22));
        Node links = column(2).scroll(navigationScroll);
        addGroup(links, "Workspace", "Workspaces", "Access");
        addGroup(links, "Planning", "Projects", "Sprints", "Board");
        addGroup(links, "Workflow", "Types", "Workflows", "Schemes");
        addGroup(links, "History", "Archives", "Activity");
        return rail.add(links);
    }
    private void addGroup(Node links, String title, String... tabs) {
        links.add(text(title, MUTED).height(20));
        for (String tab : tabs) links.add(Ui.navigation(SettingsLayout.title(tab), actions.tab.equals(tab), () -> open(tab)));
    }
    private Node sectionPicker() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String tab : WorkspaceSettings.TABS) labels.put(SettingsLayout.title(tab), tab);
        return select("Settings section", SettingsLayout.title(actions.tab), labels.keySet().toArray(new String[0]), value -> open(labels.get(value)));
    }
    private void open(String tab) { actions.tab(tab); scroll.reset(); }
    private String context() {
        RemoteWorkspace r = actions.remote();
        return r.workspace.isEmpty() ? "Set up a shared place for your world projects"
            : r.name(r.workspace) + " / " + SettingsLayout.sentence(r.role().name());
    }
    private boolean needsProject() { return actions.tab.equals("Sprints") || actions.tab.equals("Board"); }
    private boolean ownerOnly() { return Arrays.asList("Access", "Types", "Workflows", "Schemes").contains(actions.tab); }
    private boolean available() {
        return actions.tab.equals("Workspaces") || !actions.remote().workspace.isEmpty()
            && (!ownerOnly() || actions.remote().owner()) && (!needsProject() || actions.session.project() != null);
    }
    private boolean editable() {
        if (actions.kind() == Kind.WORKSPACE && actions.creating) return true;
        if (actions.kind() == Kind.WORKSPACE) return actions.selected != null
            && actions.remote().state.role(actions.selected.id, actions.remote().backend.player()) == Role.OWNER;
        return actions.remote().role().ordinal() >= Role.EDITOR.ordinal() && (!ownerOnly() || actions.remote().owner());
    }
    private void unavailable(Node content) {
        if (actions.remote().workspace.isEmpty()) content.add(layout.empty("Choose your workspace", "Create a workspace or open one you already belong to."),
            layout.secondary("Open workspaces", true, () -> open("Workspaces")));
        else if (ownerOnly() && !actions.remote().owner()) content.add(layout.notice("Managed by the owner",
            "The workspace owner manages " + SettingsLayout.title(actions.tab).toLowerCase(Locale.ROOT) + ". Your current role is "
                + SettingsLayout.sentence(actions.remote().role().name()) + ".", ACCENT));
        else content.add(layout.empty("A project comes first", "Create a project to organize its sprints and board columns."),
            layout.secondary("Open projects", true, () -> open("Projects")));
    }
    private Node entityPicker() {
        Map<String, Entity> choices = WorkspaceSettings.choices(actions.candidates(actions.kind()));
        String current = actions.creating ? "Creating a new " + SettingsLayout.singular(actions.kind())
            : WorkspaceSettings.choiceLabel(choices, actions.selected == null ? "" : actions.selected.id, "No " + SettingsLayout.title(actions.tab).toLowerCase(Locale.ROOT) + " yet");
        Node row = row(5).height(22).add(select("Choose " + SettingsLayout.singular(actions.kind()), current,
            choices.keySet().toArray(new String[0]), value -> { actions.select(choices.get(value)); scroll.reset(); }));
        if (editable()) row.add(button("New", () -> beginCreate(false)).width(42));
        return row;
    }
    private void beginCreate(boolean copy) { actions.beginCreate(copy); scroll.reset(); ui.focus(name); }
    private void noSelection(Node content) {
        String kind = SettingsLayout.singular(actions.kind());
        content.add(layout.empty("No " + kind + " selected", editable() ? "Choose one above, or use New to create a " + kind + "."
            : "Your workspace editors can create and manage these items."));
    }
    private void createForm(Node content) {
        Kind kind = actions.kind();
        String description = kind == Kind.WORKSPACE ? "Only you have access at first. A main project and a standard workflow are included. Invite players from People & access."
            : kind == Kind.PROJECT ? "Group related work under a permanent issue key. The project inherits workspace access."
            : kind == Kind.SPRINT ? "Plan a batch of work for this project. Start the sprint when your team is ready."
            : kind == Kind.COLUMN ? "Create a board column, then choose which workflow statuses it displays."
            : kind == Kind.ISSUE_TYPE ? "Name a category your team will recognize when creating an issue."
            : kind == Kind.WORKFLOW ? "Create a workflow with a draft you can shape before publishing." : "Choose a default workflow and add exceptions for individual issue types.";
        content.add(layout.section("New " + SettingsLayout.singular(kind), description, labelled("Name", Ui.field(name))));
        if (kind == Kind.PROJECT) content.add(labelled("Project key", Ui.field(key)), layout.copy("Use 2-12 letters or digits, starting with a letter. BASE produces issue keys like BASE-1. The key cannot change later."));
    }
    private void entityForm(Node content) {
        Entity e = actions.selected;
        if (e.archived()) {
            content.add(layout.notice("Archived " + SettingsLayout.singular(e.kind), "Restore this item before changing its settings.", MUTED));
            if (editable()) content.add(layout.secondary("Restore " + SettingsLayout.singular(e.kind), true, actions::archiveSelected));
            return;
        }
        if (editable()) content.add(labelled("Name", Ui.field(name)));
        else content.add(layout.notice("View only", "Editors and the owner can change these settings.", MUTED), heading(e.get("name")));
        if (e.kind == Kind.PROJECT) content.add(text("Issue key: " + e.get("key") + "-1, " + e.get("key") + "-2, ...", MUTED));
        if (e.kind == Kind.ISSUE_TYPE) content.add(layout.copy("Issue types describe the kind of work. Workflow schemes decide which workflow each type follows."));
        if (editable()) {
            Node tools = row(5).height(20).add(button("Archive", actions::archiveSelected).width(66).hint("Archive this " + SettingsLayout.singular(e.kind), "You can restore it from its selector."));
            if (e.kind == Kind.WORKFLOW || e.kind == Kind.SCHEME) tools.add(button("Duplicate", () -> beginCreate(true)).width(72).hint("Create a copy of the published configuration"));
            content.add(tools.add(space()));
        }
    }
    private Node formFooter() {
        String label = actions.creating ? "Create " + SettingsLayout.singular(actions.kind())
            : actions.kind() == Kind.COLUMN ? "Save column" : "Save name";
        Node primary = layout.primary(label, !actions.name.trim().isEmpty() && (actions.creating || actions.dirty()), actions::saveForm);
        Node cancel = layout.secondary(actions.creating ? "Cancel" : "Reset", actions.creating || actions.dirty(), actions.creating ? actions::cancelCreate : actions::reload);
        return stack(5, rule(), row(5).height(22).add(primary, cancel.width(48)));
    }

    private void workspaces(Node content) {
        RemoteWorkspace r = actions.remote();
        List<Entity> values = actions.candidates(Kind.WORKSPACE);
        content.add(layout.section("Your workspaces", "Each workspace has its own projects, people and workflows in this world."));
        if (values.isEmpty()) content.add(layout.empty("Make room for your next build", "Create a workspace, then turn your plans into issues your team can share."));
        for (Entity e : values) {
            boolean current = e.id.equals(r.workspace);
            String detail = e.archived() ? "Archived" : (current ? "Current workspace / " : "") + SettingsLayout.sentence(r.state.role(e.id, r.backend.player()).name());
            content.add(choiceRow(e.get("name"), detail, actions.selected != null && actions.selected.id.equals(e.id), () -> { actions.openWorkspace(e); scroll.reset(); }));
        }
        content.add(layout.secondary("New workspace", true, () -> beginCreate(false)));
        if (actions.selected == null) return;
        content.add(rule());
        if (actions.selected.archived()) { entityForm(content); return; }
        if (editable()) content.add(layout.section("Workspace name", "A name your team can recognize in the workspace picker.", labelled("Name", Ui.field(name))));
        else content.add(text("Owner: " + r.playerName(actions.selected.get("owner")), MUTED));
        if (r.owner()) content.add(layout.actions(layout.secondary("Manage people", true, () -> open("Access")),
            layout.secondary("Archive workspace", true, actions::archiveSelected)));
    }

    private void access(Node content) {
        RemoteWorkspace r = actions.remote(); Entity workspace = r.state.require(r.workspace, Kind.WORKSPACE);
        content.add(layout.section("Workspace members", "Access applies to every project in this workspace."),
            memberRow(r.playerName(workspace.get("owner")), "Owner", null));
        List<Entity> members = actions.list(Kind.MEMBER).stream().filter(e -> !e.archived()).collect(Collectors.toList());
        for (Entity e : members) content.add(memberRow(r.playerName(e.get("player")), SettingsLayout.sentence(e.get("role")), () -> {
            actions.choosePlayer(r.playerName(e.get("player"))); ui.revealAnchor("member-editor");
        }));
        if (members.isEmpty()) content.add(layout.copy("You're the only member. Give a player access below to start collaborating."));
        content.add(rule(), layout.section("Give a player access", "Players appear here after joining this world. They do not need to be online.").anchor("member-editor"),
            labelled("Player", playerSelect("Player", actions.player.isEmpty() ? "Choose a player" : actions.player, r.players(), actions::choosePlayer)),
            labelled("Role", select("Role", SettingsLayout.sentence(actions.role), new String[] {"Viewer", "Editor"}, value -> actions.role = value.toUpperCase(Locale.ROOT))),
            layout.copy(actions.role.equals("EDITOR") ? "Editors create and update tasks, projects, sprints and board columns." : "Viewers can read projects and tasks. They cannot change workspace content."));
        String playerId = actions.selectedPlayerId();
        boolean hasPlayer = !playerId.isEmpty();
        boolean isOwner = playerId.equals(workspace.get("owner"));
        boolean isMember = members.stream().anyMatch(e -> e.get("player").equals(playerId));
        content.add(layout.actions(layout.primary(isMember ? "Update role" : "Give access", hasPlayer && !isOwner, () -> actions.grant(false)),
            layout.secondary("Remove access", isMember && !isOwner, () -> actions.grant(true))));
        content.add(rule(), layout.section("Workspace ownership", "The owner manages people, issue types and workflows. Transferring ownership makes you an Editor."));
        if (actions.transferReview) content.add(layout.notice("Transfer to " + actions.player + "?", "You will need the new owner's help to manage access or transfer ownership again.", ERROR),
            layout.actions(layout.primary("Transfer ownership", hasPlayer && !isOwner, actions::transfer), layout.secondary("Keep ownership", true, () -> actions.transferReview = false)));
        else content.add(layout.secondary("Review ownership transfer", hasPlayer && !isOwner, () -> actions.transferReview = true));
    }
    private Node memberRow(String player, String role, Runnable select) {
        return row(6).height(26).add(Ui.player(player), text(role, MUTED).width(52),
            select == null ? text("", MUTED).width(44) : button("Edit", select).width(44));
    }

    private void projects(Node content) {
        content.add(rule(), layout.section("Project workflow", "The scheme chooses a workflow for each issue type in this project."));
        if (!actions.remote().owner()) { content.add(text(actions.remote().name(actions.selected.get("scheme")), MUTED)); return; }
        Map<String, Entity> schemes = choices(Kind.SCHEME);
        content.add(labelled("Workflow scheme", select("Workflow scheme", WorkspaceSettings.choiceLabel(schemes, actions.projectScheme, "Choose a scheme"),
            schemes.keySet().toArray(new String[0]), value -> actions.chooseScheme(schemes.get(value).id))));
        if (!actions.projectScheme.equals(actions.selected.get("scheme"))) workflows.publication(content, layout, "Apply scheme");
        else content.add(layout.copy("This scheme is already applied. Choose another to review any status changes before applying it."));
    }
    private void sprints(Node content) {
        Entity sprint = actions.liveSelected();
        List<Entity> tasks = actions.list(Kind.TASK).stream().filter(e -> !e.archived() && e.get("sprint").equals(sprint.id)).collect(Collectors.toList());
        long completed = tasks.stream().filter(e -> Workflows.done(actions.remote().state, e)).count();
        content.add(rule(), layout.section("Sprint progress", SettingsLayout.sentence(sprint.get("state")) + ": " + completed + " of " + tasks.size() + " issues complete.", progress(completed, tasks.size())));
        if (!editable()) return;
        if (sprint.get("state").equals("PLANNED")) {
            boolean active = actions.candidates(Kind.SPRINT).stream().anyMatch(e -> !e.archived() && e.get("state").equals("ACTIVE"));
            content.add(layout.copy(active ? "Complete the active sprint in this project before starting another." : "Start this sprint when your team is ready to work on its issues."),
                layout.primary("Start sprint", !active, actions::startSprint));
        } else if (sprint.get("state").equals("ACTIVE")) {
            List<String> destinations = new ArrayList<>(); destinations.add("Backlog");
            for (Entity e : actions.candidates(Kind.SPRINT)) if (!e.archived() && e.get("state").equals("PLANNED")) destinations.add(e.get("name"));
            content.add(layout.section("Complete sprint", "Choose where the " + (tasks.size() - completed) + " unfinished issues should go.",
                labelled("Move unfinished issues to", select("Destination", actions.destination, destinations.toArray(new String[0]), value -> actions.destination = value)),
                layout.primary("Complete sprint", true, actions::completeSprint)));
        } else content.add(layout.copy("This sprint is complete. Its finished issues stay here as a record of your work."));
    }
    private void board(Node content) {
        if (editable()) content.add(labelled("Column order", Ui.field(key)), layout.copy("Lower numbers appear first. Leave a gap between columns, such as 1024, 2048 and 3072."));
        content.add(rule(), layout.section("Statuses in this column", "Select the statuses to show together. Saving moves each selected status out of its previous column."));
        WorkspaceData.Status[] statuses = actions.session.statuses();
        if (statuses.length == 0) content.add(layout.empty("No statuses available", "Publish a workflow for this project's issue types to configure its board."));
        for (WorkspaceData.Status status : statuses) {
            boolean chosen = actions.columnStatuses.contains(status.id);
            String current = "Not assigned to a column";
            for (Entity e : actions.candidates(Kind.COLUMN)) if (!e.archived() && Workflows.ids(e.get("statuses")).contains(status.id)) current = "Currently in " + e.get("name");
            content.add(choiceRow((chosen ? "[x] " : "[ ] ") + status.label, current, chosen, editable() ? () -> actions.toggleStatus(status.id) : null));
        }
        content.add(text(actions.columnStatuses.size() + " statuses selected", MUTED));
    }
    private Map<String, Entity> choices(Kind kind) { return WorkspaceSettings.choices(actions.list(kind).stream().filter(e -> !e.archived()).collect(Collectors.toList())); }

    private Node historyFilters() {
        Node filter;
        if (actions.tab.equals("Archives")) filter = select("Item type", actions.archiveKind, new String[] {"All items", "Projects", "Issues", "Comments"}, value -> { actions.archiveKind = value; scroll.reset(); });
        else {
            Set<String> actors = new LinkedHashSet<>(); actors.add("Everyone");
            for (Activity a : actions.remote().state.activity) if (a.workspace.equals(actions.remote().workspace)) actors.add(a.name);
            filter = select("Changed by", actions.activityActor, actors.toArray(new String[0]), value -> { actions.activityActor = value; scroll.reset(); });
        }
        return layout.pair(labelled("Search", Ui.field(search)), labelled(actions.tab.equals("Archives") ? "Item type" : "Changed by", filter));
    }
    private boolean matches(String value) { return value.toLowerCase(Locale.ROOT).contains(actions.query.toLowerCase(Locale.ROOT).trim()); }
    private void archives(Node content) {
        int total = 0, shown = 0;
        for (Entity e : actions.remote().state.entities.values()) if (e.workspace.equals(actions.remote().workspace) && e.archived()
            && (e.kind == Kind.PROJECT || e.kind == Kind.TASK || e.kind == Kind.COMMENT)) {
            total++;
            String kind = e.kind == Kind.PROJECT ? "Projects" : e.kind == Kind.TASK ? "Issues" : "Comments";
            String label = e.kind == Kind.COMMENT ? e.get("text") : e.kind == Kind.TASK ? e.get("key") + "  " + e.get("title") : e.get("name");
            if ((!actions.archiveKind.equals("All items") && !actions.archiveKind.equals(kind)) || !matches(label)) continue;
            shown++;
            boolean canRestore = actions.session.writable() && (e.kind != Kind.COMMENT || actions.remote().owner() || e.get("author").equals(actions.remote().backend.player()));
            Node details = stack(3, text(SettingsLayout.sentence(e.kind.name()), MUTED), excerpt(label, 2));
            content.add(stack(5, details, layout.secondary("Restore", canRestore, () -> actions.archive(e)), rule()));
        }
        if (shown == 0) content.add(layout.empty(total == 0 ? "Nothing archived" : "No matching items", total == 0
            ? "Archived projects, issues and comments appear here. Restore an item to bring it back to the workspace."
            : "Try a different search or choose All items."));
    }
    private void activity(Node content) {
        List<Activity> values = actions.remote().state.activity;
        java.text.SimpleDateFormat days = new java.text.SimpleDateFormat("EEE, d MMM yyyy"), times = new java.text.SimpleDateFormat("HH:mm");
        String lastDay = ""; int shown = 0;
        for (int i = values.size() - 1; i >= 0; i--) {
            Activity a = values.get(i);
            if (!a.workspace.equals(actions.remote().workspace) || !matches(a.name + " " + a.summary)
                || !actions.activityActor.equals("Everyone") && !actions.activityActor.equals(a.name)) continue;
            String day = days.format(new Date(a.time));
            if (!day.equals(lastDay)) { content.add(heading(day)); lastDay = day; }
            Node entry = stack(3, text(times.format(new Date(a.time)) + "  " + a.name, MUTED), layout.copy(a.summary));
            if (!a.undoneBy.isEmpty()) entry.add(text("Undone", MUTED)).fitHeight();
            content.add(entry.hint(a.summary, "Changed by " + a.name)); shown++;
        }
        if (shown == 0) content.add(layout.empty("No activity to show", actions.query.isEmpty() && actions.activityActor.equals("Everyone")
            ? "Saved workspace changes appear here, newest first." : "Try a different search or choose Everyone."));
    }

    void focusSearch(Ui ui) { if (actions.tab.equals("Archives") || actions.tab.equals("Activity")) ui.focus(search); }
    void submit() { if (editable() && (actions.creating || actions.dirty())) actions.saveForm(); }
    boolean back() {
        if (actions.transferReview) { actions.transferReview = false; return true; }
        if (actions.creating) { actions.cancelCreate(); return true; }
        return false;
    }
    void reload() { actions.reload(); }
}
