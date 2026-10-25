package mira.platform.forge1710.client.workspace;

import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import mira.platform.forge1710.client.ui.Ui.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerInfo;

import static mira.platform.forge1710.client.ui.Ui.*;

/** Client roster presentation; authoritative membership/permissions are future server work. */
final class WorkspacePlayers {
    private WorkspacePlayers() {}

    static String[] options(String current, String self) {
        Set<String> names = new LinkedHashSet<>();
        names.add("Unassigned");
        names.add(self);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getNetHandler() != null) for (Object value : minecraft.getNetHandler().playerInfoList)
            names.add(((GuiPlayerInfo) value).name);
        for (String name : DemoWorkspace.PEOPLE) if (!"You".equals(name)) names.add(name);
        names.add(current);
        return names.toArray(new String[0]);
    }

    static Node assignee(String current, String self, Consumer<String> choose) {
        Node field = row(4).height(20).add(playerSelect("Assignee", current, options(current, self), choose));
        if (!current.equals(self)) field.add(button("Me", () -> choose.accept(self)).width(28).hint("Assign to " + self));
        return field;
    }

    static String[] filters(String current, String self) {
        Set<String> names = new LinkedHashSet<>();
        names.add("Everyone");
        Collections.addAll(names, options(current, self));
        return names.toArray(new String[0]);
    }
}
