package mira.platform.forge1710.client.workspace;

import java.util.Arrays;
import mira.platform.forge1710.client.ui.TextInput;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.WorkspaceData.Issue;
import mira.platform.forge1710.client.workspace.WorkspaceData.Status;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import static mira.platform.forge1710.client.ui.Ui.*;

/** Reading and discussion lead; properties move beside them when the viewport permits it. */
final class IssueDetailsView {
    private final WorkspaceSession session;
    private final FontRenderer font;
    private final Ui.Scroll scroll = new Ui.Scroll(), propertiesScroll = new Ui.Scroll();
    private final TextInput comment;
    private int selectionRevision = -1;
    private boolean moreDetails, activity;
    private final DocumentView document;
    private final TextInput inlineTitle;
    private String textTitle = "", textError = "";
    private boolean editingText;

    IssueDetailsView(WorkspaceSession session, FontRenderer font) {
        this.session = session;
        this.font = font;
        document = new DocumentView(session, font, false);
        inlineTitle = new TextInput(font, 160, "Title", () -> textTitle, value -> textTitle = value);
        comment = new TextInput(font, 500, "Write a comment", session::comment, session::comment).onSubmit(session::postComment);
    }

    Node build(int width, boolean inspector, Runnable edit, Ui ui) {
        Issue issue = session.selected();
        if (selectionRevision != session.selectionRevision()) {
            scroll.reset();
            propertiesScroll.reset();
            comment.focus(false);
            moreDetails = activity = false;
            editingText = false;
            selectionRevision = session.selectionRevision();
        }
        if (session.documentDraft() != null) editingText = false;
        Node header = row(4).height(20).add(
            button("Back", session::clearSelection).width(42), item(IssueWidgets.icon(issue)).slot(),
            text(issue.key, issue.status.color), session.writable() ? button("Edit", edit).width(36) : text("Read only").width(54),
            button(PinnedTaskHud.isPinned(issue) ? "Unpin" : "Pin", () -> PinnedTaskHud.toggle(issue)).width(38));
        if (session.remote != null && session.writable()) header.add(button("Archive", () -> session.remote.archive(issue.source, true)).width(48));
        if (inspector) header.add(button("Open", session::expand).width(36).hint("Expand issue"));
        boolean wide = width >= 520;
        int textWidth = Math.min(420, width - (wide ? 204 : 0) - 12);
        Node reading = column(7).pad(2).scroll(scroll);
        if (editingText) reading.add(Ui.field(inlineTitle),
            row(4).height(20).add(button("Save title", this::saveText), button("Cancel", () -> editingText = false)),
            text(textError.isEmpty() ? "Ctrl+Enter: save title" : textError, textError.isEmpty() ? MUTED : ERROR));
        else reading.add(IssueWidgets.paragraph(font, EnumChatFormatting.BOLD + issue.title, textWidth).width(textWidth)
            .click(() -> beginTitle(ui)).hint("Click to edit the title"));
        if (!wide) identity(reading, issue);
        Node documentFlow = document.build(ui, textWidth), documentTools = document.toolbar(ui, textWidth);
        reading.add(rule());
        if (session.documentDraft() == null) reading.add(documentTools.width(textWidth));
        reading.add(documentFlow.width(textWidth));
        reading.add(rule(), heading("Comments (" + issue.comments.size() + ")"));
        if (session.writable()) {
            Node commentRow = row(4).height(20).add(Ui.field(comment), button(session.remote != null && session.remote.editingComment() ? "Save" : "Post", session::postComment).width(38));
            if (session.remote != null && session.remote.editingComment()) commentRow.add(button("Cancel", () -> { session.remote.cancelCommentEdit(); session.comment(""); }).width(42));
            reading.add(commentRow);
        }
        for (int i = issue.comments.size() - 1; i >= 0; i--) {
            reading.add(IssueWidgets.paragraph(font, issue.comments.get(i), textWidth).width(textWidth));
            if (session.remote != null) {
                mira.core.jira.Model.Entity source = issue.commentSources.get(i);
                if (session.writable() && (session.remote.owner() || source.get("author").equals(session.remote.backend.player())))
                    reading.add(row(4).height(20).add(button("Edit comment", () -> session.remote.editComment(source)), button("Archive", () -> session.remote.archive(source, true))));
            }
        }
        if (!wide) {
            reading.add(button(moreDetails ? "Fewer details" : "More details", () -> moreDetails = !moreDetails).width(110));
            if (moreDetails) metadata(reading, issue);
        }
        reading.add(button((activity ? "Hide activity" : "Activity") + " (" + issue.activity.size() + ")", () -> activity = !activity).width(130));
        if (activity) for (int i = issue.activity.size() - 1; i >= 0; i--)
            reading.add(IssueWidgets.paragraph(font, issue.activity.get(i), textWidth).width(textWidth));
        Node readingColumn = column(4);
        if (session.documentDraft() != null) readingColumn.add(documentTools.width(textWidth));
        readingColumn.add(reading);
        Node body = row(8).add(readingColumn);
        if (wide) {
            Node properties = column(7).pad(2).width(196).scroll(propertiesScroll);
            identity(properties, issue);
            metadata(properties, issue);
            body.add(properties);
        }
        Node result = column(6).add(header, body);
        if (session.documentDraft() != null) result.add(row(4).height(20).add(
            text(width >= 300 ? "Ctrl+Enter saves / Esc cancels" : "Editing description", MUTED),
            button("Save", () -> { session.saveDocument(); ui.blur(); }).width(50),
            button("Cancel", () -> { session.cancelDocument(); ui.blur(); }).width(50)));
        return result;
    }

    private void identity(Node content, Issue issue) {
        if (!session.writable()) { content.add(text("Status: " + issue.status.label), text("Priority: " + issue.priority), text("Assignee: " + issue.assignee)); return; }
        content.add(row(6).height(33).add(
            IssueWidgets.property("Status", select("Status", issue.status.label,
                session.remote == null ? Arrays.stream(session.statuses()).map(status -> status.label).toArray(String[]::new) : session.remote.transitionLabels(issue), session::changeStatus)),
            IssueWidgets.property("Priority", select("Priority", issue.priority, WorkspaceData.PRIORITIES, session::changePriority))),
            IssueWidgets.property("Assignee", WorkspacePlayers.assignee(issue.assignee, session.self(), session::changeAssignee)));
    }

    private void metadata(Node content, Issue issue) {
        if (!session.writable()) { content.add(rule(), text("Type: " + issue.type), text("Sprint: " + issue.sprint), text("Epic: " + issue.epic), text(issue.links), text(issue.location)); return; }
        content.add(rule(),
            IssueWidgets.property("Type", select("Type", issue.type, session.types(), session::changeType)),
            IssueWidgets.property("Sprint", select("Sprint", issue.sprint, session.planningSprints(), session::changeSprint)),
            session.remote == null ? text("Epic: " + issue.epic) : IssueWidgets.property("Epic / parent", select("Parent issue", issue.epic, session.remote.epics(), session.remote::epic)), rule(), heading("Links and location"), text(issue.links).hint(issue.links),
            row(4).height(20).add(item(new ItemStack(Items.compass)), text(issue.location).hint(issue.location)));
    }

    boolean submitComment() {
        if (!comment.focused()) return false;
        session.postComment();
        return true;
    }
    private void beginTitle(Ui ui) {
        if (!session.writable()) return;
        if (session.remote != null) { session.editIssue(); return; }
        session.cancelDocument();
        textTitle = session.selected().title;
        textError = ""; editingText = true; scroll.reset();
        ui.focus(inlineTitle);
    }
    private void saveText() {
        if (session.updateText(textTitle, session.selected().description)) { editingText = false; inlineTitle.focus(false); }
        else textError = "Please enter a title.";
    }
    boolean submitInline() { if (!editingText) return false; saveText(); return true; }
    void insertBlock(Ui ui) { document.chooseInsert(ui); }
    void paragraph(Ui ui) { document.paragraph(ui); }
}
