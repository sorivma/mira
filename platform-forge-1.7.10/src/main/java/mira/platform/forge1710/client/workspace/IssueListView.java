package mira.platform.forge1710.client.workspace;

import java.util.List;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.WorkspaceData.Issue;

import static mira.platform.forge1710.client.ui.Ui.*;

final class IssueListView {
    private final WorkspaceSession session;
    private final Ui.Scroll scroll = new Ui.Scroll();
    private int filterRevision = -1;

    IssueListView(WorkspaceSession session) { this.session = session; }

    Node build() {
        if (filterRevision != session.filterRevision()) {
            scroll.reset();
            filterRevision = session.filterRevision();
        }
        Node list = column(2).pad(6).inset().scroll(scroll);
        List<Issue> issues = session.issues();
        list.add(heading(issues.size() + " issues", 0xFF202020).height(18), rule());
        for (Issue issue : issues) list.add(IssueWidgets.issueRow(issue, () -> session.select(issue)));
        if (issues.isEmpty()) list.add(column(6).height(44).add(
            text("No matches. Clear the search or filters.", 0xFF202020), button("Reset filters", session::clearFilters).width(100)));
        return list;
    }
}
