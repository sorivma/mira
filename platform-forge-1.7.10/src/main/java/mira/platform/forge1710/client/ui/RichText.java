package mira.platform.forge1710.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Font-independent styled text layout, shared by Markdown and native UI rendering. */
public final class RichText {
    public static final int BOLD = 1, ITALIC = 2, STRIKE = 4, CODE = 8;
    public static final int LINE_HEIGHT = 12;

    public static final class Span {
        public final String text, link;
        public final int style, color;
        public Span(String text, int style, int color, String link) {
            this.text = text; this.style = style; this.color = color; this.link = link;
        }
        String formatted(String value) {
            return ((style & BOLD) != 0 ? "\u00a7l" : "") + ((style & ITALIC) != 0 ? "\u00a7o" : "")
                + ((style & STRIKE) != 0 ? "\u00a7m" : "") + (link != null ? "\u00a7n" : "") + value;
        }
    }
    public static final class Run {
        public final Span span;
        public final String text;
        public final int x, y, width;
        Run(Span span, String text, int x, int y, int width) {
            this.span = span; this.text = text; this.x = x; this.y = y; this.width = width;
        }
    }
    public static final class Layout {
        public final List<Run> runs = new ArrayList<>();
        public int height = LINE_HEIGHT;
    }
    private RichText() {}

    public static void align(Layout layout, int width, int alignment) {
        for (int first = 0; first < layout.runs.size();) {
            int end = first;
            while (end < layout.runs.size() && layout.runs.get(end).y == layout.runs.get(first).y) end++;
            Run last = layout.runs.get(end - 1);
            int shift = Math.max(0, width - last.x - last.width) / (alignment == 1 ? 2 : 1);
            if (alignment != 0) for (int i = first; i < end; i++) {
                Run run = layout.runs.get(i);
                layout.runs.set(i, new Run(run.span, run.text, run.x + shift, run.y, run.width));
            }
            first = end;
        }
    }

    public static Layout layout(List<Span> spans, int width, ToIntFunction<String> measure) {
        Layout result = new Layout();
        int x = 0, y = 0;
        width = Math.max(1, width);
        for (Span span : spans) {
            int position = 0;
            while (position < span.text.length()) {
                char character = span.text.charAt(position);
                if (character == '\n') { x = 0; y += LINE_HEIGHT; position++; continue; }
                int end = position + Character.charCount(span.text.codePointAt(position));
                if (!Character.isWhitespace(character)) {
                    while (end < span.text.length() && !Character.isWhitespace(span.text.charAt(end)))
                        end += Character.charCount(span.text.codePointAt(end));
                }
                String word = span.text.substring(position, end);
                int wordWidth = measure.applyAsInt(span.formatted(word));
                if (x > 0 && x + wordWidth > width && !Character.isWhitespace(character)) { x = 0; y += LINE_HEIGHT; }
                if (Character.isWhitespace(character) && x == 0 && (span.style & CODE) == 0) { position = end; continue; }
                if (wordWidth <= width - x) {
                    result.runs.add(new Run(span, word, x, y, wordWidth)); x += wordWidth;
                } else {
                    for (int offset = 0; offset < word.length();) {
                        int next = offset + Character.charCount(word.codePointAt(offset));
                        String glyph = word.substring(offset, next);
                        int glyphWidth = measure.applyAsInt(span.formatted(glyph));
                        if (x > 0 && x + glyphWidth > width) { x = 0; y += LINE_HEIGHT; }
                        result.runs.add(new Run(span, glyph, x, y, glyphWidth)); x += glyphWidth; offset = next;
                    }
                }
                position = end;
            }
        }
        result.height = y + LINE_HEIGHT;
        return result;
    }
}
