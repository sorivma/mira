package mira.platform.forge1710.client.workspace;

/** Disposable samples used only by source-level UI fixtures. */
final class DemoWorkspace extends WorkspaceData {
    DemoWorkspace() {
        projects.add(new Project("Main Base", "BASE")); projects.add(new Project("Orbital Outpost", "ORBIT"));
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
