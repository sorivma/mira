package mira.core.taskbody;

import java.util.UUID;

/** A typed, independently extensible part of a task's ordered body. */
public abstract class TaskWidget {
    public final String id;
    public String title;
    protected TaskWidget(String title) { this(UUID.randomUUID().toString(), title); }
    protected TaskWidget(String id, String title) { this.id = id; this.title = title; }
    public abstract TaskWidget copy();
    public TaskBody.Progress progress(TaskBody body, InventorySnapshot inventory) { return new TaskBody.Progress(0, 0); }
}
