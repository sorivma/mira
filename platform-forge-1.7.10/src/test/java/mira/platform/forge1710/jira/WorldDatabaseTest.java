package mira.platform.forge1710.jira;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import mira.core.jira.JiraDatabase;
import mira.core.jira.Model;
import mira.core.jira.Model.*;
import mira.core.jira.BodyValues;
import mira.core.taskbody.NoteWidget;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WorldDatabaseTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void worldFileRestoresPermissionsVersionsAndTaskBodies() throws IOException {
        Path world = folder.newFolder("world").toPath(); String owner = Model.id(); JiraDatabase db = WorldDatabase.openDirectory(world);
        db.rememberPlayer(owner, "Builder"); Command create = new Command(); create.operation = Operation.CREATE_WORKSPACE; create.workspace = Model.id(); create.arguments.put("name", "Base");
        db.execute(owner, "Builder", false, create);
        Entity project = db.snapshot().list(Kind.PROJECT, create.workspace).get(0), type = db.snapshot().list(Kind.ISSUE_TYPE, create.workspace).get(0);
        Command task = new Command(); task.workspace = create.workspace; String taskId = Model.id(); NoteWidget note = new NoteWidget(); note.text = "Persisted description";
        task.patches.add(new Patch(taskId, Kind.TASK, create.workspace).set("project", project.id, 0).set("title", "Build", 0).set("type", type.id, 0).set("priority", "High", 0).set("rank", "0", 0));
        task.patches.add(new Patch(note.id, Kind.BLOCK, create.workspace).set("task", taskId, 0).set("content", BodyValues.encode(note), 0).set("rank", "0", 0));
        db.execute(owner, "Builder", false, task); State saved = db.snapshot();
        JiraDatabase restored = WorldDatabase.openDirectory(world); State loaded = restored.snapshot();
        assertEquals(saved.revision, loaded.revision); assertEquals(Role.OWNER, loaded.role(create.workspace, owner));
        assertArrayEquals(DatabaseCodec.state(saved), DatabaseCodec.state(loaded));
        assertTrue(Files.exists(world.resolve("data/mira-workspaces.dat.bak")));
        assertFalse(Files.exists(world.resolve("data/mira-workspaces.dat.tmp")));
        db.rememberPlayer(owner, "Renamed");
        assertEquals("Renamed", WorldDatabase.openDirectory(world).snapshot().players.get(owner));
    }
    @Test public void corruptMainFileFailsClosedEvenIfABackupExists() throws IOException {
        Path world = folder.newFolder("world").toPath(); JiraDatabase db = WorldDatabase.openDirectory(world); String player = Model.id();
        db.rememberPlayer(player, "Before"); db.rememberPlayer(player, "After");
        Path file = world.resolve("data/mira-workspaces.dat"); byte[] data = Files.readAllBytes(file); data[data.length - 1] ^= 1; Files.write(file, data);
        try { WorldDatabase.openDirectory(world); fail("Expected checksum rejection"); } catch (IOException expected) { assertTrue(expected.getMessage().contains("checksum")); }
    }
    @Test public void missingMainFileNeverSilentlyCreatesANewDatabaseOverABackup() throws IOException {
        Path world = folder.newFolder("world").toPath(); JiraDatabase db = WorldDatabase.openDirectory(world); String player = Model.id();
        db.rememberPlayer(player, "Before"); db.rememberPlayer(player, "After"); Files.delete(world.resolve("data/mira-workspaces.dat"));
        try { WorldDatabase.openDirectory(world); fail("Expected recovery requirement"); } catch (IOException expected) { assertTrue(expected.getMessage().contains("backup")); }
    }
    @Test public void protocolRoundTripsExpectedReadGuardsAndRejectsTruncation() throws IOException {
        Command c = new Command(); c.workspace = Model.id(); c.arguments.put("example", "value");
        Patch p = new Patch(Model.id(), Kind.TASK, c.workspace).set("title", "Steam supply", 7); p.expected.put("archived", 3L); c.patches.add(p);
        byte[] bytes = DatabaseCodec.command(c); Command restored = DatabaseCodec.command(bytes);
        assertEquals(c.id, restored.id); assertEquals(Long.valueOf(3), restored.patches.get(0).expected.get("archived"));
        try { DatabaseCodec.command(Arrays.copyOf(bytes, bytes.length - 1)); fail("Expected truncated message rejection"); } catch (IOException expected) { /* rejected */ }
    }
    @Test public void replyCarriesServerAuthenticatedIdentity() throws IOException {
        DatabaseCodec.Reply reply = new DatabaseCodec.Reply(); reply.player = Model.id(); reply.request = Model.id(); reply.state = new State();
        DatabaseCodec.Reply restored = DatabaseCodec.reply(DatabaseCodec.reply(reply));
        assertEquals(reply.player, restored.player); assertEquals(reply.request, restored.request); assertNotNull(restored.state);
    }
}
