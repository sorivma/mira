package mira.core.taskbody;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import mira.core.taskbody.MaterialsWidget.Material;

/** A read-only observation of one viewer's carried inventory, never a delivery/consumption action. */
public final class InventorySnapshot {
    public static final class Entry {
        public final ItemSpec item;
        public final long count;
        public final boolean bag;
        public Entry(ItemSpec item, long count, boolean bag) { this.item = item; this.count = count; this.bag = bag; }
    }
    public final List<Entry> entries = new ArrayList<>();
    public final List<String> unavailable = new ArrayList<>();
    public boolean server;

    /** Shared maximum-flow allocation prevents double counting and preserves scarce exact/main-only matches. */
    public Map<Material, Long> allocate(TaskBody body) {
        Map<Material, Long> result = new IdentityHashMap<>();
        List<Material> requirements = new ArrayList<>();
        for (TaskWidget widget : body.widgets) if (widget instanceof MaterialsWidget) {
            for (Material material : ((MaterialsWidget) widget).materials) {
                if (material.automatic) requirements.add(material);
                else result.put(material, 0L); // Manual checks do not reserve the viewer's items.
            }
        }
        int sink = 1 + entries.size() + requirements.size();
        Flow flow = new Flow(sink + 1);
        List<Flow.Edge> demands = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) flow.add(0, i + 1, Math.max(0, entries.get(i).count));
        for (int j = 0; j < requirements.size(); j++) {
            Material material = requirements.get(j);
            demands.add(flow.add(1 + entries.size() + j, sink, Math.max(0, material.required)));
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if ((!entry.bag || material.includeBags) && material.matches(entry.item))
                    flow.add(i + 1, 1 + entries.size() + j, Math.max(0, Math.min(entry.count, material.required)));
            }
        }
        flow.solve(0, sink);
        for (int j = 0; j < requirements.size(); j++) result.put(requirements.get(j), Math.max(0, requirements.get(j).required) - demands.get(j).capacity);
        return result;
    }

    private static final class Flow {
        static final class Edge {
            final int to, reverse; long capacity;
            Edge(int to, int reverse, long capacity) { this.to = to; this.reverse = reverse; this.capacity = capacity; }
        }
        final List<List<Edge>> graph = new ArrayList<>();
        final int[] level, cursor;
        Flow(int nodes) { level = new int[nodes]; cursor = new int[nodes]; for (int i = 0; i < nodes; i++) graph.add(new ArrayList<>()); }
        Edge add(int from, int to, long capacity) {
            Edge edge = new Edge(to, graph.get(to).size(), capacity);
            Edge reverse = new Edge(from, graph.get(from).size(), 0);
            graph.get(from).add(edge); graph.get(to).add(reverse); return edge;
        }
        void solve(int source, int sink) {
            while (levels(source, sink)) {
                java.util.Arrays.fill(cursor, 0);
                while (send(source, sink, Long.MAX_VALUE) > 0) {}
            }
        }
        boolean levels(int source, int sink) {
            java.util.Arrays.fill(level, -1); level[source] = 0;
            int[] queue = new int[level.length]; int head = 0, tail = 0; queue[tail++] = source;
            while (head < tail) {
                int from = queue[head++];
                for (Edge edge : graph.get(from)) if (edge.capacity > 0 && level[edge.to] < 0) {
                    level[edge.to] = level[from] + 1; queue[tail++] = edge.to;
                }
            }
            return level[sink] >= 0;
        }
        long send(int from, int sink, long limit) {
            if (from == sink) return limit;
            List<Edge> edges = graph.get(from);
            for (; cursor[from] < edges.size(); cursor[from]++) {
                Edge edge = edges.get(cursor[from]);
                if (edge.capacity <= 0 || level[edge.to] != level[from] + 1) continue;
                long used = send(edge.to, sink, Math.min(limit, edge.capacity));
                if (used > 0) { edge.capacity -= used; graph.get(edge.to).get(edge.reverse).capacity += used; return used; }
            }
            return 0;
        }
    }
}
