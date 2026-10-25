package mira.platform.forge1710.client.workspace;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import mira.core.jira.Model.Entity;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.WorkspaceData.*;
import static mira.platform.forge1710.client.ui.Ui.*;

/** Column composition; the server decides which transitions a move may perform. */
final class BoardView {
    private final WorkspaceSession session;
    private final Map<String, Ui.Scroll> scrolls = new HashMap<>();
    private final Ui.Scroll boardScroll = new Ui.Scroll();
    private Ui ui;
    BoardView(WorkspaceSession session) { this.session = session; }
    Node build(Consumer<Status> create, Ui ui) {
        this.ui = ui;
        List<BoardColumn> columns = session.columns();
        if (columns.isEmpty()) return text("Configure board columns in Settings.");
        List<Issue> issues = session.issues();
        Node result = row(5).minChildWidth(140).scroll(boardScroll);
        for (BoardColumn lane : columns) result.add(lane(lane, issues, create));
        return result;
    }
    Status creationStatus() { return session.statuses().length == 0 ? Status.OPEN : session.statuses()[0]; }
    private Node lane(BoardColumn lane, List<Issue> all, Consumer<Status> create) {
        List<Issue> issues = all.stream().filter(lane::contains).collect(Collectors.toList());
        Node cards = column(4).scroll(scrolls.computeIfAbsent(lane.id, key -> new Ui.Scroll()));
        for (Issue issue : issues) {
            Node card = IssueWidgets.card(issue, () -> session.select(issue));
            if (session.writable()) card.draggable(issue).dropBefore(Issue.class, moved -> drop(moved, lane, issue));
            cards.add(card);
        }
        if (issues.isEmpty()) cards.add(text("No matching issues", 0xFF202020).height(24));
        Node result = column(4).pad(4).inset();
        if (session.writable()) result.dropTarget(Issue.class, issue -> drop(issue, lane, null));
        result.add(row(3).height(20).add(Ui.heading(lane.name + " (" + issues.size() + ")", 0xFF202020),
            session.writable() ? button("+", () -> create.accept(creationStatus())).width(18).hint("New issue starts in its workflow's initial status") : space()));
        return result.add(cards);
    }
    private void drop(Issue issue, BoardColumn lane, Issue before) {
        if (issue == before || !session.writable()) return;
        if (session.remote == null) { if (before == null) session.moveIssue(issue, lane.statuses.get(0)); else session.moveIssueBefore(issue, before); return; }
        RemoteWorkspace remote = session.remote;
        List<Entity> choices = remote.transitions(issue).stream().filter(e -> lane.statuses.stream().anyMatch(s -> s.id.equals(e.get("to")))).collect(Collectors.toList());
        if (lane.contains(issue)) { remote.moveWithTransition(issue, null, before); return; }
        if (choices.isEmpty()) { session.remoteNotice("No allowed transition into " + lane.name); return; }
        if (choices.size() == 1) { remote.moveWithTransition(issue, choices.get(0), before); return; }
        Map<String, Entity> labels = new LinkedHashMap<>();
        for (Entity edge : choices) labels.put(edge.get("name") + " -> " + remote.statusLabel(issue, edge.get("to")) + " [" + edge.id.substring(0, 4) + "]", edge);
        ui.chooseAtFocus("Choose transition", labels.keySet().toArray(new String[0]), value -> remote.moveWithTransition(issue, labels.get(value), before));
    }
}
