package mira.core.taskbody;

import java.util.ArrayList;
import java.util.List;

public final class MaterialsWidget extends TaskWidget {
    public enum Match { EXACT, ITEM_META, ITEM, ORE }
    public static final class Material {
        public final List<ItemSpec> alternatives = new ArrayList<>();
        public long required = 1;
        public Match match = Match.ITEM_META;
        public String ore = "";
        public boolean includeBags = true, automatic = true, checked;
        public Material(ItemSpec item) { alternatives.add(item); }
        public Material copy() {
            Material value = new Material(alternatives.get(0));
            value.alternatives.clear(); value.alternatives.addAll(alternatives);
            value.required = required; value.match = match; value.ore = ore;
            value.includeBags = includeBags; value.automatic = automatic; value.checked = checked;
            return value;
        }
        public boolean matches(ItemSpec item) {
            if (match == Match.ORE) return !ore.isEmpty() && item.ores.contains(ore);
            for (ItemSpec alternative : alternatives) {
                if (!alternative.item.equals(item.item)) continue;
                if (match == Match.ITEM) return true;
                if (alternative.damage != item.damage && alternative.damage != 32767) continue;
                if (match == Match.ITEM_META || alternative.tagHash.equals(item.tagHash)) return true;
            }
            return false;
        }
    }
    public final List<Material> materials = new ArrayList<>();
    public MaterialsWidget() { super("Materials"); }
    public MaterialsWidget(String id, String title) { super(id, title); }
    @Override public MaterialsWidget copy() {
        MaterialsWidget result = new MaterialsWidget(id, title);
        for (Material material : materials) result.materials.add(material.copy());
        return result;
    }
    @Override public TaskBody.Progress progress(TaskBody body, InventorySnapshot inventory) {
        java.util.Map<Material, Long> available = inventory.allocate(body);
        int done = 0;
        for (Material material : materials) if (material.automatic ? available.getOrDefault(material, 0L) >= material.required : material.checked) done++;
        return new TaskBody.Progress(done, materials.size());
    }
}
