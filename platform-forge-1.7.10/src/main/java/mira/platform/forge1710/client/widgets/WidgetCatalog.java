package mira.platform.forge1710.client.widgets;

import java.util.ArrayList;
import java.util.List;
import mira.core.taskbody.TaskWidget;

public final class WidgetCatalog {
    private static final List<WidgetRenderer<?>> renderers = new ArrayList<>();
    static {
        register(new NoteRenderer()); register(new ChecklistRenderer()); register(new MaterialsRenderer());
        register(new LocationRenderer()); register(new LinksRenderer());
    }
    private WidgetCatalog() {}
    public static void register(WidgetRenderer<?> renderer) {
        renderers.removeIf(value -> value.type().equals(renderer.type()));
        renderers.add(renderer);
    }
    public static String[] names() { return renderers.stream().map(WidgetRenderer::name).toArray(String[]::new); }
    public static boolean textBlock(TaskWidget widget) {
        for (WidgetRenderer<?> renderer : renderers) if (renderer.type().isInstance(widget)) return renderer.textBlock();
        return false;
    }
    public static String name(TaskWidget widget) {
        for (WidgetRenderer<?> renderer : renderers) if (renderer.type().isInstance(widget)) return renderer.name();
        return "Block";
    }
    public static TaskWidget create(String name) {
        return renderers.stream().filter(value -> value.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown widget: " + name)).create();
    }
    public static WidgetRenderer.View build(TaskWidget widget, WidgetContext context) {
        for (WidgetRenderer<?> renderer : renderers) if (renderer.type().isInstance(widget)) return buildTyped(renderer, widget, context);
        return new WidgetRenderer.View(mira.platform.forge1710.client.ui.Ui.text("Section renderer unavailable"), 12);
    }
    private static <T extends TaskWidget> WidgetRenderer.View buildTyped(WidgetRenderer<T> renderer, TaskWidget widget, WidgetContext context) {
        return renderer.build(renderer.type().cast(widget), context);
    }
}
