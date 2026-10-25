package mira.core.jira;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Portable database values. Snapshots are detached; only JiraDatabase owns authoritative state. */
public final class Model {
    private Model() {}
    public enum Kind { WORKSPACE, MEMBER, PROJECT, ISSUE_TYPE, WORKFLOW, STATUS, TRANSITION, SCHEME, MAPPING, COLUMN, SPRINT, TASK, BLOCK, COMMENT }
    public enum Role { NONE, VIEWER, EDITOR, OWNER }
    public enum Category { TODO, IN_PROGRESS, DONE }
    public enum Operation { CREATE_WORKSPACE, CREATE_PROJECT, CREATE_WORKFLOW, CREATE_SCHEME, EDIT, PUBLISH, COMPLETE_SPRINT, TRANSFER, ADMIN_ROLE, UNDO }

    public static String id() { return UUID.randomUUID().toString(); }
    public static final class Entity {
        public final String id, workspace;
        public final Kind kind;
        public final Map<String, String> fields = new LinkedHashMap<>();
        public final Map<String, Long> versions = new LinkedHashMap<>();
        public Entity(String id, Kind kind, String workspace) { this.id = id; this.kind = kind; this.workspace = workspace; }
        public String get(String field) { return fields.getOrDefault(field, ""); }
        public long version(String field) { return versions.getOrDefault(field, 0L); }
        public boolean archived() { return "true".equals(get("archived")); }
        public Entity copy() { Entity e = new Entity(id, kind, workspace); e.fields.putAll(fields); e.versions.putAll(versions); return e; }
    }
    public static final class State {
        public long revision;
        public final Map<String, Entity> entities = new LinkedHashMap<>();
        public final Map<String, String> players = new LinkedHashMap<>();
        public final List<Activity> activity = new ArrayList<>();
        public State copy() {
            State s = new State(); s.revision = revision; s.players.putAll(players);
            for (Entity e : entities.values()) s.entities.put(e.id, e.copy());
            for (Activity a : activity) s.activity.add(a.copy());
            return s;
        }
        public Entity require(String id, Kind kind) {
            Entity e = entities.get(id);
            if (e == null || e.kind != kind) throw new Rejected("Unknown " + kind + ": " + id);
            return e;
        }
        public List<Entity> list(Kind kind, String workspace) {
            List<Entity> result = new ArrayList<>();
            for (Entity e : entities.values()) if (e.kind == kind && e.workspace.equals(workspace)) result.add(e);
            return result;
        }
        public Role role(String workspace, String player) {
            Entity w = entities.get(workspace);
            if (w == null || w.kind != Kind.WORKSPACE) return Role.NONE;
            if (w.get("owner").equals(player)) return Role.OWNER;
            for (Entity m : list(Kind.MEMBER, workspace)) if (m.get("player").equals(player) && !m.archived())
                return Role.valueOf(m.get("role"));
            return Role.NONE;
        }
    }
    /** Expected versions apply only to fields actually written, allowing independent edits to merge. */
    public static final class Patch {
        public final String id, workspace;
        public final Kind kind;
        public final Map<String, String> values = new LinkedHashMap<>();
        public final Map<String, Long> expected = new LinkedHashMap<>();
        public Patch(String id, Kind kind, String workspace) { this.id = id; this.kind = kind; this.workspace = workspace; }
        public Patch set(String key, String value, long version) { values.put(key, value); expected.put(key, version); return this; }
        public static Patch edit(Entity e) { return new Patch(e.id, e.kind, e.workspace); }
        public Patch set(Entity source, String key, String value) { return set(key, value, source.version(key)); }
    }
    public static final class Command {
        public String id = Model.id(), workspace = "", target = "";
        public Operation operation = Operation.EDIT;
        public final Map<String, String> arguments = new LinkedHashMap<>();
        public final List<Patch> patches = new ArrayList<>();
    }
    public static final class Difference {
        public String entity, field, before, after;
        public long version;
        public boolean created;
        public Difference copy() {
            Difference d = new Difference(); d.entity = entity; d.field = field; d.before = before; d.after = after;
            d.version = version; d.created = created; return d;
        }
    }
    public static final class Activity {
        public String id, workspace, actor, name, summary, undoneBy = "";
        public long time, revision;
        public boolean undoable;
        public final List<Difference> differences = new ArrayList<>();
        public Activity copy() {
            Activity a = new Activity(); a.id = id; a.workspace = workspace; a.actor = actor; a.name = name;
            a.summary = summary; a.undoneBy = undoneBy; a.time = time; a.revision = revision; a.undoable = undoable;
            for (Difference d : differences) a.differences.add(d.copy()); return a;
        }
    }
    public static class Rejected extends RuntimeException {
        public Rejected(String message) { super(message); }
    }
    public static final class Conflict extends Rejected {
        public Conflict(String message) { super(message); }
    }
}
