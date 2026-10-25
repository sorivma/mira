package mira.integration.harness.client;

import mira.platform.forge1710.client.workspace.MiraClientAccess;

/** Client-only adapter. Neither parsing nor drawing submits commands or opens screens. */
final class LiveTasks implements TasksWidget.Backend {
    @Override public TasksWidget.Card read(String session, int dimension, String taskId) {
        MiraClientAccess.TaskCard task = MiraClientAccess.task(session, dimension, taskId);
        return new TasksWidget.Card(task.key, task.title, task.status, task.category, task.priority,
            task.assignee, task.checked, task.total);
    }
    @Override public void open(String session, int dimension, String taskId) { MiraClientAccess.openTask(session, dimension, taskId); }
}
