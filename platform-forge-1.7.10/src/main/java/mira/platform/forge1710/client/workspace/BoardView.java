package mira.platform.forge1710.client.workspace;

import java.util.List;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Issue;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Status;

import static mira.platform.forge1710.client.ui.Ui.*;

final class BoardView {
    private final WorkspaceSession session;
    private final Ui.Scroll[] scrolls = {new Ui.Scroll(), new Ui.Scroll(), new Ui.Scroll(), new Ui.Scroll()};
    private Status compactColumn = Status.OPEN;
    private int filterRevision = -1;
    private boolean compact;

    BoardView(WorkspaceSession session) { this.session = session; }

    Node build(int availableWidth, Consumer<Status> create) {
        if (filterRevision != session.filterRevision()) {
            for (Ui.Scroll scroll : scrolls) scroll.reset();
            filterRevision = session.filterRevision();
        }
        List<Issue> issues = session.issues();
        compact = availableWidth < 480;
        if (compact) {
            Node tabs = row(3).height(20);
            for (Status status : Status.values()) tabs.add(button(
                (status == Status.DOING ? "Doing" : status.label) + " " + count(status, issues), compactColumn == status,
                () -> compactColumn = status)
                .dropTarget(Issue.class, issue -> {
                    append(issue, status);
                    compactColumn = status;
                }).hint(status.label, "Drop a card here to move it"));
            tabs.add(button("+", () -> create.accept(compactColumn)).width(18).hint("New issue in " + compactColumn.label));
            return column(4).add(tabs, statusColumn(compactColumn, issues, false, create));
        }
        Node columns = row(5);
        for (Status status : Status.values()) columns.add(statusColumn(status, issues, true, create));
        return columns;
    }

    private long count(Status status, List<Issue> issues) { return issues.stream().filter(issue -> issue.status == status).count(); }
    Status creationStatus() { return compact ? compactColumn : Status.OPEN; }

    private Node statusColumn(Status status, List<Issue> allIssues, boolean showHeading, Consumer<Status> create) {
        List<Issue> issues = allIssues.stream().filter(issue -> issue.status == status).collect(Collectors.toList());
        Node cards = column(4).scroll(scrolls[status.ordinal()]);
        for (Issue issue : issues) cards.add(IssueWidgets.card(issue, () -> session.select(issue))
            .draggable(issue).dropBefore(Issue.class, moved -> session.moveIssueBefore(moved, issue)));
        if (issues.isEmpty()) cards.add(column(4).height(40).add(
            text("Drop a card here", 0xFF202020), text("or change filters", 0xFF202020)));
        Node lane = column(4).pad(4).inset().dropTarget(Issue.class, issue -> append(issue, status));
        if (showHeading) lane.add(row(3).height(20).add(heading(status.label + " (" + issues.size() + ")", 0xFF202020),
            button("+", () -> create.accept(status)).width(18).hint("New issue in " + status.label)));
        return lane.add(cards);
    }

    private void append(Issue issue, Status status) {
        session.moveIssue(issue, status);
        scrolls[status.ordinal()].end();
    }
}
