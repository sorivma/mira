package mira.platform.forge1710.client.workspace;

import java.util.List;
import java.util.stream.Collectors;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Issue;

import static mira.platform.forge1710.client.ui.Ui.*;

final class BacklogView {
    private final WorkspaceSession session;
    private final Ui.Scroll scroll = new Ui.Scroll();
    private int filterRevision = -1;

    BacklogView(WorkspaceSession session) { this.session = session; }

    Node build() {
        if (filterRevision != session.filterRevision()) {
            scroll.reset();
            filterRevision = session.filterRevision();
        }
        Node list = column(2).pad(6).inset().scroll(scroll);
        List<Issue> issues = session.issues();
        for (String group : DemoWorkspace.SPRINTS) {
            List<Issue> groupIssues = issues.stream().filter(issue -> issue.sprint.equals(group)).collect(Collectors.toList());
            list.add(heading(group + " (" + groupIssues.size() + ")", 0xFF202020).height(22), rule());
            for (Issue issue : groupIssues) list.add(IssueWidgets.issueRow(issue, () -> session.select(issue)));
            if (groupIssues.isEmpty()) list.add(text("No matching issues", 0xFF202020).height(18));
        }
        return list;
    }
}
