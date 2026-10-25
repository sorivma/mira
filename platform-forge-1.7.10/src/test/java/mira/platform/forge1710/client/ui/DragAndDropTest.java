package mira.platform.forge1710.client.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mira.platform.forge1710.client.ui.Ui.Box;
import org.junit.Test;

import static org.junit.Assert.*;

/** Gesture/callback behavior only: no rendering, native input or game instance. */
public class DragAndDropTest {
    private final DragAndDrop drag = new DragAndDrop();
    private final List<String> actions = new ArrayList<>();

    private void press() {
        Box bounds = new Box(10, 10, 100, 72);
        drag.press(Ui.column(0), "card", bounds, bounds, 20, 20, () -> actions.add("click"));
    }

    @Test
    public void shortPressClicksInsteadOfDropping() {
        press();
        drag.target(new Box(0, 0, 200, 200), value -> true, value -> actions.add("drop"));
        assertTrue(drag.release(22, 22));
        assertEquals(Arrays.asList("click"), actions);
        assertFalse(drag.pressed());
    }

    @Test
    public void dragReleasesIntoATargetWithoutOpeningTheCard() {
        press();
        drag.beginFrame(80, 30);
        assertTrue(drag.active());
        assertTrue(drag.target(new Box(70, 0, 100, 100), value -> true, value -> actions.add("drop:" + value)));
        assertTrue(drag.release(80, 30));
        assertEquals(Arrays.asList("drop:card"), actions);
        assertFalse(drag.active());
    }

    @Test
    public void releasingOutsideTargetsCancelsTheMove() {
        press();
        drag.beginFrame(250, 200);
        drag.target(new Box(50, 50, 100, 100), value -> true, value -> actions.add("drop"));
        assertFalse(drag.release(250, 200));
        assertTrue(actions.isEmpty());
        assertFalse(drag.pressed());
    }

    @Test
    public void aCardTargetTakesPriorityOverItsColumn() {
        press();
        drag.beginFrame(80, 80);
        drag.target(new Box(0, 0, 200, 200), value -> true, value -> actions.add("column"));
        drag.target(new Box(50, 50, 100, 72), value -> true, value -> actions.add("before card"));
        assertTrue(drag.release(80, 80));
        assertEquals(Arrays.asList("before card"), actions);
    }

    @Test
    public void cancellingNeverInvokesAClickOrDrop() {
        press();
        drag.move(80, 80);
        drag.target(new Box(0, 0, 200, 200), value -> true, value -> actions.add("drop"));
        assertTrue(drag.cancel());
        assertFalse(drag.release(80, 80));
        assertTrue(actions.isEmpty());
    }

    @Test
    public void aShortReleaseOutsideTheVisiblePartDoesNotClick() {
        drag.press(Ui.column(0), "card", new Box(0, 0, 100, 72), new Box(50, 0, 50, 72),
            51, 20, () -> actions.add("click"));
        assertFalse(drag.release(48, 20));
        assertTrue(actions.isEmpty());
    }

    @Test
    public void incompatibleTargetsDoNotReceiveThePayload() {
        press();
        drag.beginFrame(80, 80);
        drag.target(new Box(0, 0, 200, 200), value -> value instanceof Integer, value -> actions.add("drop"));
        assertFalse(drag.release(80, 80));
        assertTrue(actions.isEmpty());
    }

    @Test
    public void externalPayloadsUseNestedTargetsWithoutACardPress() {
        drag.target(new Box(0, 0, 200, 200), value -> value instanceof Integer, value -> actions.add("section"));
        drag.target(new Box(50, 50, 50, 20), value -> value instanceof Integer, value -> actions.add("alternative:" + value));
        assertFalse(drag.external("wrong type", 60, 60));
        assertFalse(drag.external(64, 250, 60));
        assertTrue(drag.external(64, 60, 60));
        assertEquals(Arrays.asList("alternative:64"), actions);
        assertFalse(drag.external(64, 60, 60)); // A mutation invalidates targets until the next frame.
        assertFalse(drag.pressed());
    }
}
