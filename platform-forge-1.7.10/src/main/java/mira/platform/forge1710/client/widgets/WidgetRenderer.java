package mira.platform.forge1710.client.widgets;

import mira.core.taskbody.TaskWidget;
import mira.platform.forge1710.client.ui.Ui;

/** Register another typed body widget without changing the issue screen/editor. */
public interface WidgetRenderer<T extends TaskWidget> {
    Class<T> type();
    String name();
    T create();
    default boolean textBlock() { return false; }
    View build(T widget, WidgetContext context);
    final class View {
        public final Ui.Node node;
        public final int height;
        public View(Ui.Node node, int height) { this.node = node; this.height = height; }
    }
}
