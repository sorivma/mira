package mira.platform.forge1710.client.workspace;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import mira.core.jira.BodyValues;
import mira.core.jira.Model.*;
import mira.core.jira.Workflows;
import mira.core.taskbody.ChecklistWidget;
import mira.core.taskbody.TaskWidget;
import mira.platform.forge1710.client.MiraClient;
import mira.platform.forge1710.jira.DatabaseCodec.Reply;
import net.minecraft.client.Minecraft;

/** Client-thread API for optional integrations. All writes still go through Mira's server channel. */
public final class MiraClientAccess {
    private static Object connection, world, player, backend;
    private static int scopeDimension;
    private static String session = UUID.randomUUID().toString();
    private static final Map<String, TaskCard> cards = new LinkedHashMap<>();
    private static long cardRevision = -1;
    private MiraClientAccess() {}

    public static final class Scope {
        public final String session, player, status;
        public final int dimension;
        public final long revision;
        public final boolean ready;
        private Scope(String session, int dimension, WorkspaceBackend source, boolean connected) {
            this.session = session;
            this.dimension = dimension;
            ready = connected && source.ready();
            player = ready ? source.player() : "";
            revision = ready ? source.state().revision : 0;
            status = connected ? source.status() : "Join a world with Mira installed on the server";
        }
    }

    /** A fresh token is issued after disconnect, world/dimension replacement or player replacement. */
    public static Scope scope() {
        Minecraft mc = Minecraft.getMinecraft();
        WorkspaceBackend current = JiraClient.backend();
        int dimension = mc.thePlayer == null ? 0 : mc.thePlayer.dimension;
        if (connection != mc.getNetHandler() || world != mc.theWorld || player != mc.thePlayer || backend != current || scopeDimension != dimension) {
            connection = mc.getNetHandler(); world = mc.theWorld; player = mc.thePlayer; backend = current;
            scopeDimension = dimension;
            session = UUID.randomUUID().toString();
            cards.clear(); cardRevision = -1;
        }
        return new Scope(session, dimension, current,
            mc.theWorld != null && mc.thePlayer != null && mc.getNetHandler() != null);
    }

    public static void requireScope(String expectedSession, int dimension) {
        Scope current = scope();
        if (!current.ready) throw new IllegalStateException(current.status);
        if (!current.session.equals(expectedSession) || current.dimension != dimension)
            throw new IllegalStateException("Mira session changed; request mira_status and a fresh task card");
    }

    /** Detached, permission-filtered snapshot. Never opens a second world database. */
    public static State snapshot(String expectedSession, int dimension) {
        requireScope(expectedSession, dimension);
        return JiraClient.backend().state().copy();
    }

    public static void submit(String expectedSession, int dimension, Command command, Consumer<Reply> complete) {
        requireScope(expectedSession, dimension);
        JiraClient.backend().submit(command, complete);
    }

    /** Small projection for live cards; avoids copying the entire database on each render. */
    public static TaskCard task(String expectedSession, int dimension, String taskId) {
        requireScope(expectedSession, dimension);
        State state = JiraClient.backend().state();
        Entity task = activeTask(state, taskId);
        if (cardRevision != state.revision) { cards.clear(); cardRevision = state.revision; }
        TaskCard cached = cards.get(taskId);
        if (cached != null) return cached;
        Entity project = state.require(task.get("project"), Kind.PROJECT);
        Entity status = Workflows.status(state, Workflows.workflow(state, project, task.get("type")), task.get("status"));
        int checked = 0, total = 0;
        for (Entity block : state.list(Kind.BLOCK, task.workspace)) if (!block.archived() && block.get("task").equals(task.id)) {
            TaskWidget widget = BodyValues.decode(block.get("content"));
            if (widget instanceof ChecklistWidget) for (ChecklistWidget.Step step : ((ChecklistWidget) widget).steps) {
                total++; if (step.checked) checked++;
            }
        }
        TaskCard card = new TaskCard(task.id, task.get("key"), task.get("title"), status.get("name"), status.get("category"),
            task.get("priority"), state.players.getOrDefault(task.get("assignee"), "Unassigned"), checked, total);
        if (cards.size() >= 128) cards.remove(cards.keySet().iterator().next());
        cards.put(taskId, card);
        return card;
    }

    private static Entity activeTask(State state, String taskId) {
        Entity task = state.require(taskId, Kind.TASK);
        if (task.archived() || state.require(task.workspace, Kind.WORKSPACE).archived()
                || state.require(task.get("project"), Kind.PROJECT).archived())
            throw new IllegalStateException("Task is archived; restore it in Mira to open this card");
        return task;
    }

    /** Explicit UI action. Payload parsing/rendering must never call this method. */
    public static void openTask(String expectedSession, int dimension, String taskId) {
        requireScope(expectedSession, dimension);
        activeTask(JiraClient.backend().state(), taskId);
        MiraClient.openTask(taskId);
    }

    public static final class TaskCard {
        public final String id, key, title, status, category, priority, assignee;
        public final int checked, total;
        private TaskCard(String id, String key, String title, String status, String category,
                String priority, String assignee, int checked, int total) {
            this.id = id; this.key = key; this.title = title; this.status = status; this.category = category;
            this.priority = priority; this.assignee = assignee; this.checked = checked; this.total = total;
        }
    }
}
