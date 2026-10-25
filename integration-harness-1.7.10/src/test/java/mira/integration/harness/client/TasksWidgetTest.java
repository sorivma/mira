package mira.integration.harness.client;

import static org.junit.Assert.*;
import dev.minecraftharness.chat.Surface;
import dev.minecraftharness.chat.widget.WidgetCanvas;
import dev.minecraftharness.chat.widget.WidgetRegistry;
import java.util.*;
import org.junit.Test;

public class TasksWidgetTest {
    private static final String SESSION = "00000000-0000-0000-0000-000000000001";
    private static final String TASK = "00000000-0000-0000-0000-000000000002";
    private static String payload() { return "{\"session\":\"" + SESSION + "\",\"dimension\":0,\"taskIds\":[\"" + TASK + "\"]}"; }
    @Test public void parsingAndRenderingNeverExecuteAnAction() {
        Backend backend = new Backend(); TasksWidget widget = TasksWidget.parse(payload(), backend);
        assertNotNull(widget); assertEquals(0, backend.reads); assertEquals(0, backend.opens);
        List<Runnable> clicks = new ArrayList<>(); SurfaceStub surface = new SurfaceStub();
        widget.render(canvas(widget, surface, clicks));
        assertEquals(1, backend.reads); assertEquals(0, backend.opens); assertEquals(1, clicks.size());
        assertTrue(surface.text.contains("1 / 3 checklist items"));
        clicks.get(0).run(); assertEquals(1, backend.opens);
    }
    @Test public void staleSessionBetweenDrawingAndClickIsRechecked() {
        Backend backend = new Backend(); TasksWidget widget = TasksWidget.parse(payload(), backend);
        List<Runnable> clicks = new ArrayList<>(); widget.render(canvas(widget, new SurfaceStub(), clicks));
        backend.current = false; clicks.get(0).run(); assertEquals(0, backend.opens);
        clicks.clear(); SurfaceStub surface = new SurfaceStub(); widget.render(canvas(widget, surface, clicks));
        assertTrue(clicks.isEmpty()); assertTrue(surface.text.contains("Task unavailable"));
    }
    @Test public void invalidPayloadsRemainLiteralAndDoNotReadTheBackend() {
        Backend backend = new Backend();
        assertNull(TasksWidget.parse(payload().replace("\"dimension\":0", "\"dimension\":0.5"), backend));
        assertNull(TasksWidget.parse(payload().replace("\"dimension\":0", "\"dimension\":\"0\""), backend));
        assertNull(TasksWidget.parse(payload().replace("\"taskIds\":[", "\"command\":\"run\",\"taskIds\":["), backend));
        assertNull(TasksWidget.parse(payload().replace("[\"" + TASK + "\"]", "[]"), backend));
        assertNull(TasksWidget.parse(payload().replace("[\"" + TASK + "\"]", "[\"" + TASK + "\",\"" + TASK + "\"]"), backend));
        WidgetRegistry registry = new WidgetRegistry();
        registry.register("mira:tasks-v1", TasksWidget.instructions(), value -> TasksWidget.parse(value, backend));
        String invalid = "```mira:tasks-v1\n{\"command\":\"run\"}\n```";
        assertEquals(invalid, registry.parse(invalid).plainText());
        assertEquals(0, backend.reads); assertEquals(0, backend.opens);
    }
    @Test public void liveValuesUpdateAndNarrowCardsKeepCopyableReferences() {
        Backend backend = new Backend(); TasksWidget widget = TasksWidget.parse(payload(), backend);
        SurfaceStub before = new SurfaceStub(); widget.render(canvas(widget, before, new ArrayList<>()));
        backend.checked = 3;
        SurfaceStub after = new SurfaceStub(); widget.render(canvas(widget, after, new ArrayList<>()));
        assertTrue(after.text.contains("3 / 3 checklist items"));
        assertEquals(0, widget.height(100)); assertTrue(widget.plainText().contains(TASK));
    }
    private static WidgetCanvas canvas(TasksWidget widget, SurfaceStub surface, List<Runnable> clicks) {
        return new WidgetCanvas(surface, 0, 0, 300, widget.height(300),
            (id, label, x, y, width, height, callback) -> clicks.add(callback));
    }
    private static final class Backend implements TasksWidget.Backend {
        boolean current = true; int reads, opens, checked = 1;
        public TasksWidget.Card read(String session, int dimension, String taskId) {
            reads++; require(session, dimension, taskId);
            return new TasksWidget.Card("BASE-1", "Steam supply", "In progress", "IN_PROGRESS", "High", "Builder", checked, 3);
        }
        public void open(String session, int dimension, String taskId) { require(session, dimension, taskId); opens++; }
        private void require(String session, int dimension, String taskId) {
            if (!current || !session.equals(SESSION) || dimension != 0 || !taskId.equals(TASK)) throw new IllegalStateException("Expired");
        }
    }
    private static final class SurfaceStub implements Surface {
        final List<String> text = new ArrayList<>();
        public int textWidth(String text) { return text.length() * 6; }
        public int lineHeight() { return 9; }
        public void rectangle(int x, int y, int width, int height, int color) {}
        public void text(String text, int x, int y, int color) { this.text.add(text); }
        public void clip(int x, int y, int width, int height) {}
        public void unclip() {}
        public String clipboard() { return ""; }
        public void clipboard(String text) {}
        public void openResource(String location) {}
        public void closePanel() {}
        public void ordinaryChat() {}
    }
}
