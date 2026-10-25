package mira.core.taskbody;

import java.util.Arrays;
import java.util.Map;
import mira.core.taskbody.MaterialsWidget.Material;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure Java behavior; these tests do not initialize Minecraft or mod integrations. */
public class TaskBodyTest {
    private static ItemSpec item(String name, int meta, String tag, String... ores) {
        return new ItemSpec(name, meta, tag, Arrays.asList(ores), name, "");
    }
    private static Material require(MaterialsWidget widget, ItemSpec item, long count) {
        Material material = new Material(item); material.required = count; widget.materials.add(material); return material;
    }

    @Test public void matchingPoliciesAndAlternativesAreIndependent() {
        Material material = new Material(item("mod:part", 2, "tag-a", "plateIron"));
        assertTrue(material.matches(item("mod:part", 2, "tag-b")));
        assertFalse(material.matches(item("mod:part", 3, "tag-a")));
        material.match = MaterialsWidget.Match.EXACT;
        assertFalse(material.matches(item("mod:part", 2, "tag-b")));
        assertTrue(material.matches(item("mod:part", 2, "tag-a")));
        material.match = MaterialsWidget.Match.ITEM;
        assertTrue(material.matches(item("mod:part", 3, "tag-b")));
        assertFalse(material.matches(item("other:part", 2, "tag-a")));
        material.alternatives.add(item("other:part", 32767, ""));
        material.match = MaterialsWidget.Match.ITEM_META;
        assertTrue(material.matches(item("other:part", 41, "any")));
        material.match = MaterialsWidget.Match.ORE;
        assertFalse(material.matches(item("mod:part", 2, "tag-a", "plateIron")));
        material.ore = "plateIron";
        assertTrue(material.matches(item("third:iron", 7, "", "plateIron")));
        assertFalse(material.matches(item("third:copper", 7, "", "plateCopper")));
    }

    @Test public void allocationReroutesBroadMatchesToPreserveExactAndMainOnlyRequirements() {
        TaskBody body = new TaskBody();
        MaterialsWidget broad = new MaterialsWidget(), narrow = new MaterialsWidget();
        body.widgets.add(broad); body.widgets.add(narrow);
        ItemSpec exact = item("mod:part", 0, "exact"), replacement = item("mod:part", 1, "other");
        Material any = require(broad, exact, 64); any.match = MaterialsWidget.Match.ITEM;
        Material specific = require(narrow, exact, 64); specific.match = MaterialsWidget.Match.EXACT; specific.includeBags = false;
        InventorySnapshot inventory = new InventorySnapshot();
        inventory.entries.add(new InventorySnapshot.Entry(exact, 64, false));
        inventory.entries.add(new InventorySnapshot.Entry(replacement, 64, true));
        Map<Material, Long> allocation = inventory.allocate(body);
        assertEquals(Long.valueOf(64), allocation.get(any));
        assertEquals(Long.valueOf(64), allocation.get(specific));
        assertEquals(2, body.progress(inventory).done);
    }

    @Test public void inventoryIsSharedAcrossWidgetsAndManualChecksDoNotReserveItems() {
        TaskBody body = new TaskBody();
        MaterialsWidget first = new MaterialsWidget(), second = new MaterialsWidget();
        body.widgets.add(first); body.widgets.add(second);
        ItemSpec iron = item("mod:iron", 0, "");
        Material a = require(first, iron, 64), b = require(second, iron, 64);
        InventorySnapshot inventory = new InventorySnapshot();
        inventory.entries.add(new InventorySnapshot.Entry(iron, 64, false));
        Map<Material, Long> allocation = inventory.allocate(body);
        assertEquals(64L, allocation.get(a) + allocation.get(b));
        assertEquals(1, body.progress(inventory).done);
        a.automatic = false; a.checked = true;
        allocation = inventory.allocate(body);
        assertEquals(Long.valueOf(0), allocation.get(a));
        assertEquals(Long.valueOf(64), allocation.get(b));
        assertEquals(2, body.progress(inventory).done);
        inventory.entries.clear();
        assertEquals(1, body.progress(inventory).done);
    }

    @Test public void scopeExcludesBagsAndCountsUseLongs() {
        TaskBody body = new TaskBody(); MaterialsWidget widget = new MaterialsWidget(); body.widgets.add(widget);
        ItemSpec iron = item("mod:iron", 0, "");
        Material material = require(widget, iron, 4000000000L); material.includeBags = false;
        InventorySnapshot inventory = new InventorySnapshot();
        inventory.entries.add(new InventorySnapshot.Entry(iron, 3000000000L, false));
        inventory.entries.add(new InventorySnapshot.Entry(iron, 1000000000L, true));
        assertEquals(Long.valueOf(3000000000L), inventory.allocate(body).get(material));
        assertEquals(0, body.progress(inventory).done);
        material.includeBags = true;
        assertEquals(Long.valueOf(4000000000L), inventory.allocate(body).get(material));
        assertEquals(1, body.progress(inventory).done);
    }

    @Test public void copiesAreDetachedAndRepeatedWidgetTypesKeepTheirOrderAndIdentity() {
        TaskBody body = new TaskBody();
        ChecklistWidget one = new ChecklistWidget(), two = new ChecklistWidget();
        one.steps.add(new ChecklistWidget.Step("Wire it")); two.steps.add(new ChecklistWidget.Step("Test it"));
        MaterialsWidget materials = new MaterialsWidget();
        require(materials, item("mod:part", 0, ""), 8);
        body.widgets.add(one); body.widgets.add(two); body.widgets.add(materials);
        TaskBody copy = body.copy();
        ChecklistWidget copied = (ChecklistWidget) copy.widgets.get(0);
        assertEquals(one.id, copied.id); assertNotSame(one, copied);
        copied.steps.get(0).checked = true;
        ((MaterialsWidget) copy.widgets.get(2)).materials.get(0).alternatives.clear();
        assertFalse(one.steps.get(0).checked);
        assertEquals(1, materials.materials.get(0).alternatives.size());
        copy.move(copied, -1); assertSame(copied, copy.widgets.get(0));
        copy.move(copied, 1); assertSame(copied, copy.widgets.get(1));
        assertSame(one, body.widgets.get(0));
    }

    @Test public void customWidgetsParticipateWithoutChangingBodyDispatch() {
        TaskBody body = new TaskBody(); body.widgets.add(new Counter("counter", 2));
        TaskBody copy = body.copy();
        assertTrue(copy.widgets.get(0) instanceof Counter);
        assertEquals("counter", copy.widgets.get(0).id);
        assertEquals(2, copy.progress(new InventorySnapshot()).done);
        assertEquals(3, copy.progress(new InventorySnapshot()).total);
    }
    private static final class Counter extends TaskWidget {
        final int done;
        Counter(String id, int done) { super(id, "Custom"); this.done = done; }
        public TaskWidget copy() { return new Counter(id, done); }
        public TaskBody.Progress progress(TaskBody body, InventorySnapshot inventory) { return new TaskBody.Progress(done, 3); }
    }
}
