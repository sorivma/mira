package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.ui.Ui.Node;
import net.minecraft.client.gui.FontRenderer;
import static mira.platform.forge1710.client.ui.Ui.*;

/** Shared settings composition. Measurement, painting and input stay in Ui. */
final class SettingsLayout {
    final int width;
    private final FontRenderer font;
    SettingsLayout(FontRenderer font, int width) { this.font = font; this.width = width; }
    Node copy(String value) { return wrappedParagraph(font, value, width); }
    Node section(String title, String description, Node... controls) {
        Node group = column(6).add(heading(title));
        if (!description.isEmpty()) group.add(copy(description));
        return group.add(controls).fitHeight();
    }
    Node empty(String title, String description) { return section(title, description); }
    Node notice(String title, String description, int color) {
        return column(4).pad(7).background(PAPER).add(heading(title, color),
            wrappedParagraph(font, description, Math.max(1, width - 14))).fitHeight();
    }
    Node pair(Node first, Node second) {
        return width >= 340 ? row(10).add(first, second).fitHeight() : stack(7, first, second);
    }
    Node actions(Node... buttons) {
        return width >= buttons.length * 100 ? row(5).height(22).add(buttons) : stack(4, buttons);
    }
    Node primary(String label, boolean enabled, Runnable action) { return actionButton(label, enabled, true, action); }
    Node secondary(String label, boolean enabled, Runnable action) { return actionButton(label, enabled, false, action); }
    static String title(String tab) {
        if (tab.equals("Types")) return "Issue types";
        if (tab.equals("Schemes")) return "Workflow schemes";
        if (tab.equals("Board")) return "Board columns";
        if (tab.equals("Access")) return "People & access";
        return tab;
    }
    static String singular(mira.core.jira.Model.Kind kind) {
        switch (kind) {
            case WORKSPACE: return "workspace";
            case PROJECT: return "project";
            case SPRINT: return "sprint";
            case ISSUE_TYPE: return "issue type";
            case WORKFLOW: return "workflow";
            case SCHEME: return "scheme";
            case COLUMN: return "column";
            default: return "item";
        }
    }
    static String sentence(String value) {
        String words = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return words.isEmpty() ? "" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
