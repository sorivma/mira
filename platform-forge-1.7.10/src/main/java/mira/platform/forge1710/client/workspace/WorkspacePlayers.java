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
        java.util.Map<String, String> players = JiraClient.backend().state().players;
        if (players.isEmpty()) names.add(self);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (players.isEmpty() && minecraft.getNetHandler() != null) for (Object value : minecraft.getNetHandler().playerInfoList)
            names.add(((GuiPlayerInfo) value).name);
        for (java.util.Map.Entry<String, String> player : players.entrySet()) {
            long count = players.values().stream().filter(player.getValue()::equalsIgnoreCase).count();
            names.add(count > 1 ? player.getValue() + " [" + player.getKey().substring(0, 8) + "]" : player.getValue());
        }
        names.add(current);
        return names.toArray(new String[0]);
    }

    static Node assignee(String current, String self, Consumer<String> choose) {
        java.util.Map<String, String> players = JiraClient.backend().state().players;
        String id = JiraClient.backend().player(), nickname = players.getOrDefault(id, self);
        long count = players.values().stream().filter(nickname::equalsIgnoreCase).count();
        String me = count > 1 ? nickname + " [" + id.substring(0, 8) + "]" : nickname;
        Node field = row(4).height(20).add(playerSelect("Assignee", current, options(current, me), choose));
        if (!current.equals(me)) field.add(button("Me", () -> choose.accept(me)).width(28).hint("Assign to " + nickname));
        return field;
    }

    static String[] filters(String current, String self) {
        Set<String> names = new LinkedHashSet<>();
        names.add("Everyone");
        Collections.addAll(names, options(current, self));
        return names.toArray(new String[0]);
    }
}
