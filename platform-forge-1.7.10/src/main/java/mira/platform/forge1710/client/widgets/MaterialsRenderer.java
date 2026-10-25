package mira.platform.forge1710.client.widgets;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import mira.core.taskbody.ItemSpec;
import mira.core.taskbody.MaterialsWidget;
import mira.core.taskbody.MaterialsWidget.Material;
import mira.platform.forge1710.client.ui.Ui;
import mira.platform.forge1710.inventory.ItemDescriptors;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import static mira.platform.forge1710.client.ui.Ui.*;

final class MaterialsRenderer implements WidgetRenderer<MaterialsWidget> {
    public Class<MaterialsWidget> type() { return MaterialsWidget.class; }
    public String name() { return "Materials"; }
    public MaterialsWidget create() { return new MaterialsWidget(); }
    static void add(MaterialsWidget widget, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        ItemSpec spec = ItemDescriptors.describe(stack);
        for (Material existing : widget.materials) {
            ItemSpec first = existing.alternatives.get(0);
            if (existing.alternatives.size() == 1 && first.item.equals(spec.item) && first.damage == spec.damage && first.tagHash.equals(spec.tagHash)) {
                existing.required = Math.min(1000000000L, existing.required + Math.max(1, stack.stackSize)); return;
            }
        }
        Material material = new Material(spec); material.required = Math.max(1, stack.stackSize); widget.materials.add(material);
    }
    public View build(MaterialsWidget widget, WidgetContext context) {
        Ui.Node rows = column(4);
        int height = 0;
        for (Material material : widget.materials) {
            long available = context.allocated.getOrDefault(material, 0L);
            boolean complete = material.automatic ? available >= material.required : material.checked;
            ItemSpec first = material.alternatives.get(0);
            ItemStack stack = ItemDescriptors.stack(first);
            Ui.Node identity = row(4).height(20).add(
                button(complete ? "x" : " ", () -> context.change(() -> { material.automatic = false; material.checked = !complete; })).width(18)
                    .hint("Completion", "Click to use a manual check; Auto restores inventory tracking"));
            if (stack != null) identity.add(item(stack).slot());
            identity.add(text(first.displayName + (material.alternatives.size() > 1 ? " + alternatives" : "")).hint(first.displayName));
            if (context.editing) identity.add(button("-", () -> context.change(() -> widget.materials.remove(material))).width(18).hint("Remove material"));
            String summary = material.automatic ? available + " / " + material.required + " carried"
                : material.required + " required / " + (complete ? "Done" : "Manual");
            int blockHeight = context.editing ? 141 : 43;
            Ui.Node block = column(4).add(identity,
                text(summary, complete ? 0xFF286628 : MUTED).height(12).hint(summary,
                    material.includeBags ? "Includes supported carried storage" : "Main inventory only"),
                progress(material.automatic ? available : complete ? material.required : 0, material.required)).height(blockHeight)
                .dropTarget(ItemStack.class, dropped -> context.change(() -> material.alternatives.add(ItemDescriptors.describe(dropped))))
                .hint("Drop an alternative here", "Your inventory; counts are shared across all material requirements");
            if (context.editing) {
                block.add(row(6).height(33).add(
                    setting("Required amount", Ui.field(context.input(material, 10, "Required amount", () -> Long.toString(material.required), value -> {
                        try { material.required = Math.max(1, Math.min(1000000000L, Long.parseLong(value))); } catch (NumberFormatException ignored) {}
                    }).preserveEditing()).hint("Required amount", "Positive whole number; invalid input keeps the previous amount")),
                    setting("Completion", select("Completion", material.automatic ? "Auto" : "Manual", new String[] {"Auto", "Manual"}, value -> context.change(() -> material.automatic = value.equals("Auto"))))));
                String[] matches = {"Exact data", "Same variant", "Any variant", "Ore group"};
                block.add(row(6).height(33).add(
                    setting("Match items", select("Match", matches[material.match.ordinal()], matches,
                        value -> context.change(() -> material.match = MaterialsWidget.Match.values()[Arrays.asList(matches).indexOf(value)]))
                        .hint("Same variant: item and variant", "Exact data: includes item data", "Any variant: any metadata", "Ore group: selected compatible material group")),
                    setting("Inventory source", select("Inventory source", material.includeBags ? "All carried" : "Main only", new String[] {"All carried", "Main only"}, value -> context.change(() -> material.includeBags = value.equals("All carried"))))));
                Set<String> ores = new LinkedHashSet<>();
                for (ItemSpec alternative : material.alternatives) ores.addAll(alternative.ores);
                if (material.match == MaterialsWidget.Match.ORE && !ores.isEmpty()) block.add(select("Ore group", material.ore.isEmpty() ? "Choose ore group" : material.ore,
                    ores.toArray(new String[0]), value -> context.change(() -> material.ore = value)).height(20));
                else if (material.match == MaterialsWidget.Match.ORE) block.add(text("This item has no ore group", ERROR).height(20));
                else if (material.alternatives.size() > 1) block.add(button("Reset alternatives (" + material.alternatives.size() + ")",
                    () -> context.change(() -> material.alternatives.subList(1, material.alternatives.size()).clear())).height(20));
                else block.add(text("Drop replacements onto this row", MUTED).height(20));
            }
            rows.add(block); height += blockHeight + 4;
        }
        if (context.editing) { rows.add(row(4).height(20).add(text("Drop items here", MUTED), button("Held item", () -> {
            ItemStack held = Minecraft.getMinecraft().thePlayer.getHeldItem();
            if (held != null) context.change(() -> add(widget, held.copy()));
        }).width(68)));
        height += 24; }
        else if (widget.materials.isEmpty()) { rows.add(text("Drop an item here or click to add materials.", MUTED).height(16)); height += 20; }
        if (!context.inventory.unavailable.isEmpty()) { rows.add(text("Some storage unavailable", ERROR).height(12).hint(context.inventory.unavailable.toArray(new String[0]))); height += 16; }
        rows.dropTarget(ItemStack.class, dropped -> context.change(() -> add(widget, dropped)));
        return new View(rows, Math.max(0, height - 4));
    }
    private Ui.Node setting(String label, Ui.Node control) {
        return column(3).height(33).add(text(label, MUTED).height(10), control);
    }
}
