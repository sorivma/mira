package mira.platform.forge1710.client.workspace;

import mira.core.taskbody.ChecklistWidget;
import mira.core.taskbody.LocationWidget;
import mira.core.taskbody.NoteWidget;
import mira.core.taskbody.TaskBody;
import org.junit.Test;
import static org.junit.Assert.*;

public class DocumentDraftTest {
    @Test public void dragPositionsAreGapsAndPreserveBlockIdentity() {
        DocumentDraft document = new DocumentDraft(new TaskBody());
        NoteWidget first = new NoteWidget(), middle = new NoteWidget(); ChecklistWidget last = new ChecklistWidget();
        document.insert(first, 0); document.insert(middle, 1); document.insert(last, 2);
        document.moveTo(first.id, 3);
        assertSame(middle, document.body.widgets.get(0)); assertSame(last, document.body.widgets.get(1));
        assertSame(first, document.body.widgets.get(2)); assertSame(first, document.selected());
        document.moveTo(first.id, 2); assertSame(first, document.body.widgets.get(2));
        document.moveTo(first.id, 0); assertSame(first, document.body.widgets.get(0));
        document.moveTo("missing", 1); assertEquals(3, document.body.widgets.size());
    }
    @Test public void splittingParagraphsKeepsWhitespaceAndSelectsTheSecondHalf() {
        DocumentDraft document = new DocumentDraft(new TaskBody()); NoteWidget text = new NoteWidget();
        document.insert(text, 0); document.splitText("    first  ", "\nsecond\n");
        assertEquals("    first  ", text.text);
        assertEquals("\nsecond\n", ((NoteWidget) document.selected()).text);
        assertEquals(1, document.selectedIndex()); assertEquals(2, document.body.widgets.size());
        LocationWidget location = new LocationWidget(); document.insert(location, 2);
        document.paragraph(); assertTrue(document.selected() instanceof NoteWidget); assertEquals(3, document.selectedIndex());
    }
    @Test public void slashReplacesAnEmptyParagraphAndKeepsNonemptyText() {
        DocumentDraft document = new DocumentDraft(new TaskBody()); document.paragraph();
        ChecklistWidget checklist = new ChecklistWidget(); document.insertFromSlash(checklist);
        assertEquals(1, document.body.widgets.size()); assertSame(checklist, document.selected());
        NoteWidget text = new NoteWidget(); text.text = "Keep this"; document.insert(text, 1);
        LocationWidget location = new LocationWidget(); document.insertFromSlash(location);
        assertEquals(3, document.body.widgets.size()); assertSame(text, document.body.widgets.get(1));
        assertSame(location, document.body.widgets.get(2));
    }
    @Test public void removalSelectsAnAdjacentBlockAndAllowsAnEmptyDocument() {
        DocumentDraft document = new DocumentDraft(new TaskBody()); NoteWidget first = new NoteWidget(), last = new NoteWidget();
        document.insert(first, 0); document.insert(last, 1); document.removeSelected();
        assertSame(first, document.selected()); document.removeSelected();
        assertNull(document.selected()); assertTrue(document.body.widgets.isEmpty());
        document.removeSelected(); document.paragraph(); assertEquals(1, document.body.widgets.size());
    }
}
