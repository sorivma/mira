package mira.platform.forge1710.client.ui;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.gui.FontRenderer;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Replays SDL key + committed-text pairs through real Mira controls, without a game or native input. */
public class TextInputEventsTest {
    private FontRenderer font() {
        FontRenderer font = mock(FontRenderer.class);
        when(font.getStringWidth(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length());
        when(font.trimStringToWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    @Test public void singleLineInsertsEachSdlCommitOnceIncludingIntentionalRepeatedLetters() {
        AtomicReference<String> value = new AtomicReference<>("");
        TextInput input = new TextInput(font(), 100, "", value::get, value::set, true);
        input.focus(true);
        for (char c : "aaЯ".toCharArray()) {
            input.type(c, Keyboard.KEY_A); // physical press/repeat, carries a naive character
            input.type(c, Keyboard.KEY_NONE); // SDL committed text for the same press
        }
        assertEquals("aaЯ", value.get());
    }

    @Test public void multilineInsertsEachSdlCommitOnce() {
        AtomicReference<String> value = new AtomicReference<>("");
        TextArea input = new TextArea(font(), 100, 3, "", value::get, value::set, true);
        input.focus(true);
        for (char c : "aaЯ".toCharArray()) {
            input.type(c, Keyboard.KEY_A, false, false);
            input.type(c, Keyboard.KEY_NONE, false, false);
        }
        assertEquals("aaЯ", value.get());
    }

    @Test public void legacyCombinedEventsStillInsertEveryCharacter() {
        AtomicReference<String> value = new AtomicReference<>("");
        TextInput input = new TextInput(font(), 100, "", value::get, value::set, false);
        input.focus(true);
        for (char c : "aaЯ".toCharArray()) input.type(c, Keyboard.KEY_A);
        assertEquals("aaЯ", value.get());
    }

    @Test public void physicalTextDoesNotCollapseMultilineSelectionBeforeTheCommit() {
        AtomicReference<String> value = new AtomicReference<>("old");
        TextArea input = new TextArea(font(), 100, 3, "", value::get, value::set, true);
        input.focus(true);
        input.synchronize();
        input.type((char) 1, Keyboard.KEY_A, true, false);
        input.type('Я', Keyboard.KEY_A, false, false);
        assertEquals("old", value.get());
        input.type('Я', Keyboard.KEY_NONE, false, false);
        assertEquals("Я", value.get());
        input.type('\b', Keyboard.KEY_BACK, false, false);
        assertEquals("", value.get());
    }

    @Test public void slashActionRunsOnceFromCommittedText() {
        AtomicReference<String> value = new AtomicReference<>("");
        TextArea input = new TextArea(font(), 100, 3, "", value::get, value::set, true);
        int[] opens = {0};
        input.focus(true);
        input.onEmptySlash(() -> opens[0]++);
        input.type('/', Keyboard.KEY_SLASH, false, false);
        assertEquals(0, opens[0]);
        input.type('/', Keyboard.KEY_NONE, false, false);
        assertEquals(1, opens[0]);
        assertEquals("", value.get());
        assertEquals("", input.field.getText());
    }

    @Test public void legacyMultilineInputAndEnterStillWork() {
        AtomicReference<String> value = new AtomicReference<>("");
        TextArea input = new TextArea(font(), 100, 3, "", value::get, value::set, false);
        input.focus(true);
        input.type('a', Keyboard.KEY_A, false, false);
        input.type('\r', Keyboard.KEY_RETURN, false, false);
        input.type('Я', Keyboard.KEY_A, false, false);
        assertEquals("a\nЯ", value.get());
    }
}
