package mira.core.jira;

import java.util.Arrays;
import mira.core.taskbody.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BodyValuesTest {
    @Test public void materialMatchingAndPortableItemDataSurviveRoundTrip() {
        MaterialsWidget widget = new MaterialsWidget(); widget.title = "Steel";
        MaterialsWidget.Material material = new MaterialsWidget.Material(new ItemSpec("gregtech:gt.metaitem.01", 23, "hash", Arrays.asList("ingotSteel"), "Steel ingot", "{display:{Name:Steel}}"));
        material.alternatives.add(new ItemSpec("minecraft:iron_ingot", 0, "", Arrays.asList("ingotIron"), "Iron", ""));
        material.required = 9000; material.match = MaterialsWidget.Match.ORE; material.ore = "ingotSteel"; material.includeBags = false; material.automatic = false; material.checked = true;
        widget.materials.add(material); String encoded = BodyValues.encode(widget); MaterialsWidget restored = (MaterialsWidget) BodyValues.decode(encoded);
        assertEquals(widget.id, restored.id); assertEquals(encoded, BodyValues.encode(restored));
        MaterialsWidget.Material copy = restored.materials.get(0); assertEquals(9000, copy.required); assertEquals(2, copy.alternatives.size());
        assertEquals(material.alternatives.get(0).tag, copy.alternatives.get(0).tag); assertFalse(copy.includeBags); assertTrue(copy.checked);
    }
    @Test public void orderedBlockValuesPreserveTextChecksLocationsAndLinks() {
        NoteWidget note = new NoteWidget(); note.text = "  first\n\nsecond  \n";
        ChecklistWidget checklist = new ChecklistWidget(); ChecklistWidget.Step step = new ChecklistWidget.Step("Build"); step.checked = true; checklist.steps.add(step);
        LocationWidget location = new LocationWidget(); location.dimension = -1; location.x = -120; location.y = 64; location.z = 300;
        LinksWidget links = new LinksWidget(); links.blockers.add("MAIN-1"); links.blockers.add("POWER-7");
        for (TaskWidget widget : Arrays.asList(note, checklist, location, links)) {
            String encoded = BodyValues.encode(widget); assertEquals(encoded, BodyValues.encode(BodyValues.decode(encoded)));
            TaskWidget detached = BodyValues.withNewIdentity(widget); assertNotEquals(widget.id, detached.id); assertEquals(widget.title, detached.title);
        }
    }
    @Test public void invalidAndOversizedBlocksFailCleanly() {
        try { BodyValues.decode("not a valid block"); fail("Expected rejection"); } catch (Model.Rejected expected) { /* rejected */ }
        NoteWidget note = new NoteWidget(); char[] text = new char[262145]; Arrays.fill(text, 'x'); note.text = new String(text);
        try { BodyValues.encode(note); fail("Expected text bound"); } catch (Model.Rejected expected) { /* rejected */ }
    }
}
