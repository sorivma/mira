package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.ui.TextInput;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.WorkspaceSession.View;
import mira.platform.forge1710.client.workspace.WorkspaceData.Status;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import static mira.platform.forge1710.client.ui.Ui.*;

/** Inventory-style workspace chrome; feature views only receive the space they can use. */
final class WorkspaceShell {
    private final WorkspaceSession session;
    private final Runnable close;
    private final BoardView board;
    private final BacklogView backlog;
    private final IssueListView issues;
    private final IssueDetailsView details;
    private final IssueEditorView editor;
    private final TextInput search;
    private final WorkspaceSettingsView settings;
    private boolean managing;

    WorkspaceShell(WorkspaceSession session, FontRenderer font, Runnable close) {
        this.session = session;
        this.close = close;
        board = new BoardView(session);
        backlog = new BacklogView(session);
        issues = new IssueListView(session);
        details = new IssueDetailsView(session, font);
        editor = new IssueEditorView(session, font);
        settings = session.remote == null ? null : new WorkspaceSettingsView(session, font);
        search = new TextInput(font, 128, "Search issues", session::query, session::query);
    }

    Node build(int width, Ui ui) {
        Node frame = column(6).panel().pad(8);
        Node root = column(0).add(navigation(width), frame);
        if (session.remote != null && !session.remote.backend.ready()) { frame.add(text(session.remote.backend.status()), space(), button("Done", close).height(20)); return root; }
        if (session.saving() && !settingsOpen()) { frame.add(heading("Saving to world..."), text(session.notice()).hint(session.notice()), text(session.remote.backend.status()), space(), button("Done", close).height(20)); return root; }
        if (session.remote != null && (session.draft() != null || session.documentDraft() != null)) {
            String projectId = session.draft() != null ? session.draft().project.id : session.documentIssue().project.id;
            String taskId = session.draft() != null ? session.draft().original == null ? "" : session.draft().id : session.documentIssue().id;
            mira.core.jira.Model.Entity project = session.remote.state.entities.get(projectId), task = session.remote.state.entities.get(taskId);
            mira.core.jira.Model.Entity archived = project != null && project.archived() ? project : task != null && task.archived() ? task : null;
            if (archived != null) {
                frame.add(heading("This work was archived"), text("Your draft is retained. Restore the archived parent to continue."),
                    row(4).height(20).add(button("Restore " + archived.kind, () -> session.remote.archive(archived, false)),
                        button("Discard draft", () -> { session.cancelEditor(); session.cancelDocument(); })), space(), savedLabel());
                return root;
            }
        }
        if (settingsOpen()) {
            if (session.remote.conflict) frame.add(stack(4, text("This setting changed while you were editing", ERROR),
                row(4).height(20).add(button("Keep my edits", session.remote::rebaseConflict),
                    button("Reload saved", () -> { session.remote.discardConflict(); settings.reload(); }))));
            frame.add(settings.build(width - 16, () -> { if (session.project() == null) close.run(); else managing = false; }, ui).interactive(!session.saving()),
                row(5).height(20).add(savedLabel(), button("Done", close).width(44))); return root;
        }
        if (session.remote != null && session.remote.conflict) frame.add(row(4).height(20).add(text("Conflict: your draft is kept", ERROR),
            button("Keep my edits", session.remote::rebaseConflict).width(80), button("Reload saved", session.remote::discardConflict).width(80)));
        if (session.draft() != null) {
            frame.add(editor.build(width - 16, ui), text("Draft is local until Save. Saved changes synchronize immediately.", MUTED).height(12));
            return root;
        }
        frame.add(header(width, ui));
        boolean inspector = session.selected() != null && !session.expanded() && width >= 860;
        if (session.selected() == null || inspector) frame.add(toolbar(width));
        int workWidth = width - 16;
        Node body = row(6);
        if (session.selected() != null && !inspector) {
            body.add(details.build(workWidth, false, () -> edit(ui), ui));
        } else {
            int contentWidth = workWidth - (inspector ? 266 : 0);
            body.add(session.view() == View.BOARD ? board.build(contentWidth, status -> create(ui, status), ui)
                : session.view() == View.BACKLOG ? backlog.build() : issues.build());
            if (inspector) body.add(details.build(260, true, () -> edit(ui), ui).width(260));
        }
        Node footer = row(5).height(20).add(savedLabel());
        if (session.canUndo()) footer.add(button("Undo", session::undo).width(38).hint(session.undoLabel(), "Ctrl+Z outside a text field"));
        frame.add(body, footer.add(button("Done", close).width(44)));
        return root;
    }

    private Node navigation(int width) {
        Node tabs = row(3).height(28);
        if (settingsOpen() || session.saving()) return tabs.add(tab(settingsOpen() ? "Workspace settings" : "Saving", new ItemStack(Items.book), true, null).width(Math.min(160, width)), space());
        if (session.draft() != null) return tabs.add(
            tab("Issue editor", new ItemStack(Items.writable_book), true, null).width(130), space());
        for (View view : View.values()) {
            ItemStack icon = new ItemStack(view == View.BOARD ? Items.book : view == View.BACKLOG ? Items.map : Items.paper);
            tabs.add(tab(view.label, icon, session.view() == view, () -> session.navigate(view)).width(Math.min(100, (width - 9) / 3)));
        }
        return tabs.add(space());
    }

    private Node header(int width, Ui ui) {
        if (session.remote != null) {
            java.util.List<mira.core.jira.Model.Entity> available = new java.util.ArrayList<>();
            for (mira.core.jira.Model.Entity e : session.remote.state.entities.values()) if (e.kind == mira.core.jira.Model.Kind.WORKSPACE && !e.archived()) available.add(e);
            java.util.Map<String, mira.core.jira.Model.Entity> workspaces = WorkspaceSettings.choices(available);
            Node scope = row(4).height(20).add(
                select("Workspace", WorkspaceSettings.choiceLabel(workspaces, session.remote.workspace, "Choose a workspace"), workspaces.keySet().toArray(new String[0]), value -> session.remote.switchWorkspace(workspaces.get(value).id)),
                select("Project", session.project().name, session.projectNames(), session::project),
                button("Settings", this::openSettings).width(54));
            Node tools = row(4).height(20).add(Ui.field(search));
            if (session.writable()) tools.add(button("New issue", () -> create(ui)).width(68));
            return column(4).height(44).add(scope, tools);
        }
        if (width < 280) return column(4).height(44).add(row(4).height(20).add(
            select("Project", session.project().name, session.projectNames(), session::project),
            button("New", () -> create(ui)).width(38)), Ui.field(search));
        return row(5).height(20).add(
            select("Project", session.project().name, session.projectNames(), session::project).width(width < 400 ? 96 : 122),
            Ui.field(search), button("New issue", () -> create(ui)).width(68).hint("New issue", "C or Ctrl+N"));
    }

    private Node toolbar(int width) {
        if (width < 280) return column(4).height(44).add(row(4).height(20).add(
            playerSelect("Assignee", session.assignee(), WorkspacePlayers.filters(session.assignee(), session.self()), session::assigneeFilter),
            button("Reset", session::clearFilters).width(38)),
            select("Sprint", session.sprint(), session.sprintFilters(), session::sprintFilter));
        return row(4).height(20).add(
            heading(session.view().label),
            playerSelect("Assignee", session.assignee(), WorkspacePlayers.filters(session.assignee(), session.self()), session::assigneeFilter).width(100),
            select("Sprint", session.sprint(), session.sprintFilters(), session::sprintFilter).width(78),
            button("Reset", session::clearFilters).width(38));
    }

    private Node savedLabel() {
        String label = session.notice().isEmpty() ? session.remote == null ? "Local fixture" : session.remote.backend.status() : session.notice();
        return text(label, MUTED).hint(label);
    }

    private void edit(Ui ui) { session.editIssue(); editor.focusTitle(ui); }
    private void openSettings() {
        if (session.documentDraft() != null) { session.remoteNotice("Save or cancel the description before opening settings."); return; }
        managing = true;
    }
    private boolean settingsOpen() { return settings != null && (managing || session.project() == null); }
    boolean back() {
        if (session.saving()) return true;
        if (!settingsOpen()) return session.back();
        if (settings.back()) return true;
        if (managing && session.project() != null) { managing = false; return true; }
        return false;
    }
    void create(Ui ui) { create(ui, session.view() == View.BOARD ? board.creationStatus() : Status.OPEN); }
    private void create(Ui ui, Status status) {
        if (settingsOpen() || session.draft() != null || !session.writable() || session.project() == null) return;
        managing = false;
        session.createIssue(status);
        editor.focusTitle(ui);
    }
    void focusSearch(Ui ui) { if (settingsOpen()) settings.focusSearch(ui); else if (session.draft() == null) ui.focus(search); }
    void submit(Ui ui, boolean another) {
        if (settingsOpen()) { if (!session.saving()) settings.submit(); return; }
        if (session.draft() != null) editor.submit(ui, another);
        else if (session.documentDraft() != null) {
            if (another) { session.saveDocument(); ui.blur(); } else details.paragraph(ui);
        }
        else if (session.selected() != null && !details.submitInline()) details.submitComment();
    }
    void insertBlock(Ui ui) {
        if (settingsOpen()) return;
        if (session.draft() != null) editor.insertBlock(ui);
        else if (session.selected() != null) details.insertBlock(ui);
    }
}
