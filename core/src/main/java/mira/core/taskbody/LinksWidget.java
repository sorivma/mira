package mira.core.taskbody;

import java.util.ArrayList;
import java.util.List;

public final class LinksWidget extends TaskWidget {
    public final List<String> blockers = new ArrayList<>();
    public LinksWidget() { super("Blocked by"); }
    private LinksWidget(String id, String title) { super(id, title); }
    @Override public LinksWidget copy() { LinksWidget value = new LinksWidget(id, title); value.blockers.addAll(blockers); return value; }
}
