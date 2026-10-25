package mira.core.jira;

import java.io.IOException;
import java.util.*;
import mira.core.jira.Model.*;
import mira.core.taskbody.NoteWidget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Transaction, authorization and concurrency contracts; no game classes or runtime required. */
public class JiraDatabaseTest {
    private final String owner = Model.id(), editor = Model.id(), viewer = Model.id();
    private final String workspace = Model.id();
    private JiraDatabase db;
    private MemoryStore store;
    private String project, type;
    private static final class MemoryStore implements JiraDatabase.Store {
        State saved; boolean fail;
        public void write(State state) throws IOException { if (fail) throw new IOException("disk full"); saved = state.copy(); }
    }
    @Before public void prepare() {
        store = new MemoryStore(); db = new JiraDatabase(new State(), store);
        db.rememberPlayer(owner, "Owner"); db.rememberPlayer(editor, "Builder"); db.rememberPlayer(viewer, "Reader");
        Command c = command(Operation.CREATE_WORKSPACE); c.arguments.put("name", "Base"); execute(owner, c);
        project = first(Kind.PROJECT).id; type = first(Kind.ISSUE_TYPE).id;
        grant(editor, "EDITOR"); grant(viewer, "VIEWER");
    }
    private Command command(Operation operation) { Command c = new Command(); c.workspace = workspace; c.operation = operation; return c; }
    private void execute(String actor, Command c) { db.execute(actor, db.snapshot().players.get(actor), false, c); }
    private Entity first(Kind kind) { return db.snapshot().list(kind, workspace).get(0); }
    private Entity entity(String id) { return db.snapshot().entities.get(id); }
    private void grant(String player, String role) {
        Command c = command(Operation.EDIT); c.patches.add(new Patch(Model.id(), Kind.MEMBER, workspace).set("player", player, 0).set("role", role, 0).set("archived", "false", 0)); execute(owner, c);
    }
    private Entity task(String title) {
        Command c = command(Operation.EDIT); String id = Model.id();
        c.patches.add(new Patch(id, Kind.TASK, workspace).set("project", project, 0).set("title", title, 0).set("description", "", 0)
            .set("type", type, 0).set("priority", "Medium", 0).set("rank", "0", 0).set("archived", "false", 0));
        execute(editor, c); return entity(id);
    }
    private Command edit(Entity source, String key, String value) {
        Command c = command(Operation.EDIT); c.patches.add(Patch.edit(source).set(source, key, value)); return c;
    }
    private static void rejected(Runnable action) { try { action.run(); fail("Expected a rejected transaction"); } catch (Rejected expected) { assertNotNull(expected.getMessage()); } }
    private static void conflict(Runnable action) { try { action.run(); fail("Expected a field conflict"); } catch (Conflict expected) { assertNotNull(expected.getMessage()); } }

    @Test public void workspacesArePrivateAndOperatorStatusDoesNotGrantReadAccess() {
        String outsider = Model.id(); db.rememberPlayer(outsider, "Visitor");
        assertTrue(db.view(outsider).entities.isEmpty());
        rejected(() -> db.execute(outsider, "Visitor", true, edit(first(Kind.WORKSPACE), "name", "Stolen")));
        assertEquals("Base", first(Kind.WORKSPACE).get("name"));
    }
    @Test public void viewersCannotWriteAndEditorsCannotManagePermissions() {
        Entity task = task("Build"); rejected(() -> execute(viewer, edit(task, "title", "Changed")));
        rejected(() -> execute(editor, edit(first(Kind.MEMBER), "role", "VIEWER")));
    }
    @Test public void disjointFieldsMergeFromTheSameSnapshot() {
        Entity task = task("Before"); execute(editor, edit(task, "title", "After")); execute(owner, edit(task, "priority", "Critical"));
        assertEquals("After", entity(task.id).get("title")); assertEquals("Critical", entity(task.id).get("priority"));
    }
    @Test public void sameFieldConflictRejectsTheEntireTransaction() {
        Entity task = task("Before"); execute(editor, edit(task, "title", "First"));
        Command stale = edit(task, "title", "Second"); stale.patches.get(0).set(task, "priority", "Critical");
        long revision = db.revision(); conflict(() -> execute(owner, stale));
        assertEquals(revision, db.revision()); assertEquals("First", entity(task.id).get("title")); assertEquals("Medium", entity(task.id).get("priority"));
    }
    private Entity block(Entity task, String text) {
        NoteWidget note = new NoteWidget(); note.text = text; Command c = command(Operation.EDIT);
        c.patches.add(new Patch(note.id, Kind.BLOCK, workspace).set("task", task.id, 0).set("content", BodyValues.encode(note), 0).set("rank", "0", 0).set("archived", "false", 0));
        execute(editor, c); return entity(note.id);
    }
    @Test public void separateDescriptionBlocksMergeButTheSameBlockConflicts() {
        Entity task = task("Build"), first = block(task, "A"), second = block(task, "B");
        NoteWidget a = (NoteWidget) BodyValues.decode(first.get("content")); a.text = "A edited";
        NoteWidget b = (NoteWidget) BodyValues.decode(second.get("content")); b.text = "B edited";
        execute(owner, edit(first, "content", BodyValues.encode(a))); execute(editor, edit(second, "content", BodyValues.encode(b)));
        conflict(() -> execute(editor, edit(first, "content", BodyValues.encode(b))));
        assertEquals("B edited", ((NoteWidget) BodyValues.decode(entity(second.id).get("content"))).text);
    }
    @Test public void archiveReadGuardsProtectAgainstConcurrentBlockEdits() {
        Entity task = task("Build"), block = block(task, "Before"); Command archive = edit(block, "archived", "true"); archive.patches.get(0).expected.putAll(block.versions);
        NoteWidget note = (NoteWidget) BodyValues.decode(block.get("content")); note.text = "After";
        execute(owner, edit(block, "content", BodyValues.encode(note))); conflict(() -> execute(editor, archive));
        assertFalse(entity(block.id).archived());
    }
    @Test public void commentsAppendIndependentlyAndCannotSpoofTheirAuthor() {
        Entity task = task("Build");
        for (String actor : Arrays.asList(owner, editor)) {
            Command c = command(Operation.EDIT); c.patches.add(new Patch(Model.id(), Kind.COMMENT, workspace).set("task", task.id, 0).set("text", "Hello", 0)); execute(actor, c);
        }
        assertEquals(2, db.snapshot().list(Kind.COMMENT, workspace).size());
        Entity comment = first(Kind.COMMENT); rejected(() -> execute(editor, edit(comment, "text", "Forged")));
        execute(owner, edit(comment, "archived", "true")); assertTrue(entity(comment.id).archived());
    }
    @Test public void permissionsSurviveNicknameChangesAndRevokeImmediately() {
        db.rememberPlayer(editor, "Renamed"); assertEquals(Role.EDITOR, db.snapshot().role(workspace, editor));
        Entity membership = db.snapshot().list(Kind.MEMBER, workspace).stream().filter(e -> e.get("player").equals(editor)).findFirst().get();
        execute(owner, edit(membership, "archived", "true")); assertTrue(db.view(editor).entities.isEmpty()); rejected(() -> task("Denied"));
    }
    @Test public void ownershipTransfersAtomicallyAndThePreviousOwnerBecomesEditor() {
        Command c = command(Operation.TRANSFER); c.arguments.put("player", editor); execute(owner, c);
        assertEquals(Role.OWNER, db.snapshot().role(workspace, editor)); assertEquals(Role.EDITOR, db.snapshot().role(workspace, owner));
        rejected(() -> execute(owner, edit(first(Kind.WORKSPACE), "owner", owner)));
    }
    @Test public void operatorRecoveryIsExplicitAndAudited() {
        Command c = command(Operation.ADMIN_ROLE); c.arguments.put("player", viewer); c.arguments.put("role", "OWNER");
        rejected(() -> db.execute(editor, "Builder", false, c)); db.execute(editor, "Builder", true, c);
        assertEquals(Role.OWNER, db.snapshot().role(workspace, viewer));
        assertEquals("ADMIN_ROLE", db.snapshot().activity.get(db.snapshot().activity.size() - 1).summary);
    }
    @Test public void acceptedRequestsAreIdempotentAcrossRestarts() {
        Entity task = task("Before"); Command c = edit(task, "title", "After"); execute(editor, c); long revision = db.revision();
        db = new JiraDatabase(store.saved, store); execute(editor, c); assertEquals(revision, db.revision());
    }
    @Test public void failedPersistenceDoesNotExposeAnyPartOfTheChange() {
        Entity task = task("Before"); long revision = db.revision(); State saved = store.saved;
        store.fail = true; rejected(() -> execute(editor, edit(task, "title", "Lost")));
        assertEquals(revision, db.revision()); assertEquals("Before", entity(task.id).get("title")); assertSame(saved, store.saved);
    }
    @Test public void snapshotsCannotMutateTheDatabase() {
        Entity task = task("Before"); State snapshot = db.snapshot(); snapshot.entities.get(task.id).fields.put("title", "Local");
        assertEquals("Before", entity(task.id).get("title"));
    }
    @Test public void issueNumbersAreNotReusedAfterArchiveOrRestart() {
        Entity first = task("First"); execute(editor, edit(first, "archived", "true")); db = new JiraDatabase(store.saved, store);
        Entity second = task("Second"); assertEquals("MAIN-1", first.get("key")); assertEquals("MAIN-2", second.get("key"));
    }
    @Test public void undoPreservesOtherPeoplesChangesToOtherFieldsAndKeepsHistory() {
        Entity task = task("Before"); Command edit = edit(task, "title", "After"); execute(editor, edit); execute(owner, edit(task, "priority", "High"));
        Command undo = command(Operation.UNDO); undo.target = edit.id; execute(editor, undo);
        assertEquals("Before", entity(task.id).get("title")); assertEquals("High", entity(task.id).get("priority"));
        assertEquals(undo.id, db.snapshot().activity.stream().filter(a -> a.id.equals(edit.id)).findFirst().get().undoneBy);
    }
    @Test public void undoRefusesToOverwriteLaterChangesToTheSameField() {
        Entity task = task("Before"); Command edit = edit(task, "title", "After"); execute(editor, edit); execute(owner, edit(entity(task.id), "title", "Newer"));
        Command undo = command(Operation.UNDO); undo.target = edit.id; conflict(() -> execute(editor, undo)); assertEquals("Newer", entity(task.id).get("title"));
    }
    private Command publication(Entity workflow) {
        Command c = command(Operation.PUBLISH); c.target = workflow.id;
        c.arguments.put("generationVersion", Long.toString(workflow.version("generation")));
        c.arguments.put("draftVersion", Long.toString(Workflows.draftVersion(db.snapshot(), workflow))); return c;
    }
    private String restrictDoneTransition() {
        Entity flow = first(Kind.WORKFLOW); State state = db.snapshot();
        String done = Workflows.nodes(state, flow, true).stream().filter(e -> e.get("category").equals("DONE")).findFirst().get().get("logical");
        Entity edge = Workflows.edges(state, flow, true).stream().filter(e -> e.get("to").equals(done)).findFirst().get();
        Command edit = edit(edge, "role", "OWNER"); edit.patches.get(0).set(edge, "required", "assignee"); execute(owner, edit); execute(owner, publication(entity(flow.id))); return done;
    }
    @Test public void workflowRoleAndRequiredFieldsAreEnforcedForAllTaskChanges() {
        Entity task = task("Build"); String done = restrictDoneTransition();
        rejected(() -> execute(editor, edit(task, "status", done))); rejected(() -> execute(owner, edit(task, "status", done)));
        Command accepted = edit(task, "status", done); accepted.patches.get(0).set(task, "assignee", editor); execute(owner, accepted); assertTrue(Workflows.done(db.snapshot(), entity(task.id)));
    }
    @Test public void changingTypeCannotBypassAWorkflowGate() {
        Entity task = task("Build"); String done = restrictDoneTransition(); Entity bug = db.snapshot().list(Kind.ISSUE_TYPE, workspace).stream().filter(e -> e.get("name").equals("Bug")).findFirst().get();
        Command bypass = edit(task, "status", done); bypass.patches.get(0).set(task, "type", bug.id).set(task, "assignee", editor); rejected(() -> execute(editor, bypass));
    }
    @Test public void sprintsHaveOneActiveSprintAndCompletionMovesOnlyUnfinishedWork() {
        List<Entity> sprints = new ArrayList<>();
        for (String name : Arrays.asList("First", "Next")) {
            Command c = command(Operation.EDIT); String id = Model.id();
            c.patches.add(new Patch(id, Kind.SPRINT, workspace).set("project", project, 0).set("name", name, 0).set("state", "PLANNED", 0)); execute(editor, c); sprints.add(entity(id));
        }
        execute(editor, edit(sprints.get(0), "state", "ACTIVE")); rejected(() -> execute(editor, edit(sprints.get(1), "state", "ACTIVE")));
        Entity unfinished = task("Unfinished"), finished = task("Finished");
        execute(editor, edit(unfinished, "sprint", sprints.get(0).id)); execute(editor, edit(finished, "sprint", sprints.get(0).id));
        Entity flow = first(Kind.WORKFLOW); String done = Workflows.nodes(db.snapshot(), flow, false).stream().filter(e -> e.get("category").equals("DONE")).findFirst().get().get("logical");
        execute(editor, edit(entity(finished.id), "status", done));
        Command complete = command(Operation.COMPLETE_SPRINT); complete.target = sprints.get(0).id; complete.arguments.put("destination", sprints.get(1).id); execute(editor, complete);
        assertEquals(sprints.get(1).id, entity(unfinished.id).get("sprint")); assertEquals(sprints.get(0).id, entity(finished.id).get("sprint"));
    }
    @Test public void publishingRemovalRequiresAtomicStatusMigration() {
        Entity task = task("Build"), flow = first(Kind.WORKFLOW); State state = db.snapshot();
        Entity removed = Workflows.nodes(state, flow, true).stream().filter(e -> e.get("name").equals("Review")).findFirst().get();
        execute(editor, edit(task, "status", removed.get("logical")));
        Command draft = edit(removed, "archived", "true");
        for (Entity edge : Workflows.edges(state, flow, true)) if (edge.get("to").equals(removed.get("logical")) || edge.get("from").equals(removed.get("logical"))) draft.patches.add(Patch.edit(edge).set(edge, "archived", "true"));
        execute(owner, draft); Command publish = publication(entity(flow.id)); long revision = db.revision();
        rejected(() -> execute(owner, publish)); assertEquals(revision, db.revision());
        publish.arguments.put("migrate." + task.id, flow.get("initial")); publish.arguments.put("migrateVersion." + task.id, Long.toString(entity(task.id).version("status"))); execute(owner, publish);
        assertEquals(flow.get("initial"), entity(task.id).get("status"));
    }
}
