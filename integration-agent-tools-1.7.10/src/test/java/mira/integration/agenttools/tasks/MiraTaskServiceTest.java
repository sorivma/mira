package mira.integration.agenttools.tasks;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import mira.core.jira.JiraDatabase;
import mira.core.jira.Model;
import mira.core.jira.Model.*;
import mira.integration.agenttools.contract.MiraTools;
import org.junit.Before;
import org.junit.Test;

/** Actual core transactions with a deferred transport: no game, sockets or runtime automation. */
public class MiraTaskServiceTest {
    private final String owner = Model.id(), workspace = Model.id();
    private final MemoryBackend backend = new MemoryBackend();
    private MiraTaskService service;
    private String project, type;
    @Before public void setup() {
        backend.database.rememberPlayer(owner, "Owner");
        Command create = new Command(); create.operation = Operation.CREATE_WORKSPACE; create.workspace = workspace;
        create.arguments.put("name", "Base"); backend.database.execute(owner, "Owner", false, create);
        project = backend.database.snapshot().list(Kind.PROJECT, workspace).get(0).id;
        type = backend.database.snapshot().list(Kind.ISSUE_TYPE, workspace).get(0).id;
        service = new MiraTaskService(backend);
    }
    private Map<String, Object> args(Object... values) {
        Map<String, Object> result = map(values); result.put("session", backend.session); result.put("dimension", 0); return result;
    }
    private Map<String, Object> create(String id) {
        return args("requestId", id, "projectId", project, "fields", map("title", "Build steam supply", "type", type));
    }
    private String task() {
        String id = Model.id(); service.call("mira_create_task", create(id)); backend.deliver(); return id;
    }
    private Map<String, Object> receipt(String request) { return service.call("mira_get_operation", args("requestId", request)); }

    @Test public void queuedCreationBecomesSuccessfulOnlyAfterDurableAcknowledgment() {
        String request = Model.id();
        assertEquals("PENDING", service.call("mira_create_task", create(request)).get("result"));
        assertTrue(backend.database.snapshot().list(Kind.TASK, workspace).isEmpty());
        assertEquals("PENDING", receipt(request).get("result"));
        service.call("mira_create_task", create(request)); assertEquals(1, backend.submissions);
        backend.deliver();
        assertEquals("OK", receipt(request).get("result"));
        assertEquals(1, backend.database.snapshot().list(Kind.TASK, workspace).size());
        assertFalse(backend.database.snapshot().require(request, Kind.TASK).get("key").isEmpty());
    }
    @Test public void retriesAfterReconnectionUseServerActivityWithoutCreatingAnotherTask() {
        String request = task(); backend.session = Model.id();
        assertEquals("OK", service.call("mira_create_task", create(request)).get("result"));
        assertEquals(1, backend.submissions);
        assertEquals(1, backend.database.snapshot().list(Kind.TASK, workspace).size());
    }
    @Test public void requestIdsCannotBeReusedForDifferentInputInTheSameSession() {
        String request = Model.id(); service.call("mira_create_task", create(request));
        Map<String, Object> changed = create(request); changed.put("fields", map("title", "Different", "type", type));
        assertThrows(IllegalArgumentException.class, () -> service.call("mira_create_task", changed));
        assertEquals(1, backend.submissions);
    }
    @Test public void staleFieldVersionsReachTheServerAndRemainConflicts() {
        String id = task(); Entity original = backend.database.snapshot().require(id, Kind.TASK);
        Command change = new Command(); change.workspace = workspace;
        change.patches.add(Patch.edit(original).set(original, "title", "Another player's edit"));
        backend.database.execute(owner, "Owner", false, change);
        String request = Model.id();
        service.call("mira_update_task", args("requestId", request, "taskId", id,
            "fields", map("title", "Stale edit"), "expected", map("title", original.version("title"))));
        backend.deliver();
        assertEquals("CONFLICT", receipt(request).get("result"));
        assertEquals("Another player's edit", backend.database.snapshot().require(id, Kind.TASK).get("title"));
    }
    @Test public void permissionsAreRecheckedByTheServerAfterEnqueue() {
        String id = task(), editor = Model.id(); backend.database.rememberPlayer(editor, "Editor");
        Command grant = new Command(); grant.workspace = workspace; String memberId = Model.id();
        grant.patches.add(new Patch(memberId, Kind.MEMBER, workspace).set("player", editor, 0).set("role", "EDITOR", 0).set("archived", "false", 0));
        backend.database.execute(owner, "Owner", false, grant); backend.actor = editor;
        String request = Model.id(); service.call("mira_add_comment", args("requestId", request, "taskId", id, "text", "Queued before role change"));
        Entity member = backend.database.snapshot().require(memberId, Kind.MEMBER);
        Command downgrade = new Command(); downgrade.workspace = workspace; downgrade.patches.add(Patch.edit(member).set(member, "role", "VIEWER"));
        backend.database.execute(owner, "Owner", false, downgrade); backend.deliver();
        assertEquals("ERROR", receipt(request).get("result"));
        assertTrue(backend.database.snapshot().list(Kind.COMMENT, workspace).isEmpty());
    }
    @Test public void storageFailureDoesNotBecomeSuccess() {
        String request = Model.id(); service.call("mira_create_task", create(request)); backend.failStore = true; backend.deliver();
        assertEquals("ERROR", receipt(request).get("result"));
        assertTrue(backend.database.snapshot().list(Kind.TASK, workspace).isEmpty());
    }
    @Test public void privateWorkspaceAndTaskAreNotExposedToAnotherPlayer() {
        String id = task(), outsider = Model.id(); backend.database.rememberPlayer(outsider, "Visitor");
        backend.actor = outsider; backend.session = Model.id();
        Map<String, Object> visible = service.call("mira_list_workspaces", args());
        assertTrue(((List<?>) visible.get("entries")).isEmpty());
        assertThrows(Rejected.class, () -> service.call("mira_get_task", args("taskId", id)));
        assertThrows(Rejected.class, () -> service.call("mira_add_comment",
            args("requestId", Model.id(), "taskId", id, "text", "Unauthorized")));
        assertEquals(1, backend.submissions);
    }
    @Test public void oldScopeCannotSubmitToANewWorldAndUnknownIsNotReportedAsFailed() {
        Map<String, Object> old = create(Model.id()); backend.session = Model.id();
        assertThrows(IllegalStateException.class, () -> service.call("mira_create_task", old));
        assertEquals(0, backend.submissions);
        assertEquals("UNKNOWN", receipt(Model.id()).get("result"));
    }
    @Test public void schemaRejectsMissingVersionsSpoofedIdentityAndFractionalScope() {
        Map<String, Object> update = args("requestId", Model.id(), "taskId", Model.id(), "fields", map("title", "Edit"), "expected", map("priority", 0));
        assertThrows(IllegalArgumentException.class, () -> MiraTools.validate("mira_update_task", update));
        Map<String, Object> spoofed = create(Model.id()); spoofed.put("actor", owner);
        assertThrows(IllegalArgumentException.class, () -> MiraTools.validate("mira_create_task", spoofed));
        Map<String, Object> fractional = create(Model.id()); fractional.put("dimension", 0.25);
        assertThrows(IllegalArgumentException.class, () -> MiraTools.validate("mira_create_task", fractional));
    }
    @Test public void pagingRejectsRevisionChangesAndTextPagesStayBounded() {
        String id = task(); long revision = backend.database.revision();
        StringBuilder text = new StringBuilder(); for (int i = 0; i < 10000; i++) text.append('x');
        Entity task = backend.database.snapshot().require(id, Kind.TASK);
        Command edit = new Command(); edit.workspace = workspace; edit.patches.add(Patch.edit(task).set(task, "description", text.toString()));
        backend.database.execute(owner, "Owner", false, edit);
        assertThrows(IllegalStateException.class, () -> service.call("mira_list_tasks", args("workspaceId", workspace, "revision", revision)));
        Map<String, Object> first = service.call("mira_get_task", args("taskId", id));
        assertEquals(8192, ((String) first.get("text")).length());
        Map<String, Object> next = service.call("mira_get_task", args("taskId", id, "textOffset", first.get("nextTextOffset")));
        assertEquals(1808, ((String) next.get("text")).length()); assertNull(next.get("nextTextOffset"));
    }

    private final class MemoryBackend implements MiraTaskService.Backend {
        String session = Model.id(), actor = owner;
        boolean failStore; int submissions; Command pending; BiConsumer<String, String> callback;
        final JiraDatabase database = new JiraDatabase(new State(), state -> { if (failStore) throw new IOException("Disk full"); });
        public Map<String, Object> scope() { return map("session", session, "dimension", 0, "ready", true, "player", actor); }
        public State snapshot(String token, int dimension) { return database.view(actor); }
        public void submit(String token, int dimension, Command command, BiConsumer<String, String> complete) {
            submissions++; pending = command; callback = complete;
        }
        void deliver() {
            try { database.execute(actor, "Player", false, pending); callback.accept("OK", "Saved"); }
            catch (Conflict conflict) { callback.accept("CONFLICT", conflict.getMessage()); }
            catch (Rejected rejected) { callback.accept("ERROR", rejected.getMessage()); }
        }
    }
}
