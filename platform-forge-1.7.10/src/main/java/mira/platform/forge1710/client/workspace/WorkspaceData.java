package mira.platform.forge1710.client.workspace;

import java.util.*;
import java.util.stream.Collectors;
import mira.core.jira.Model.Entity;
import mira.core.taskbody.*;

/** Detached UI projection. Permissions, workflow decisions, identifiers and persistence live on the server. */
class WorkspaceData {
    static final class Status {
        static final Status OPEN = new Status("open", "Open", "TODO", 0xFF404040);
        static final Status DOING = new Status("doing", "In progress", "IN_PROGRESS", 0xFF2B579A);
        static final Status REVIEW = new Status("review", "Review", "IN_PROGRESS", 0xFF865A12);
        static final Status DONE = new Status("done", "Done", "DONE", 0xFF286628);
        final String id, label, category; final int color;
        Status(String id, String label, String category, int color) { this.id = id; this.label = label; this.category = category; this.color = color; }
        static Status[] values() { return new Status[] {OPEN, DOING, REVIEW, DONE}; }
        boolean done() { return category.equals("DONE"); }
    }
    static final class Project {
        final String id, key; String name; Entity source;
        Project(String name, String key) { this(key, name, key); }
        Project(String id, String name, String key) { this.id = id; this.name = name; this.key = key; }
    }
    static final class Issue {
        final String id, key; final Project project;
        String title, description;
        Status status = Status.OPEN;
        String type = "Task", priority = "Medium", assignee = "Unassigned", sprint = "Backlog";
        String epic = "None", location = "No location attached", links = "No linked issues";
        final List<String> activity = new ArrayList<>(), comments = new ArrayList<>();
        final List<Entity> commentSources = new ArrayList<>(), blocks = new ArrayList<>();
        Entity source;
        TaskBody body = new TaskBody();
        Issue(Project project, String key, String title, String description) { this(key, project, key, title, description); }
        Issue(String id, Project project, String key, String title, String description) {
            this.id = id; this.project = project; this.key = key; this.title = title; this.description = description;
        }
        void transition(Status next) {
            if (status != next) { activity.add(status.label + " -> " + next.label); status = next; }
        }
    }
    static final class BoardColumn {
        final String id, name; final List<Status> statuses;
        BoardColumn(String id, String name, List<Status> statuses) { this.id = id; this.name = name; this.statuses = statuses; }
        boolean contains(Issue issue) { return statuses.stream().anyMatch(status -> status.id.equals(issue.status.id)); }
    }
    final List<Project> projects = new ArrayList<>();
    final List<Issue> issues = new ArrayList<>();
    private final Map<Project, Long> numbers = new HashMap<>();
    static final String[] PEOPLE = {"Unassigned", "Alex", "Sam", "You"};
    static final String[] TYPES = {"Task", "Story", "Bug", "Epic"};
    static final String[] PRIORITIES = {"Low", "Medium", "High", "Critical"};
    static final String[] SPRINTS = {"Backlog", "Sprint 4", "Sprint 5"};
    Issue create(Project project, String title, String description) {
        long number = numbers.getOrDefault(project, 0L) + 1; numbers.put(project, number);
        Issue issue = new Issue(project, project.key + "-" + number, title.trim(), description); issues.add(issue); return issue;
    }
    List<Issue> find(Project project, String query, String assignee, String sprint) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return issues.stream().filter(issue -> issue.project == project)
            .filter(issue -> searchText(issue).toLowerCase(Locale.ROOT).contains(needle))
            .filter(issue -> "Everyone".equals(assignee) || issue.assignee.equals(assignee))
            .filter(issue -> "All work".equals(sprint) || issue.sprint.equals(sprint)).collect(Collectors.toList());
    }
    private static String searchText(Issue issue) {
        StringBuilder text = new StringBuilder(issue.key).append(' ').append(issue.title).append(' ').append(issue.description);
        for (TaskWidget widget : issue.body.widgets) if (widget instanceof NoteWidget) text.append(' ').append(((NoteWidget) widget).text);
        return text.toString();
    }
}
