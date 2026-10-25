package mira.platform.forge1710.client.workspace;

import java.util.ArrayList;
import java.util.List;
import mira.core.taskbody.ChecklistWidget;
import mira.core.taskbody.NoteWidget;
import mira.core.taskbody.TaskBody;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Issue;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Status;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskBodySessionTest {
    private final DemoWorkspace data = new DemoWorkspace();
    private final WorkspaceSession session = new WorkspaceSession(data);

    @Test public void cancelAndSaveKeepWidgetEditsDetached() {
        Issue issue = data.issues.get(0);
        ChecklistWidget checklist = new ChecklistWidget(); checklist.steps.add(new ChecklistWidget.Step("Build"));
        issue.body.widgets.add(checklist); session.select(issue); session.editIssue();
        ((ChecklistWidget) session.draft().body.widgets.get(0)).steps.get(0).checked = true;
        session.cancelEditor(); assertFalse(checklist.steps.get(0).checked);
        session.editIssue();
        ((ChecklistWidget) session.draft().body.widgets.get(0)).steps.get(0).checked = true;
        assertTrue(session.saveDraft());
        assertTrue(((ChecklistWidget) issue.body.widgets.get(0)).steps.get(0).checked);
        session.undo();
        assertFalse(((ChecklistWidget) issue.body.widgets.get(0)).steps.get(0).checked);
    }

    @Test public void undoRestoresBodyOrderStatusAndActivity() {
        Issue issue = data.issues.get(0); session.select(issue);
        List<Issue> order = new ArrayList<>(data.issues);
        List<String> activity = new ArrayList<>(issue.activity);
        Status status = issue.status;
        session.moveIssue(issue, Status.DONE);
        session.mutateBody(() -> issue.body.widgets.add(new NoteWidget()));
        assertEquals(1, issue.body.widgets.size());
        session.undo(); assertTrue(issue.body.widgets.isEmpty()); assertEquals(Status.DONE, issue.status);
        session.undo(); assertEquals(status, issue.status); assertEquals(order, data.issues); assertEquals(activity, issue.activity);
    }

    @Test public void undoCreateDoesNotReuseIssueKeys() {
        session.createIssue(); session.draft().title = "First"; assertTrue(session.saveDraft());
        assertEquals("BASE-9", session.selected().key);
        session.undo(); assertNull(session.selected()); assertEquals(10, data.issues.size());
        session.createIssue(); session.draft().title = "Next"; assertTrue(session.saveDraft());
        assertEquals("BASE-10", session.selected().key);
    }

    @Test public void templatesAppendAndKeepExistingContent() {
        session.createIssue(); IssueDraft draft = session.draft();
        draft.body.widgets.add(new NoteWidget());
        TaskTemplates.apply(draft, "Build"); assertEquals(3, draft.body.widgets.size());
        TaskTemplates.apply(draft, "Build"); assertEquals(3, draft.body.widgets.size());
        TaskTemplates.apply(draft, "Repair"); assertEquals(5, draft.body.widgets.size());
        assertEquals("Bug", draft.type); assertEquals("High", draft.priority);
        TaskTemplates.apply(draft, "Blank"); assertEquals(5, draft.body.widgets.size());
    }

    @Test public void inlineTextAndCrossProjectLinksRespectDraftAndUndo() {
        Issue issue = data.issues.get(0); String title = issue.title; session.select(issue);
        assertFalse(session.updateText("  ", "description")); assertFalse(session.canUndo());
        assertTrue(session.updateText("  New title  ", "First line\nSecond line"));
        assertEquals("New title", issue.title); assertTrue(issue.description.contains("\n"));
        session.undo(); assertEquals(title, issue.title);
        assertNull(session.issueDone("MISSING-1"));
        session.openIssue("orbit-1"); assertEquals("ORBIT-1", session.selected().key);
        session.createIssue(); session.openIssue("BASE-1"); assertEquals("ORBIT-1", session.selected().key);
    }

    @Test public void documentCancelKeepsTextAndNestedChangesDetached() {
        Issue issue = data.issues.get(0);
        ChecklistWidget checklist = new ChecklistWidget(); checklist.steps.add(new ChecklistWidget.Step("Build"));
        issue.body.widgets.add(checklist); session.select(issue);
        String description = issue.description;
        session.beginDocument(checklist.id);
        ChecklistWidget edit = (ChecklistWidget) session.documentDraft().selected();
        edit.title = "Edited"; edit.steps.get(0).text = "Changed"; edit.steps.get(0).checked = true;
        ((NoteWidget) session.documentDraft().body.widgets.get(0)).text = "Changed introduction";
        session.documentDraft().paragraph(); session.cancelDocument();
        assertEquals("Checklist", checklist.title); assertEquals("Build", checklist.steps.get(0).text);
        assertFalse(checklist.steps.get(0).checked); assertFalse(session.canUndo());
        assertEquals(description, issue.description); assertEquals(1, issue.body.widgets.size());
    }

    @Test public void documentSaveCommitsAllBlocksInOneUndoAndKeepsUnrelatedProperties() {
        Issue issue = data.issues.get(0);
        String description = issue.description;
        ChecklistWidget checklist = new ChecklistWidget(); checklist.steps.add(new ChecklistWidget.Step("Build"));
        issue.body.widgets.add(checklist); session.select(issue); session.beginDocument(checklist.id);
        DocumentDraft document = session.documentDraft();
        ((ChecklistWidget) document.selected()).steps.get(0).checked = true;
        NoteWidget edit = (NoteWidget) document.body.widgets.get(0); edit.text = "**After**\n\nNew paragraph";
        session.changePriority("High");
        assertTrue(session.saveDocument()); assertNull(session.documentDraft());
        assertEquals("", issue.description); assertEquals(edit.text, ((NoteWidget) issue.body.widgets.get(0)).text);
        assertTrue(((ChecklistWidget) issue.body.widgets.get(1)).steps.get(0).checked);
        edit.text = "Changed after save";
        assertFalse(((NoteWidget) issue.body.widgets.get(0)).text.equals(edit.text));
        session.undo();
        assertEquals(description, issue.description); assertEquals(1, issue.body.widgets.size());
        assertFalse(((ChecklistWidget) issue.body.widgets.get(0)).steps.get(0).checked);
        assertEquals("High", issue.priority);
    }

    @Test public void originalDescriptionCanMoveBetweenWidgetsAndNewTextCanBeInsertedThere() {
        Issue issue = data.issues.get(0);
        String description = issue.description;
        ChecklistWidget first = new ChecklistWidget(), last = new ChecklistWidget();
        issue.body.widgets.add(first); issue.body.widgets.add(last); session.select(issue);
        String introductionId = session.documentBody().widgets.get(0).id;
        session.beginDocument(introductionId);
        DocumentDraft document = session.documentDraft(); document.moveSelected(1);
        NoteWidget text = new NoteWidget(); text.text = "Between"; document.insert(text, 2);
        assertSame(first, issue.body.widgets.get(0)); assertEquals(description, issue.description);
        assertTrue(session.saveDocument());
        assertEquals(first.id, issue.body.widgets.get(0).id);
        assertEquals(introductionId, issue.body.widgets.get(1).id);
        assertEquals("Between", ((NoteWidget) issue.body.widgets.get(2)).text);
        assertEquals(last.id, issue.body.widgets.get(3).id);
        session.undo(); assertEquals(description, issue.description); assertEquals(first.id, issue.body.widgets.get(0).id);
    }

    @Test public void cancellingNewDocumentDoesNotLeaveAnEmptyBlock() {
        Issue issue = data.issues.get(0); session.select(issue);
        session.beginDocument(null); session.documentDraft().paragraph();
        ((NoteWidget) session.documentDraft().selected()).text = "Discarded";
        session.cancelDocument(); assertTrue(issue.body.widgets.isEmpty()); assertFalse(session.canUndo());
    }

    @Test public void staleDocumentsCannotOverwriteReplacedBodiesOrAnotherIssue() {
        Issue issue = data.issues.get(0); NoteWidget note = new NoteWidget(); issue.body.widgets.add(note); session.select(issue);
        session.beginDocument(note.id); issue.body = new TaskBody();
        assertFalse(session.saveDocument()); assertTrue(issue.body.widgets.isEmpty()); assertFalse(session.canUndo());
        issue.body.widgets.add(note); session.beginDocument(note.id); session.select(data.issues.get(1));
        assertNull(session.documentDraft()); assertFalse(session.saveDocument());
    }

    @Test public void escapeCancelsTheDocumentAndRemovalIsSavedAndUndoable() {
        Issue issue = data.issues.get(0); NoteWidget note = new NoteWidget(); note.text = "Keep";
        issue.body.widgets.add(note); session.select(issue); session.beginDocument(note.id);
        assertTrue(session.back()); assertSame(issue, session.selected()); assertNull(session.documentDraft());
        session.beginDocument(note.id); session.documentDraft().removeSelected(); assertEquals(1, issue.body.widgets.size());
        assertTrue(session.saveDocument()); assertEquals(1, issue.body.widgets.size());
        assertFalse(issue.body.widgets.get(0).id.equals(note.id));
        session.undo(); assertEquals("Keep", ((NoteWidget) issue.body.widgets.get(0)).text);
        session.editIssue(); session.beginDocument(note.id); assertNull(session.documentDraft());
    }

    @Test public void switchingBlocksKeepsPreviousEditsUntilDocumentSave() {
        Issue issue = data.issues.get(0); ChecklistWidget checklist = new ChecklistWidget();
        issue.body.widgets.add(checklist); session.select(issue); session.beginDocument(checklist.id);
        DocumentDraft document = session.documentDraft(); document.selected().title = "Renamed";
        session.beginDocument(document.body.widgets.get(0).id);
        assertSame(document, session.documentDraft());
        ((NoteWidget) document.selected()).text = "Text edited too";
        assertTrue(session.saveDocument());
        assertEquals("Text edited too", ((NoteWidget) issue.body.widgets.get(0)).text);
        assertEquals("Renamed", issue.body.widgets.get(1).title);
    }

    @Test public void fullFormUsesTheSameDocumentAndTemplatesAppendToIt() {
        Issue issue = data.issues.get(0); String original = issue.description; session.select(issue); session.editIssue();
        DocumentDraft document = session.draft().document();
        ((NoteWidget) document.body.widgets.get(0)).text = "    code\n\nHard break  \nnext\n";
        TaskTemplates.apply(session.draft(), "Build");
        assertSame(document.body, session.draft().body); assertEquals(3, document.body.widgets.size());
        session.cancelEditor(); assertEquals(original, issue.description); assertTrue(issue.body.widgets.isEmpty());
        session.editIssue(); document = session.draft().document();
        String markdown = "    code\n\nHard break  \nnext\n";
        ((NoteWidget) document.body.widgets.get(0)).text = markdown;
        assertTrue(session.saveDraft()); assertEquals("", issue.description);
        assertEquals(markdown, ((NoteWidget) issue.body.widgets.get(0)).text);
        session.undo(); assertEquals(original, issue.description); assertTrue(issue.body.widgets.isEmpty());
    }

    @Test public void markdownWhitespaceSurvivesCreationInlineSaveAndFullEdit() {
        String markdown = "    indented code\n\nParagraph with hard break  \nnext line\n";
        session.createIssue(); session.draft().title = "Markdown"; session.draft().description = markdown;
        assertTrue(session.saveDraft()); assertEquals(markdown, session.selected().description);
        assertTrue(session.updateText("Markdown", markdown + "\n"));
        assertEquals(markdown + "\n", session.selected().description);
        session.editIssue(); assertTrue(session.saveDraft()); assertEquals(markdown + "\n", session.selected().description);
    }

    @Test public void descriptionSearchIncludesInterleavedText() {
        Issue issue = data.issues.get(0); NoteWidget note = new NoteWidget();
        note.text = "**Interleaved instructions**"; issue.body.widgets.add(note);
        session.query("interleaved instructions");
        assertEquals(1, session.issues().size()); assertSame(issue, session.issues().get(0));
    }
}
