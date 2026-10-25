package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.ui.TextInput;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import net.minecraft.client.gui.FontRenderer;

import static mira.platform.forge1710.client.ui.Ui.*;

/** Title-first creation; optional fields never stand between a title and Create. */
final class IssueEditorView {
    private final WorkspaceSession session;
    private final Ui.Scroll scroll = new Ui.Scroll();
    private final TextInput title;
    private IssueDraft boundDraft, displayedDraft;
    private boolean moreFields;
    private final DocumentView document;

    IssueEditorView(WorkspaceSession session, FontRenderer font) {
        this.session = session;
        document = new DocumentView(session, font, true);
        title = new TextInput(font, 160, "What needs to be done?",
            () -> boundDraft.title, value -> { boundDraft.title = value; boundDraft.validation = ""; });
    }

    void focusTitle(Ui ui) {
        if (session.draft() == null) return;
        boundDraft = session.draft();
        scroll.reset();
        ui.focus(title);
    }

    Node build(int width, Ui ui) {
        IssueDraft draft = session.draft();
        boundDraft = draft;
        if (displayedDraft != draft) {
            moreFields = draft.original != null;
            displayedDraft = draft;
        }
        Node documentFlow = document.build(ui, width - 12), documentTools = document.toolbar(ui, width - 12);
        Node form = column(6).pad(2).scroll(scroll);
        form.add(row(4).height(20).add(text(draft.project.name + " / " + draft.status.label, MUTED),
                select("Task template", draft.template, TaskTemplates.NAMES, value -> { TaskTemplates.apply(draft, value); session.draftType(draft.type); }).width(90)),
            IssueWidgets.property("Title (required)", Ui.field(title)),
            heading("Description"), documentFlow,
            IssueWidgets.property("Assignee", WorkspacePlayers.assignee(draft.assignee, session.self(), value -> draft.assignee = value)),
            button(moreFields ? "Fewer fields" : "More fields", () -> moreFields = !moreFields).width(92));
        if (moreFields) form.add(
            row(6).height(33).add(
                IssueWidgets.property("Type", select("Type", draft.type, session.types(), session::draftType)),
                IssueWidgets.property("Priority", select("Priority", draft.priority, WorkspaceData.PRIORITIES, value -> draft.priority = value))),
            IssueWidgets.property("Sprint", select("Sprint", draft.sprint, session.planningSprints(), value -> draft.sprint = value)));
        Node actions = row(5).height(20).add(space(), button("Cancel", session::cancelEditor).width(50));
        if (draft.original == null) actions.add(button("Create + next", () -> save(ui, true)).width(87).hint("Create and keep adding", "Ctrl+Enter; reuses assignee, type, priority and sprint"));
        actions.add(button(draft.original == null ? "Create" : "Save", () -> save(ui, false)).width(60)
            .hint(draft.original == null ? "Create issue" : "Save changes", "Enter"));
        String help = draft.original == null ? "Enter: create   Ctrl+Enter: create next" : "Enter: save changes";
        String feedback = !draft.validation.isEmpty() ? draft.validation : session.notice().isEmpty() ? help : session.notice();
        if (width < 260) {
            actions = column(4).height(draft.original == null ? 44 : 20).add(row(4).height(20).add(
                button("Cancel", session::cancelEditor), button(draft.original == null ? "Create" : "Save", () -> save(ui, false))));
            if (draft.original == null) actions.add(button("Create + next", () -> save(ui, true)));
        }
        return column(6).add(documentTools, form, text(feedback, draft.validation.isEmpty() ? MUTED : ERROR).height(12), actions);
    }

    void save(Ui ui, boolean another) {
        if (session.draft() == null) return;
        boolean keepCreating = another && session.draft() != null && session.draft().original == null;
        boolean saved = keepCreating ? session.saveAndCreateAnother() : session.saveDraft();
        if (!saved || keepCreating) focusTitle(ui);
        else { title.focus(false); ui.blur(); }
    }
    void insertBlock(Ui ui) { document.chooseInsert(ui); }
    void submit(Ui ui, boolean another) {
        if (!another && document.ownsInput()) document.paragraph(ui);
        else save(ui, another);
    }
}
