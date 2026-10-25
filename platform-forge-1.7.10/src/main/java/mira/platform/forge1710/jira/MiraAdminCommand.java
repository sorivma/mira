package mira.platform.forge1710.jira;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import mira.core.jira.JiraDatabase;
import mira.core.jira.Model.*;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Explicit, audited recovery path. Operator status never implicitly grants workspace UI access. */
public final class MiraAdminCommand extends CommandBase {
    @Override public String getCommandName() { return "miraadmin"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/miraadmin list | role <workspace UUID> <known player name/UUID> <OWNER|EDITOR|VIEWER|NONE>"; }
    @Override public void processCommand(ICommandSender sender, String[] args) {
        try {
            if (!canCommandSenderUseCommand(sender)) throw new Rejected("Operator permission is required");
            JiraDatabase db = JiraNetwork.database(); State state = db.snapshot();
            if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                for (Entity e : state.entities.values()) if (e.kind == Kind.WORKSPACE) sender.addChatMessage(new ChatComponentText(e.id + "  " + e.get("name")));
                return;
            }
            if (args.length != 4 || !args[0].equalsIgnoreCase("role")) { sender.addChatMessage(new ChatComponentText(getCommandUsage(sender))); return; }
            String target = state.players.containsKey(args[2]) ? args[2] : "";
            for (Map.Entry<String, String> player : state.players.entrySet()) if (player.getValue().equalsIgnoreCase(args[2])) {
                if (!target.isEmpty() && !target.equals(player.getKey())) throw new Rejected("Ambiguous nickname; use the player's UUID"); target = player.getKey();
            }
            if (target.isEmpty()) throw new Rejected("Player has not joined this world");
            String actor = sender instanceof EntityPlayerMP ? ((EntityPlayerMP) sender).getUniqueID().toString()
                : UUID.nameUUIDFromBytes("mira:server-console".getBytes(StandardCharsets.UTF_8)).toString();
            db.rememberPlayer(actor, sender.getCommandSenderName());
            Command c = new Command(); c.operation = Operation.ADMIN_ROLE; c.workspace = args[1]; c.arguments.put("player", target); c.arguments.put("role", args[3].toUpperCase(java.util.Locale.ROOT));
            db.execute(actor, sender.getCommandSenderName(), true, c);
            sender.addChatMessage(new ChatComponentText("Mira access updated and recorded in workspace activity."));
        } catch (Rejected | IOException e) { sender.addChatMessage(new ChatComponentText("Mira: " + e.getMessage())); }
    }
}
