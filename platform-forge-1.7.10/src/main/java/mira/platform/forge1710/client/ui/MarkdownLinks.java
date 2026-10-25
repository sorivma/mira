package mira.platform.forge1710.client.ui;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;

/** Native confirmation for external links, with relative destinations delegated to the caller. */
public final class MarkdownLinks {
    private MarkdownLinks() {}
    public static void open(String destination, Consumer<String> openIssue) {
        final URI uri;
        try { uri = new URI(destination); } catch (Exception ignored) { return; }
        String scheme = uri.getScheme();
        if (scheme == null) { openIssue.accept(destination); return; }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http") && !scheme.equals("mailto")) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.gameSettings.chatLinks) return;
        GuiScreen previous = minecraft.currentScreen;
        minecraft.displayGuiScreen(new GuiConfirmOpenLink((accepted, id) -> {
            minecraft.displayGuiScreen(previous);
            if (accepted) {
                try {
                    if (schemeFor(uri).equals("mailto")) Desktop.getDesktop().mail(uri);
                    else Desktop.getDesktop().browse(uri);
                } catch (Exception ignored) { GuiScreen.setClipboardString(destination); }
            }
        }, destination, 0, true));
    }
    private static String schemeFor(URI uri) { return uri.getScheme().toLowerCase(Locale.ROOT); }
}
