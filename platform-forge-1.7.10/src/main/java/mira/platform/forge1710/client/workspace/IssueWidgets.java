package mira.platform.forge1710.client.workspace;

import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.client.ui.Ui.Node;
import mira.platform.forge1710.client.workspace.DemoWorkspace.Issue;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import mira.core.taskbody.TaskBody;
import mira.platform.forge1710.client.InventoryClient;

import static mira.platform.forge1710.client.ui.Ui.*;

/** Issue-specific visual vocabulary shared by the board, backlog and details. */
final class IssueWidgets {
    private IssueWidgets() {}

    static Node card(Issue issue, Runnable select) {
        TaskBody.Progress progress = issue.body.progress(InventoryClient.snapshot());
        Node footer = row(3).height(12).add(player(issue.assignee));
        if (progress.total > 0) footer.add(text(progress.done + "/" + progress.total,
            progress.done == progress.total ? 0xFF286628 : MUTED).width(32));
        Node card = column(5).height(progress.total > 0 ? 93 : 85).pad(6).panel().click(select)
            .hint(issue.title, issue.key + " - " + issue.type,
                issue.status.label + ": " + issue.assignee, "Priority: " + issue.priority, issue.sprint)
            .add(row(4).height(20).add(item(icon(issue)).slot(),
                    column(0).add(text(issue.key, issue.status.color).height(10), text(issue.type + " / " + issue.priority, MUTED).height(10)
                        .hint(issue.type, "Priority: " + issue.priority))),
                Ui.excerpt(issue.title, 3), footer);
        if (progress.total > 0) card.add(Ui.progress(progress.done, progress.total));
        return card;
    }

    static Node issueRow(Issue issue, Runnable select) {
        return row(6).height(40).pad(6).sheet().click(select)
            .hint(issue.title, issue.key + " - " + issue.type, "Priority: " + issue.priority, "Assignee: " + issue.assignee)
            .add(item(icon(issue)).slot(), column(3).add(
                text(issue.title).height(12),
                row(4).height(10).add(text(issue.key + "  " + issue.status.label, issue.status.color).height(10),
                    player(issue.assignee).height(10).width(108))));
    }

    static Node property(String label, Node control) {
        return column(3).height(33).add(text(label, MUTED).height(10), control);
    }

    static ItemStack icon(Issue issue) {
        switch (issue.key) {
            case "BASE-1": return new ItemStack(Blocks.furnace);
            case "BASE-2": return new ItemStack(Items.cauldron);
            case "BASE-3": return new ItemStack(Blocks.iron_block);
            case "BASE-4": return new ItemStack(Items.glass_bottle);
            case "BASE-5": return new ItemStack(Items.water_bucket);
            case "BASE-6": return new ItemStack(Items.redstone);
            case "BASE-7": return new ItemStack(Items.iron_ingot);
            case "BASE-8": return new ItemStack(Blocks.brick_block);
            case "ORBIT-1": return new ItemStack(Items.fireworks);
            case "ORBIT-2": return new ItemStack(Items.compass);
            default: return new ItemStack(Items.paper);
        }
    }

    static Node paragraph(FontRenderer font, String value, int width) {
        return Ui.wrappedParagraph(font, value, width);
    }
}
