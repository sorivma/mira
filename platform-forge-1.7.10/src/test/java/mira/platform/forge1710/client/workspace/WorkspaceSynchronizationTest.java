package mira.platform.forge1710.client.workspace;

import java.util.*;
import java.util.function.Consumer;
import mira.core.jira.BodyValues;
import mira.core.jira.JiraDatabase;
import mira.core.jira.Model;
import mira.core.jira.Model.*;
import mira.core.taskbody.NoteWidget;
import mira.platform.forge1710.jira.DatabaseCodec.Reply;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Headless integration of real domain transactions with the existing editor/session. */
public class WorkspaceSynchronizationTest {
    private final String owner = Model.id(), editor = Model.id(), workspace = Model.id();
    private JiraDatabase db;
    private FakeBackend backend;
    private WorkspaceData data;
    private WorkspaceSession session;
    private static final class FakeBackend implements WorkspaceBackend {
        final JiraDatabase db; final String player; final List<Consumer<State>> listeners = new ArrayList<>(); State current;
        FakeBackend(JiraDatabase db, String player) { this.db = db; this.player = player; current = db.view(player); }
        public State state() { return current; } public String player() { return player; } public boolean ready() { return true; } public String status() { return "Saved"; }
        public void listen(Consumer<State> listener) { listeners.add(listener); listener.accept(current); }
        void push() { current = db.view(player); for (Consumer<State> listener : listeners) listener.accept(current); }
        public void submit(Command c, Consumer<Reply> complete) {
            Reply reply = new Reply(); reply.request = c.id;
            try { db.execute(player, "Builder", false, c); }
            catch (Conflict e) { reply.result = "CONFLICT"; reply.message = e.getMessage(); }
            catch (Rejected e) { reply.result = "ERROR"; reply.message = e.getMessage(); }
            push(); reply.state = current; complete.accept(reply);
        }
    }
    @Before public void setup() {
        db = new JiraDatabase(new State(), state -> {}); db.rememberPlayer(owner, "Owner"); db.rememberPlayer(editor, "Builder");
        Command create = new Command(); create.operation = Operation.CREATE_WORKSPACE; create.workspace = workspace; create.arguments.put("name", "Base"); db.execute(owner, "Owner", false, create);
        Command grant = command(); grant.patches.add(new Patch(Model.id(), Kind.MEMBER, workspace).set("player", editor, 0).set("role", "EDITOR", 0)); db.execute(owner, "Owner", false, grant);
        backend = new FakeBackend(db, editor); data = new WorkspaceData(); session = new WorkspaceSession(data, "Builder", backend);
        session.createIssue(); session.draft().title = "Build"; session.saveDraft(); assertNotNull(session.selected());
    }
    private Command command() { Command c = new Command(); c.workspace = workspace; return c; }
    private void ownerEdit(Entity source, String field, String value) {
        Command c = command(); c.patches.add(Patch.edit(source).set(source, field, value)); db.execute(owner, "Owner", false, c); backend.push();
    }
    @Test public void savedChangesRefreshStableIssueObjectsWithoutChangingAnOpenDraft() {
        WorkspaceData.Issue issue = session.selected(); session.editIssue(); session.draft().title = "My draft";
        ownerEdit(issue.source, "priority", "Critical");
        assertSame(issue, session.selected()); assertEquals("My draft", session.draft().title); assertEquals("Critical", issue.priority);
        session.saveDraft(); assertNull(session.draft()); assertEquals("My draft", issue.title); assertEquals("Critical", issue.priority);
    }
    @Test public void conflictingTitlesKeepTheDraftAndCanBeExplicitlyRebased() {
        WorkspaceData.Issue issue = session.selected(); session.editIssue(); session.draft().title = "My draft";
        ownerEdit(issue.source, "title", "First accepted"); session.saveDraft();
        assertTrue(session.remote.conflict); assertEquals("My draft", session.draft().title); assertEquals("First accepted", issue.title);
        session.remote.rebaseConflict(); session.saveDraft(); assertNull(session.draft()); assertEquals("My draft", issue.title);
    }
    @Test public void descriptionDraftMergesWithAnotherPlayersDifferentBlock() {
        session.editIssue(); NoteWidget first = new NoteWidget(), second = new NoteWidget(); first.text = "A"; second.text = "B";
        session.draft().body.widgets.add(first); session.draft().body.widgets.add(second); session.saveDraft();
        WorkspaceData.Issue issue = session.selected(); session.beginDocument(first.id);
        ((NoteWidget) session.documentDraft().body.widgets.get(0)).text = "My A";
        Entity b = issue.blocks.get(1); NoteWidget other = (NoteWidget) BodyValues.decode(b.get("content")); other.text = "Their B"; ownerEdit(b, "content", BodyValues.encode(other));
        session.saveDocument(); assertNull(session.documentDraft());
        assertEquals("My A", ((NoteWidget) issue.body.widgets.get(0)).text); assertEquals("Their B", ((NoteWidget) issue.body.widgets.get(1)).text);
    }
    @Test public void losingWriteAccessDropsEditingStateAndBlocksFurtherSaves() {
        session.editIssue(); session.draft().title = "Private draft";
        Entity membership = db.snapshot().list(Kind.MEMBER, workspace).get(0); ownerEdit(membership, "role", "VIEWER");
        assertNull(session.draft()); assertFalse(session.writable()); session.createIssue(); assertNull(session.draft());
    }
    @Test public void archivingAProjectPreservesItsOpenDraftUntilItIsRestored() {
        session.editIssue(); session.draft().title = "Retained edit";
        Entity project = session.project().source; ownerEdit(project, "archived", "true");
        assertNotNull(session.draft()); assertEquals("Retained edit", session.draft().title);
        ownerEdit(db.snapshot().entities.get(project.id), "archived", "false"); session.saveDraft();
        assertNull(session.draft()); assertEquals("Retained edit", session.selected().title);
    }
    @Test public void archivingATaskRetainsItsDocumentDraftAndRestorationReattachesIt() {
        session.beginDocument(null); session.documentDraft().body.widgets.add(new NoteWidget());
        Entity task = session.selected().source; ownerEdit(task, "archived", "true");
        assertNotNull(session.documentDraft()); assertNull(session.selected());
        ownerEdit(db.snapshot().entities.get(task.id), "archived", "false"); assertNotNull(session.selected());
        session.saveDocument(); assertNull(session.documentDraft()); assertEquals(1, session.selected().body.widgets.size());
    }
    @Test public void revokingReadAccessPurgesTheProjection() {
        Entity membership = db.snapshot().list(Kind.MEMBER, workspace).get(0); ownerEdit(membership, "archived", "true");
        assertTrue(data.issues.isEmpty()); assertTrue(data.projects.isEmpty()); assertNull(session.selected()); assertNull(session.project());
    }
    @Test public void aConflictingCommentEditRetainsTextUntilAnExplicitRebase() {
        session.comment("Before"); session.postComment(); Entity comment = session.selected().commentSources.get(0);
        session.remote.editComment(comment); session.comment("My edit"); ownerEdit(comment, "text", "First accepted"); session.postComment();
        assertTrue(session.remote.conflict); assertEquals("My edit", session.comment());
        session.remote.rebaseConflict(); session.postComment();
        assertEquals("My edit", db.snapshot().entities.get(comment.id).get("text")); assertEquals("", session.comment());
    }

    @Test public void cancellingSettingsCreationReturnsToTheSavedProject() {
        WorkspaceSettings settings = new WorkspaceSettings(session); settings.tab("Projects");
        String original = settings.selected.id, originalName = settings.name;
        settings.beginCreate(false); settings.changeName("Unfinished project"); settings.changeKey("TEMP");
        settings.cancelCreate();
        assertFalse(settings.creating); assertEquals(original, settings.selected.id); assertEquals(originalName, settings.name);
        assertEquals(1, db.snapshot().list(Kind.PROJECT, workspace).size());
    }

    @Test public void settingsCreationValidatesTheKeyAndNeverRenamesTheSelectedProject() {
        WorkspaceSettings settings = new WorkspaceSettings(session); settings.tab("Projects");
        String original = settings.selected.id, originalName = settings.name;
        settings.beginCreate(false); settings.changeName("Power plant"); settings.changeKey("1"); settings.saveForm();
        assertTrue(settings.creating); assertFalse(settings.error.isEmpty());
        assertEquals(1, db.snapshot().list(Kind.PROJECT, workspace).size());
        settings.changeKey("power"); settings.saveForm();
        assertFalse(settings.creating); assertEquals("POWER", settings.selected.get("key"));
        assertEquals("Power plant", settings.selected.get("name"));
        assertEquals(originalName, db.snapshot().entities.get(original).get("name"));
    }

    @Test public void remoteRenameDoesNotReplaceAnUnsubmittedSettingsEdit() {
        WorkspaceSettings settings = new WorkspaceSettings(session); settings.tab("Projects");
        Entity original = settings.selected.copy(); settings.changeName("My project name");
        ownerEdit(original, "name", "Their project name");
        assertEquals("My project name", settings.name); settings.saveForm();
        assertTrue(session.remote.conflict); assertEquals("My project name", settings.name);
        assertEquals("Their project name", db.snapshot().entities.get(original.id).get("name"));
        session.remote.discardConflict(); settings.reload();
        assertEquals("Their project name", settings.name); assertFalse(settings.dirty());
    }

    @Test public void changingTheSettingsProjectClearsThePreviousColumnForm() {
        Command create = command(); create.operation = Operation.CREATE_PROJECT; create.target = Model.id();
        create.arguments.put("name", "Power plant"); create.arguments.put("key", "POWER");
        db.execute(owner, "Owner", false, create); backend.push();
        WorkspaceSettings settings = new WorkspaceSettings(session); settings.tab("Board");
        settings.changeName("Unsaved column name"); settings.chooseProject("Power plant");
        assertEquals("Board", settings.tab); assertNotNull(settings.selected);
        assertEquals(create.target, settings.selected.get("project")); assertFalse(settings.dirty());
        for (Entity column : settings.candidates(Kind.COLUMN)) assertEquals(create.target, column.get("project"));
    }

    @Test public void repeatedWorkspaceNamesRemainIndividuallySelectable() {
        Entity first = new Entity(Model.id(), Kind.WORKSPACE, workspace);
        Entity second = new Entity(Model.id(), Kind.WORKSPACE, workspace);
        first.fields.put("name", "Base"); second.fields.put("name", "Base");
        Map<String, Entity> choices = WorkspaceSettings.choices(Arrays.asList(first, second));
        assertEquals(2, choices.size());
        assertSame(first, choices.get(WorkspaceSettings.choiceLabel(choices, first.id, "")));
        assertSame(second, choices.get(WorkspaceSettings.choiceLabel(choices, second.id, "")));
        assertEquals("Base", WorkspaceSettings.choices(Collections.singletonList(first)).keySet().iterator().next());
    }
}
