package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.ui.TextInput;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.WorkspaceSession.View;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Status;
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

    WorkspaceShell(WorkspaceSession session, FontRenderer font, Runnable close) {
        this.session = session;
        this.close = close;
        board = new BoardView(session);
        backlog = new BacklogView(session);
        issues = new IssueListView(session);
        details = new IssueDetailsView(session, font);
        editor = new IssueEditorView(session, font);
        search = new TextInput(font, 128, "Search issues", session::query, session::query);
    }

    Node build(int width, Ui ui) {
        Node frame = column(6).panel().pad(8);
        Node root = column(0).add(navigation(width), frame);
        if (session.draft() != null) {
            frame.add(editor.build(width - 16, ui), text("Local demo; changes are not saved or shared.", MUTED).height(12));
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
            body.add(session.view() == View.BOARD ? board.build(contentWidth, status -> create(ui, status))
                : session.view() == View.BACKLOG ? backlog.build() : issues.build());
            if (inspector) body.add(details.build(260, true, () -> edit(ui), ui).width(260));
        }
        Node footer = row(5).height(20).add(demoLabel());
        if (session.canUndo()) footer.add(button("Undo", session::undo).width(38).hint(session.undoLabel(), "Ctrl+Z outside a text field"));
        frame.add(body, footer.add(button("Done", close).width(44)));
        return root;
    }

    private Node navigation(int width) {
        Node tabs = row(3).height(28);
        if (session.draft() != null) return tabs.add(
            tab("Issue editor", new ItemStack(Items.writable_book), true, null).width(130), space());
        for (View view : View.values()) {
            ItemStack icon = new ItemStack(view == View.BOARD ? Items.book : view == View.BACKLOG ? Items.map : Items.paper);
            tabs.add(tab(view.label, icon, session.view() == view, () -> session.navigate(view)).width(Math.min(100, (width - 9) / 3)));
        }
        return tabs.add(space());
    }

    private Node header(int width, Ui ui) {
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
            select("Sprint", session.sprint(), new String[] {"All work", "Backlog", "Sprint 4", "Sprint 5"}, session::sprintFilter));
        return row(4).height(20).add(
            heading(session.view().label),
            playerSelect("Assignee", session.assignee(), WorkspacePlayers.filters(session.assignee(), session.self()), session::assigneeFilter).width(100),
            select("Sprint", session.sprint(), new String[] {"All work", "Backlog", "Sprint 4", "Sprint 5"}, session::sprintFilter).width(78),
            button("Reset", session::clearFilters).width(38));
    }

    private Node demoLabel() {
        return text(session.notice().isEmpty() ? "Demo project" : session.notice() + " / Demo", MUTED)
            .hint("Local demo workspace", "Issues and drafts are not saved or shared.");
    }

    private void edit(Ui ui) { session.editIssue(); editor.focusTitle(ui); }
    void create(Ui ui) { create(ui, session.view() == View.BOARD ? board.creationStatus() : Status.OPEN); }
    private void create(Ui ui, Status status) {
        if (session.draft() != null) return;
        session.createIssue(status);
        editor.focusTitle(ui);
    }
    void focusSearch(Ui ui) { if (session.draft() == null) ui.focus(search); }
    void submit(Ui ui, boolean another) {
        if (session.draft() != null) editor.submit(ui, another);
        else if (session.documentDraft() != null) {
            if (another) { session.saveDocument(); ui.blur(); } else details.paragraph(ui);
        }
        else if (session.selected() != null && !details.submitInline()) details.submitComment();
    }
    void insertBlock(Ui ui) {
        if (session.draft() != null) editor.insertBlock(ui);
        else if (session.selected() != null) details.insertBlock(ui);
    }
}
