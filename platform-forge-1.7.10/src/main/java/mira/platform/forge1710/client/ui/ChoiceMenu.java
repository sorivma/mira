package mira.platform.forge1710.client.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import mira.platform.forge1710.client.ui.Ui.Node;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;
import static mira.platform.forge1710.client.ui.Ui.*;

/** Shared picker; player rosters and longer option lists have type-to-filter search. */
final class ChoiceMenu {
    final String label;
    final String[] options;
    private final Consumer<String> choose;
    private final boolean players, searchable;
    private final Ui.Scroll scroll = new Ui.Scroll();
    private int active;
    private String query = "";
    private TextInput search;
    int x, y, width, height;
    ChoiceMenu(String label, String value, String[] options, Consumer<String> choose, boolean players) {
        this(label, value, options, choose, players, false);
    }
    ChoiceMenu(String label, String value, String[] options, Consumer<String> choose, boolean players, boolean forceSearch) {
        this.label = label; this.options = options.clone(); this.choose = choose; this.players = players;
        searchable = forceSearch || players || options.length > 6;
        active = Math.max(0, Arrays.asList(options).indexOf(value));
    }
    void place(int anchorX, int anchorY, int anchorWidth, int anchorHeight, int screenWidth, int screenHeight) {
        width = Math.min(Math.max(players ? 164 : 132, anchorWidth), screenWidth - 12);
        height = Math.min(header() + options.length * 22, screenHeight - 12);
        x = Math.max(6, Math.min(anchorX, screenWidth - width - 6));
        y = anchorY + anchorHeight + 2;
        if (y + height > screenHeight - 6) y = Math.max(6, anchorY - height - 2);
        revealActive();
    }
    Node build(Runnable dismiss, FontRenderer font) {
        Node root = column(4).panel().pad(6).add(text(label, MUTED).height(12));
        if (searchable) {
            if (search == null) {
                search = new TextInput(font, 64, "Type to find", () -> query, value -> { query = value; active = 0; scroll.reset(); });
                search.focus(true);
            }
            root.add(Ui.field(search));
        }
        Node list = column(2).scroll(scroll);
        List<String> matches = matches();
        for (int i = 0; i < matches.size(); i++) {
            String option = matches.get(i);
            list.add(players ? playerButton(option, active == i, () -> accept(option, dismiss))
                : button(option, active == i, () -> accept(option, dismiss)));
        }
        if (matches.isEmpty()) list.add(text("No matches", MUTED));
        return root.add(list);
    }
    void key(char character, int key, Runnable dismiss) {
        List<String> matches = matches();
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_TAB) dismiss.run();
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (!matches.isEmpty()) accept(matches.get(Math.min(active, matches.size() - 1)), dismiss);
        } else if ((key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) && !matches.isEmpty()) {
            if (search != null && search.focused()) { search.focus(false); active = key == Keyboard.KEY_DOWN ? 0 : matches.size() - 1; }
            else active = (active + (key == Keyboard.KEY_UP ? matches.size() - 1 : 1)) % matches.size();
            revealActive();
        } else if (search != null) { search.focus(true); search.type(character, key); }
    }
    private List<String> matches() {
        return Arrays.stream(options).filter(value -> value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).collect(Collectors.toList());
    }
    private int header() { return searchable ? 52 : 28; }
    private void revealActive() { scroll.reveal(active * 22, active * 22 + 20, Math.max(1, height - header())); }
    private void accept(String option, Runnable dismiss) { dismiss.run(); choose.accept(option); }
}
