package mira.platform.forge1710.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mira.platform.forge1710.client.ui.Ui.Box;
import mira.platform.forge1710.client.ui.Ui.Node;

/** Pointer gesture state. A short press clicks; a drag commits only onto an accepting target. */
final class DragAndDrop {
    private final List<Target> targets = new ArrayList<>();
    private final List<PanZone> panZones = new ArrayList<>();
    private Press press;
    private int mouseX, mouseY;

    void beginFrame(int x, int y) { targets.clear(); panZones.clear(); move(x, y); }

    void press(Node view, Object payload, Box bounds, Box clickBounds, int x, int y, Runnable click) {
        press = new Press(view, payload, bounds, clickBounds, x, y, click);
        mouseX = x;
        mouseY = y;
    }

    void move(int x, int y) {
        mouseX = x;
        mouseY = y;
        if (press != null && !press.active) {
            int dx = x - press.x, dy = y - press.y;
            press.active = dx * dx + dy * dy >= 25;
        }
    }

    boolean target(Box bounds, Predicate<Object> accepts, Consumer<Object> drop) {
        targets.add(new Target(bounds, accepts, drop));
        return active() && bounds.contains(mouseX, mouseY) && accepts.test(press.payload);
    }

    void panZone(Ui.Scroll scroll, Box bounds, boolean horizontal) { panZones.add(new PanZone(scroll, bounds, horizontal)); }

    void tick() {
        if (!active()) return;
        boolean pannedX = false, pannedY = false;
        for (int i = panZones.size() - 1; i >= 0; i--) {
            PanZone zone = panZones.get(i);
            if (!zone.bounds.contains(mouseX, mouseY)) continue;
            if (zone.horizontal ? pannedX : pannedY) continue;
            int position = zone.horizontal ? mouseX : mouseY;
            int start = zone.horizontal ? zone.bounds.x : zone.bounds.y;
            int extent = zone.horizontal ? zone.bounds.w : zone.bounds.h;
            int edge = Math.min(18, extent / 3);
            if (position < start + edge) zone.scroll.nudge(-8);
            else if (position >= start + extent - edge) zone.scroll.nudge(8);
            if (zone.horizontal) pannedX = true; else pannedY = true;
        }
    }

    boolean release(int x, int y) {
        if (press == null) return false;
        move(x, y);
        Press released = press;
        press = null;
        if (!released.active) {
            if (released.clickBounds.contains(x, y) && released.click != null) { released.click.run(); return true; }
            return false;
        }
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target target = targets.get(i);
            if (target.bounds.contains(x, y) && target.accepts.test(released.payload)) {
                target.drop.accept(released.payload);
                return true;
            }
        }
        return false;
    }

    boolean cancel() { boolean hadPress = press != null; press = null; return hadPress; }
    boolean external(Object payload, int x, int y) {
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target target = targets.get(i);
            if (target.bounds.contains(x, y) && target.accepts.test(payload)) {
                target.drop.accept(payload); targets.clear(); return true;
            }
        }
        return false;
    }
    boolean pressed() { return press != null; }
    boolean active() { return press != null && press.active; }
    boolean isSource(Object payload) { return active() && press.payload == payload; }
    Node preview() { return press.view; }
    Box previewBounds() { return new Box(mouseX - (press.x - press.bounds.x), mouseY - (press.y - press.bounds.y), press.bounds.w, press.bounds.h); }

    private static final class Press {
        final Node view;
        final Object payload;
        final Box bounds;
        final Box clickBounds;
        final int x, y;
        final Runnable click;
        boolean active;
        Press(Node view, Object payload, Box bounds, Box clickBounds, int x, int y, Runnable click) {
            this.view = view; this.payload = payload; this.bounds = bounds; this.clickBounds = clickBounds;
            this.x = x; this.y = y; this.click = click;
        }
    }
    private static final class Target {
        final Box bounds;
        final Predicate<Object> accepts;
        final Consumer<Object> drop;
        Target(Box bounds, Predicate<Object> accepts, Consumer<Object> drop) {
            this.bounds = bounds; this.accepts = accepts; this.drop = drop;
        }
    }
    private static final class PanZone {
        final Ui.Scroll scroll;
        final Box bounds;
        final boolean horizontal;
        PanZone(Ui.Scroll scroll, Box bounds, boolean horizontal) { this.scroll = scroll; this.bounds = bounds; this.horizontal = horizontal; }
    }
}
