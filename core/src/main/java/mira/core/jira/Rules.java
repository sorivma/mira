package mira.core.jira;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import mira.core.jira.Model.*;

/** Invariants are checked on the entire proposed transaction before persistence or publication. */
public final class Rules {
    private Rules() {}
    private static final Map<Kind, Set<String>> FIELDS = new LinkedHashMap<>();
    static {
        fields(Kind.WORKSPACE, "name owner archived");
        fields(Kind.MEMBER, "player role archived");
        fields(Kind.PROJECT, "name key scheme sequence archived");
        fields(Kind.ISSUE_TYPE, "name archived");
        fields(Kind.WORKFLOW, "name initial draftInitial generation archived");
        fields(Kind.STATUS, "workflow generation logical name category x y archived");
        fields(Kind.TRANSITION, "workflow generation logical name from to role required archived");
        fields(Kind.SCHEME, "name generation archived");
        fields(Kind.MAPPING, "scheme generation type workflow archived");
        fields(Kind.COLUMN, "project name statuses rank archived");
        fields(Kind.SPRINT, "project name state archived");
        fields(Kind.TASK, "project key title description type priority assignee sprint status epic rank archived");
        fields(Kind.BLOCK, "task content rank archived");
        fields(Kind.COMMENT, "task author text archived");
    }
    private static void fields(Kind kind, String keys) { FIELDS.put(kind, new HashSet<>(Arrays.asList(keys.split(" ")))); }
    public static boolean field(Kind kind, String key) { return FIELDS.get(kind).contains(key); }
    public static boolean ownerOnly(Kind kind) {
        return kind == Kind.WORKSPACE || kind == Kind.MEMBER || kind == Kind.ISSUE_TYPE || kind == Kind.WORKFLOW
            || kind == Kind.STATUS || kind == Kind.TRANSITION || kind == Kind.SCHEME || kind == Kind.MAPPING;
    }
    public static boolean immutable(Kind kind, String key) {
        return key.equals("project") || key.equals("task") || key.equals("author") || key.equals("player")
            || key.equals("workflow") && (kind == Kind.STATUS || kind == Kind.TRANSITION) || key.equals("scheme") && kind == Kind.MAPPING
            || key.equals("logical") || key.equals("generation") || key.equals("key");
    }
    public static void validate(State s) {
        if (s.entities.size() > 100000) throw new Rejected("World database entity limit reached");
        if (s.players.size() > 100000) throw new Rejected("World player directory limit reached");
        for (Map.Entry<String, String> player : s.players.entrySet()) { uuid(player.getKey()); text(player.getValue(), 1, 80); }
        Set<String> unique = new HashSet<>();
        for (Entity e : s.entities.values()) {
            uuid(e.id); uuid(e.workspace);
            Entity w = s.require(e.workspace, Kind.WORKSPACE);
            if (!w.id.equals(w.workspace)) throw new Rejected("Invalid workspace identity");
            for (Map.Entry<String, String> f : e.fields.entrySet()) {
                if (!field(e.kind, f.getKey()) || f.getValue() == null || f.getValue().length() > 1048576)
                    throw new Rejected("Invalid field: " + f.getKey());
            }
            if (!e.get("archived").isEmpty() && !e.get("archived").equals("true") && !e.get("archived").equals("false"))
                throw new Rejected("Invalid archive flag");
            switch (e.kind) {
                case WORKSPACE:
                    name(e); uuid(e.get("owner")); knownPlayer(s, e.get("owner")); break;
                case MEMBER:
                    knownPlayer(s, e.get("player"));
                    if (!e.get("role").equals("EDITOR") && !e.get("role").equals("VIEWER")) throw new Rejected("Invalid member role");
                    distinct(unique, "member/" + e.workspace + "/" + e.get("player")); break;
                case PROJECT:
                    name(e);
                    if (!e.archived()) distinct(unique, "project-name/" + e.workspace + "/" + e.get("name").toLowerCase(java.util.Locale.ROOT));
                    if (!e.get("key").matches("[A-Z][A-Z0-9]{1,11}")) throw new Rejected("Project key must contain 2-12 uppercase letters/digits");
                    distinct(unique, "project/" + e.workspace + "/" + e.get("key"));
                    Entity scheme = same(s, e, "scheme", Kind.SCHEME);
                    if (!e.archived()) {
                        if (scheme.archived()) throw new Rejected("An active project cannot use an archived scheme");
                        for (Entity mapping : s.list(Kind.MAPPING, e.workspace)) if (!mapping.archived() && mapping.get("scheme").equals(scheme.id) && mapping.get("generation").equals(scheme.get("generation"))
                            && s.require(mapping.get("workflow"), Kind.WORKFLOW).archived()) throw new Rejected("An active project cannot use an archived workflow");
                    }
                    number(e.get("sequence"), 0, Long.MAX_VALUE - 1); break;
                case ISSUE_TYPE: name(e); if (!e.archived()) distinct(unique, "type/" + e.workspace + "/" + e.get("name").toLowerCase(java.util.Locale.ROOT)); break;
                case WORKFLOW:
                    name(e); graph(s, e, false); graph(s, e, true); break;
                case STATUS:
                    name(e); same(s, e, "workflow", Kind.WORKFLOW); uuid(e.get("logical"));
                    enumValue(Category.class, e.get("category")); number(e.get("x"), 0, 10000); number(e.get("y"), 0, 10000); break;
                case TRANSITION:
                    name(e); same(s, e, "workflow", Kind.WORKFLOW); uuid(e.get("logical"));
                    if (!e.get("role").equals("EDITOR") && !e.get("role").equals("OWNER")) throw new Rejected("Invalid transition role");
                    for (String f : Workflows.ids(e.get("required"))) if (!Arrays.asList("title", "description", "assignee", "priority", "sprint", "epic").contains(f))
                        throw new Rejected("Unsupported required field: " + f);
                    break;
                case SCHEME:
                    name(e); scheme(s, e, false); scheme(s, e, true); break;
                case MAPPING:
                    same(s, e, "scheme", Kind.SCHEME); same(s, e, "workflow", Kind.WORKFLOW);
                    if (!e.get("type").equals("*")) same(s, e, "type", Kind.ISSUE_TYPE); break;
                case COLUMN:
                    name(e); same(s, e, "project", Kind.PROJECT); rank(e);
                    for (String id : Workflows.ids(e.get("statuses"))) { uuid(id); if (!e.archived()) distinct(unique, "column-status/" + e.get("project") + "/" + id); } break;
                case SPRINT:
                    name(e); same(s, e, "project", Kind.PROJECT);
                    if (!e.archived()) distinct(unique, "sprint-name/" + e.get("project") + "/" + e.get("name").toLowerCase(java.util.Locale.ROOT));
                    if (!Arrays.asList("PLANNED", "ACTIVE", "COMPLETED").contains(e.get("state"))) throw new Rejected("Invalid sprint state");
                    if (!e.archived() && e.get("state").equals("ACTIVE")) distinct(unique, "active/" + e.get("project")); break;
                case TASK:
                    Entity project = same(s, e, "project", Kind.PROJECT);
                    same(s, e, "type", Kind.ISSUE_TYPE);
                    text(e.get("title"), 1, 160); text(e.get("description"), 0, 262144); rank(e);
                    if (!Arrays.asList("Low", "Medium", "High", "Critical").contains(e.get("priority"))) throw new Rejected("Invalid priority");
                    if (!e.get("assignee").isEmpty()) knownPlayer(s, e.get("assignee"));
                    if (!e.get("sprint").isEmpty() && !same(s, e, "sprint", Kind.SPRINT).get("project").equals(project.id)) throw new Rejected("Sprint belongs to another project");
                    if (!e.get("epic").isEmpty()) {
                        Entity epic = same(s, e, "epic", Kind.TASK);
                        if (!epic.get("project").equals(project.id) || epic.id.equals(e.id)) throw new Rejected("Invalid parent epic");
                        Set<String> parents = new HashSet<>(); parents.add(e.id);
                        while (epic != null) {
                            if (!parents.add(epic.id)) throw new Rejected("Cyclic task hierarchy");
                            epic = epic.get("epic").isEmpty() ? null : s.require(epic.get("epic"), Kind.TASK);
                        }
                    }
                    if (!e.get("key").matches(java.util.regex.Pattern.quote(project.get("key")) + "-[1-9][0-9]*")) throw new Rejected("Invalid issue key");
                    distinct(unique, "task/" + e.workspace + "/" + e.get("key"));
                    Workflows.status(s, Workflows.workflow(s, project, e.get("type")), e.get("status")); break;
                case BLOCK:
                    same(s, e, "task", Kind.TASK); rank(e);
                    if (!BodyValues.decode(e.get("content")).id.equals(e.id)) throw new Rejected("Block identity does not match its contents"); break;
                case COMMENT:
                    same(s, e, "task", Kind.TASK); knownPlayer(s, e.get("author")); text(e.get("text"), 1, 8192); break;
                default: throw new Rejected("Unsupported entity");
            }
        }
        for (Entity workspace : s.entities.values()) if (workspace.kind == Kind.WORKSPACE && !workspace.archived()
            && s.list(Kind.ISSUE_TYPE, workspace.id).stream().noneMatch(e -> !e.archived())) throw new Rejected("Keep at least one active issue type");
    }
    private static void scheme(State s, Entity scheme, boolean draft) {
        String generation = draft ? "draft" : scheme.get("generation");
        Set<String> types = new HashSet<>();
        for (Entity e : s.list(Kind.MAPPING, scheme.workspace)) if (!e.archived() && e.get("scheme").equals(scheme.id) && e.get("generation").equals(generation)) {
            if (!types.add(e.get("type"))) throw new Rejected("Issue type is mapped more than once");
        }
        if (!types.contains("*")) throw new Rejected("A workflow scheme needs a default workflow");
    }
    private static void graph(State s, Entity workflow, boolean draft) {
        Set<String> nodes = new HashSet<>(), edges = new HashSet<>(), names = new HashSet<>();
        for (Entity node : Workflows.nodes(s, workflow, draft)) {
            if (!nodes.add(node.get("logical"))) throw new Rejected("Duplicate workflow status");
            if (!names.add(node.get("name").trim().toLowerCase(java.util.Locale.ROOT))) throw new Rejected("Status names must be unique within a workflow");
        }
        if (!nodes.contains(workflow.get(draft ? "draftInitial" : "initial"))) throw new Rejected("Choose a valid initial status");
        for (Entity edge : Workflows.edges(s, workflow, draft)) {
            if (!edges.add(edge.get("logical"))) throw new Rejected("Duplicate transition");
            if ((!edge.get("from").equals("*") && !nodes.contains(edge.get("from"))) || !nodes.contains(edge.get("to")))
                throw new Rejected("Transition references an absent status");
        }
    }
    static Entity same(State s, Entity e, String field, Kind kind) {
        Entity other = s.require(e.get(field), kind);
        if (!other.workspace.equals(e.workspace)) throw new Rejected("Cross-workspace reference");
        return other;
    }
    static void knownPlayer(State s, String id) { uuid(id); if (!s.players.containsKey(id)) throw new Rejected("Player has not joined this world"); }
    static void text(String text, int min, int max) { if (text == null || text.trim().length() < min || text.length() > max) throw new Rejected("Text must contain " + min + "-" + max + " characters"); }
    private static void name(Entity e) { text(e.get("name"), 1, 80); }
    private static void distinct(Set<String> set, String key) { if (!set.add(key)) throw new Rejected("Duplicate identity: " + key); }
    static void uuid(String value) {
        try { if (value == null || !java.util.UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new Rejected("Invalid identifier"); }
    }
    private static void rank(Entity e) {
        try { double rank = Double.parseDouble(e.get("rank")); if (!Double.isFinite(rank)) throw new NumberFormatException(); }
        catch (NumberFormatException x) { throw new Rejected("Invalid ordering position"); }
    }
    private static void number(String value, long min, long max) {
        try { long n = Long.parseLong(value); if (n < min || n > max) throw new NumberFormatException(); }
        catch (NumberFormatException x) { throw new Rejected("Number out of range"); }
    }
    private static <E extends Enum<E>> void enumValue(Class<E> type, String value) {
        try { Enum.valueOf(type, value); } catch (IllegalArgumentException e) { throw new Rejected("Invalid " + type.getSimpleName()); }
    }
}
