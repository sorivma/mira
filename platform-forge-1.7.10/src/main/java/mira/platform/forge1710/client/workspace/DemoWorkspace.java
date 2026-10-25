package mira.platform.forge1710.client.workspace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import mira.core.taskbody.TaskBody;
import mira.core.taskbody.TaskWidget;
import mira.core.taskbody.NoteWidget;

/** Disposable presentation data. This is deliberately not Mira's domain model or storage. */
final class DemoWorkspace {
    enum Status {
        OPEN("Open", 0xFF404040), DOING("In progress", 0xFF2B579A),
        REVIEW("Review", 0xFF865A12), DONE("Done", 0xFF286628);
        final String label;
        final int color;
        Status(String label, int color) { this.label = label; this.color = color; }
    }

    static final class Project {
        final String name;
        final String key;
        Project(String name, String key) { this.name = name; this.key = key; }
    }

    static final class Issue {
        final Project project;
        final String key;
        String title;
        String description;
        Status status = Status.OPEN;
        String type = "Task";
        String priority = "Medium";
        String assignee = "Unassigned";
        String sprint = "Backlog";
        String epic = "Infrastructure";
        String location = "No location attached";
        String links = "No linked issues";
        final List<String> activity = new ArrayList<>();
        final List<String> comments = new ArrayList<>();
        TaskBody body = new TaskBody();

        Issue(Project project, String key, String title, String description) {
            this.project = project;
            this.key = key;
            this.title = title;
            this.description = description;
            activity.add("Created in the demo workspace");
        }

        void transition(Status next) {
            if (status != next) {
                activity.add(status.label + " -> " + next.label);
                status = next;
            }
        }
    }

    final List<Project> projects = Arrays.asList(
        new Project("Main Base", "BASE"), new Project("Orbital Outpost", "ORBIT"));
    final List<Issue> issues = new ArrayList<>();
    private final java.util.Map<Project, Long> numbers = new java.util.HashMap<>();
    static final String[] PEOPLE = {"Unassigned", "Alex", "Sam", "You"};
    static final String[] TYPES = {"Task", "Story", "Bug", "Epic"};
    static final String[] PRIORITIES = {"Low", "Medium", "High", "Critical"};
    static final String[] SPRINTS = {"Backlog", "Sprint 4", "Sprint 5"};

    DemoWorkspace() {
        Project base = projects.get(0);
        seed(base, "Upgrade steam supply", "Supply both turbines without starving ore processing. Check the buffer under sustained load.", Status.DOING, "Sam", "High", "Sprint 4");
        seed(base, "Build ore washing line", "Add an ore washer, route clean water and reserve space for a thermal centrifuge.", Status.OPEN, "Unassigned", "Medium", "Sprint 4");
        seed(base, "Finish turbine room", "Check maintenance access, lighting and cable routing before closing the walls.", Status.REVIEW, "Alex", "Medium", "Sprint 4");
        seed(base, "Separate oxygen storage", "Move oxygen away from the main workshop and label all fluid connections.", Status.OPEN, "Alex", "High", "Backlog");
        seed(base, "Repair water intake", "The pump runs dry after a chunk reload. Reproduce the failure and check the intake.", Status.DOING, "You", "Critical", "Sprint 4").type = "Bug";
        seed(base, "Mark cable routes", "Label voltage tiers along the maintenance corridor.", Status.DONE, "Sam", "Low", "Sprint 4");
        seed(base, "Automate steel production", "Plan blast furnace inputs, outputs and charcoal supply.", Status.OPEN, "Unassigned", "High", "Sprint 5").type = "Story";
        seed(base, "Expand the workshop", "Coordinate the next workshop expansion and the supporting power infrastructure.", Status.OPEN, "Alex", "Medium", "Backlog").type = "Epic";
        issues.get(0).location = "Overworld: 120 / 64 / -80";
        issues.get(0).links = "Blocks BASE-2";
        issues.get(0).comments.add("Sam: Pipe capacity needs another check.");
        seed(projects.get(1), "Prepare launch supplies", "Collect fuel, food and spare tools for the first outpost crew.", Status.OPEN, "You", "High", "Sprint 4");
        seed(projects.get(1), "Survey landing site", "Find a clear site and record safe approach coordinates.", Status.DOING, "Alex", "Medium", "Sprint 4");
    }

    Issue create(Project project, String title, String description) {
        long number = numbers.getOrDefault(project, 0L) + 1;
        numbers.put(project, number);
        Issue issue = new Issue(project, project.key + "-" + number, title.trim(), description);
        issues.add(issue);
        return issue;
    }

    List<Issue> find(Project project, String query, String assignee, String sprint) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return issues.stream().filter(issue -> issue.project == project)
            .filter(issue -> searchText(issue).toLowerCase(Locale.ROOT).contains(needle))
            .filter(issue -> "Everyone".equals(assignee) || issue.assignee.equals(assignee))
            .filter(issue -> "All work".equals(sprint) || issue.sprint.equals(sprint))
            .collect(Collectors.toList());
    }

    private String searchText(Issue issue) {
        StringBuilder text = new StringBuilder(issue.key).append(' ').append(issue.title).append(' ').append(issue.description);
        for (TaskWidget widget : issue.body.widgets) if (widget instanceof NoteWidget)
            text.append(' ').append(((NoteWidget) widget).text);
        return text.toString();
    }

    private Issue seed(Project project, String title, String description, Status status,
            String assignee, String priority, String sprint) {
        Issue issue = create(project, title, description);
        issue.status = status;
        issue.assignee = assignee;
        issue.priority = priority;
        issue.sprint = sprint;
        return issue;
    }

}
