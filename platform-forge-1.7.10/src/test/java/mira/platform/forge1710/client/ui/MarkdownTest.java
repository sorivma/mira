package mira.platform.forge1710.client.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarkdownTest {
    @Test public void parsesNestedFormattingReferencesAndEscapes() {
        Node document = Markdown.parse("# Heading\n\n**bold *nested***, ~~old~~, `literal *code*`, \\*escaped\\* &amp; [task][task].\n\n[task]: BASE-1\n");
        assertEquals(1, nodes(document, Heading.class).size());
        assertEquals(1, nodes(document, StrongEmphasis.class).size());
        assertEquals(1, nodes(document, Emphasis.class).size());
        assertEquals(1, nodes(document, Strikethrough.class).size());
        assertEquals("literal *code*", nodes(document, Code.class).get(0).getLiteral());
        assertEquals("BASE-1", nodes(document, Link.class).get(0).getDestination());
        boolean escaped = false;
        for (Text text : nodes(document, Text.class)) if (text.getLiteral().contains("*escaped* &")) escaped = true;
        assertTrue(escaped);
    }
    @Test public void parsesTablesTasksNestedListsImagesAndLiteralCode() {
        Node document = Markdown.parse(
            "| Item | Amount |\n| :--- | ---: |\n| Iron | 64 |\n\n- [x] Ready\n- [ ] Build\n  1. Nested\n\n"
            + "> Quote\n\n```java\n**literal**\n```\n\n![Layout](https://example.com/layout.png)\n\nhttps://example.com\n");
        assertEquals(1, nodes(document, TableBlock.class).size());
        List<TaskListItemMarker> tasks = nodes(document, TaskListItemMarker.class);
        assertEquals(2, tasks.size()); assertTrue(tasks.get(0).isChecked()); assertFalse(tasks.get(1).isChecked());
        assertEquals(1, nodes(document, OrderedList.class).size());
        assertEquals(1, nodes(document, BlockQuote.class).size());
        assertEquals("**literal**\n", nodes(document, FencedCodeBlock.class).get(0).getLiteral());
        assertEquals("https://example.com/layout.png", nodes(document, Image.class).get(0).getDestination());
        assertEquals("https://example.com", nodes(document, Link.class).get(0).getDestination());
    }
    @Test public void wrappingKeepsLinkDestinationsStylesAndLongWords() {
        String link = "https://example.com";
        RichText.Span span = new RichText.Span("LongLinkText next", RichText.BOLD | RichText.ITALIC, 0, link);
        RichText.Layout layout = RichText.layout(Arrays.asList(span), 5, MarkdownTest::width);
        assertTrue(layout.height > RichText.LINE_HEIGHT);
        StringBuilder rendered = new StringBuilder();
        for (RichText.Run run : layout.runs) {
            assertSame(span, run.span); assertEquals(link, run.span.link); assertTrue(run.x + run.width <= 5);
            rendered.append(run.text);
        }
        assertEquals("LongLinkTextnext", rendered.toString().replace(" ", ""));
    }
    @Test public void lineBreaksAndCodeIndentationSurviveLayout() {
        RichText.Layout layout = RichText.layout(Arrays.asList(new RichText.Span("  first\n\nlast", RichText.CODE, 0, null)), 30, MarkdownTest::width);
        assertEquals(RichText.LINE_HEIGHT * 3, layout.height);
        assertEquals(" ", layout.runs.get(0).text);
        RichText.align(layout, 30, 2);
        RichText.Run last = layout.runs.get(layout.runs.size() - 1);
        assertEquals(30, last.x + last.width);
    }
    private static int width(String text) { return text.replaceAll("\u00a7[0-9a-z]", "").length(); }
    private static <T extends Node> List<T> nodes(Node parent, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (type.isInstance(node)) result.add(type.cast(node));
            result.addAll(nodes(node, type));
        }
        return result;
    }
}
