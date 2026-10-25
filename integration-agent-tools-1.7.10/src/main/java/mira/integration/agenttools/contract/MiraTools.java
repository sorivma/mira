package mira.integration.agenttools.contract;

import static dev.minecraftagenttools.protocol.data.Data.map;
import java.util.*;

/** Java 8 contract shared by the provider and tick-thread adapter; no game or MCP runtime types. */
public final class MiraTools {
    private static final long MAX_INTEGER = 9007199254740991L;
    private static final List<Map<String, Object>> TOOLS = definitions();
    private MiraTools() {}

    public static List<Map<String, Object>> list() { return definitions(); }
    private static List<Map<String, Object>> definitions() {
        return Arrays.asList(
            tool("mira_status", "Discover Mira's independent client session, dimension, player and synchronization status.", false),
            tool("mira_list_workspaces", "List workspaces visible to the connected player and their role.", false, "offset?", "limit?", "revision?"),
            tool("mira_list_projects", "List active projects in a visible workspace.", false, "workspaceId", "offset?", "limit?", "revision?"),
            tool("mira_list_options", "List issue types, project sprints or known players for task creation and assignment. IDs, not display names, are used in writes.", false,
                "projectId", "kind", "offset?", "limit?", "revision?"),
            tool("mira_list_tasks", "Browse or search active tasks by literal key/title/legacy description. Optional project and workflow category filters. Includes live task-card references.", false,
                "workspaceId", "projectId?", "query?", "category?", "offset?", "limit?", "revision?"),
            tool("mira_get_task", "Read task fields and their versions. Page description, ordered body text, comments or allowed transitions using section. Body material values are requirements, not inventory observations.", false,
                "taskId", "section?", "offset?", "limit?", "textOffset?", "revision?"),
            tool("mira_create_task", "Queue a task in a project. fields requires title and type (issue type UUID). Server assigns key and initial status. PENDING is not success; poll mira_get_operation.", true,
                "requestId", "projectId", "fields"),
            tool("mira_update_task", "Queue field edits with expected versions from mira_get_task for exactly the supplied fields. Never silently retry a conflict with new versions. Poll mira_get_operation.", true,
                "requestId", "taskId", "fields", "expected"),
            tool("mira_transition_task", "Queue an allowed workflow transition using its logical transitionId and the observed status version. Server rechecks role and required fields. Poll mira_get_operation.", true,
                "requestId", "taskId", "transitionId", "expectedStatusVersion"),
            tool("mira_add_comment", "Queue a comment as the connected player. Reuse requestId only for an identical retry. Poll mira_get_operation.", true,
                "requestId", "taskId", "text"),
            tool("mira_get_operation", "Read PENDING, OK, CONFLICT, ERROR or UNKNOWN for a mutation. Only OK means the server acknowledged it. UNKNOWN is not proof of failure; re-read the task before retrying.", false,
                "requestId")
        );
    }

    private static Map<String, Object> tool(String name, String description, boolean write, String... arguments) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (!name.equals("mira_status")) {
            properties.put("session", uuid()); required.add("session");
            properties.put("dimension", integer(Integer.MIN_VALUE, Integer.MAX_VALUE)); required.add("dimension");
        }
        for (String argument : arguments) {
            boolean optional = argument.endsWith("?");
            String key = optional ? argument.substring(0, argument.length() - 1) : argument;
            properties.put(key, property(name, key));
            if (!optional) required.add(key);
        }
        return map("name", name, "description", description,
            "inputSchema", map("type", "object", "properties", properties, "required", required, "additionalProperties", false),
            "annotations", map("readOnlyHint", !write,
                "destructiveHint", name.equals("mira_update_task") || name.equals("mira_transition_task"),
                "idempotentHint", true, "openWorldHint", false));
    }

    private static Map<String, Object> property(String tool, String key) {
        if (key.endsWith("Id")) return uuid();
        switch (key) {
            case "offset": case "textOffset": return integer(0, Integer.MAX_VALUE);
            case "limit": return integer(1, 50);
            case "revision": case "expectedStatusVersion": return integer(0, MAX_INTEGER);
            case "query": return string(0, 256);
            case "text": return string(1, 8192);
            case "kind": return enumeration("types", "sprints", "players");
            case "category": return enumeration("TODO", "IN_PROGRESS", "DONE");
            case "section": return enumeration("description", "body", "comments", "transitions");
            case "fields": case "expected":
                boolean expected = key.equals("expected"), create = tool.equals("mira_create_task");
                Map<String, Object> fields = new LinkedHashMap<>();
                for (String field : Arrays.asList("title", "description", "priority", "assignee", "sprint", "epic"))
                    fields.put(field, expected ? integer(0, MAX_INTEGER) : field(field));
                if (create) fields.put("type", uuid());
                return map("type", "object", "properties", fields, "minProperties", 1,
                    "required", create ? Arrays.asList("title", "type") : Collections.emptyList(), "additionalProperties", false);
            default: throw new IllegalArgumentException("Unknown schema field " + key);
        }
    }

    private static Map<String, Object> field(String key) {
        if (key.equals("title")) return string(1, 160);
        if (key.equals("description")) return string(0, 262144);
        if (key.equals("priority")) return enumeration("Low", "Medium", "High", "Critical");
        // Empty assignment/sprint/parent explicitly clears the value.
        return map("type", "string", "maxLength", 36,
            "pattern", "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?$");
    }
    private static Map<String, Object> uuid() { return map("type", "string", "format", "uuid", "minLength", 36, "maxLength", 36); }
    private static Map<String, Object> string(int min, int max) { return map("type", "string", "minLength", min, "maxLength", max); }
    private static Map<String, Object> integer(long min, long max) { return map("type", "integer", "minimum", min, "maximum", max); }
    private static Map<String, Object> enumeration(String... values) { return map("type", "string", "enum", Arrays.asList(values)); }

    @SuppressWarnings("unchecked")
    public static void validate(String name, Map<String, Object> arguments) {
        for (Map<String, Object> tool : TOOLS) if (name.equals(tool.get("name"))) {
            validateValue((Map<String, Object>) tool.get("inputSchema"), arguments, "arguments");
            if (name.equals("mira_update_task") && !object(arguments, "fields").keySet().equals(object(arguments, "expected").keySet()))
                throw new IllegalArgumentException("expected must contain exactly the fields being changed");
            return;
        }
        throw new IllegalArgumentException("Unknown Mira tool: " + name);
    }

    @SuppressWarnings("unchecked")
    private static void validateValue(Map<String, Object> schema, Object value, String path) {
        String type = (String) schema.get("type");
        if (type.equals("object")) {
            if (!(value instanceof Map)) throw new IllegalArgumentException("Expected object: " + path);
            Map<?, ?> values = (Map<?, ?>) value;
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            for (Object key : (List<?>) schema.get("required")) if (!values.containsKey(key))
                throw new IllegalArgumentException("Missing " + path + "." + key);
            if (schema.containsKey("minProperties") && values.isEmpty()) throw new IllegalArgumentException("Empty " + path);
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                Object property = properties.get(entry.getKey());
                if (property == null) throw new IllegalArgumentException("Unknown field " + path + "." + entry.getKey());
                validateValue((Map<String, Object>) property, entry.getValue(), path + "." + entry.getKey());
            }
        } else if (type.equals("integer")) {
            if (!(value instanceof Number)) throw new IllegalArgumentException("Expected integer: " + path);
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number != Math.rint(number)
                    || number < ((Number) schema.get("minimum")).doubleValue() || number > ((Number) schema.get("maximum")).doubleValue())
                throw new IllegalArgumentException("Integer out of range: " + path);
        } else {
            if (!(value instanceof String)) throw new IllegalArgumentException("Expected string: " + path);
            String text = (String) value;
            if (text.length() < ((Number) schema.getOrDefault("minLength", 0)).intValue()
                    || text.length() > ((Number) schema.getOrDefault("maxLength", Integer.MAX_VALUE)).intValue()
                    || schema.containsKey("enum") && !((List<?>) schema.get("enum")).contains(text))
                throw new IllegalArgumentException("Invalid value: " + path);
            if (schema.containsKey("pattern") && !text.matches((String) schema.get("pattern")))
                throw new IllegalArgumentException("Expected UUID or empty string: " + path);
            if (schema.containsKey("format")) {
                try { if (!UUID.fromString(text).toString().equals(text)) throw new IllegalArgumentException(); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException("Expected canonical UUID: " + path); }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> args, String key) { return (Map<String, Object>) args.get(key); }
}
