package mira.platform.forge1710.client.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.HashMap;
import java.util.function.Consumer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import static mira.platform.forge1710.client.ui.Ui.*;

/** CommonMark syntax translated to native fluent nodes, with GFM tables, tasks and strike-through. */
public final class Markdown {
    private static final Parser parser = Parser.builder().extensions(Arrays.asList(
        TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create())).build();
    private static final Map<String, org.commonmark.node.Node> documents = new LinkedHashMap<String, org.commonmark.node.Node>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, org.commonmark.node.Node> entry) { return size() > 64; }
    };
    public static final class View {
        public final Ui.Node node;
        public final int height;
        View(Ui.Node node, int height) { this.node = node.height(height); this.height = height; }
    }
    private final FontRenderer font;
    private final Consumer<String> openLink;
    private final Map<String, Integer> anchors = new HashMap<>();

    private Markdown(FontRenderer font, Consumer<String> openIssue) {
        this.font = font;
        openLink = destination -> MarkdownLinks.open(destination, openIssue);
    }
    public static org.commonmark.node.Node parse(String source) { return parser.parse(source); }
    public static View build(FontRenderer font, String source, int width, Consumer<String> openIssue) {
        org.commonmark.node.Node document = documents.computeIfAbsent(source, Markdown::parse);
        View view = new Markdown(font, openIssue).blocks(document, Math.max(1, width), 6);
        return new View(view.node, Math.max(12, view.height));
    }
    private View blocks(org.commonmark.node.Node parent, int width, int gap) {
        Ui.Node column = column(gap); int height = 0, count = 0;
        for (org.commonmark.node.Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof LinkReferenceDefinition || child instanceof TaskListItemMarker) continue;
            View view = block(child, width);
            column.add(view.node); height += view.height; count++;
        }
        return new View(column, Math.max(0, height + Math.max(0, count - 1) * gap));
    }
    private View block(org.commonmark.node.Node node, int width) {
        if (node instanceof Paragraph) return inline(node, width, 0);
        if (node instanceof Heading) {
            View content = inline(node, width, RichText.BOLD);
            boolean underline = ((Heading) node).getLevel() <= 2;
            String slug = plain(node).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_ -]", "").trim().replaceAll(" +", "-");
            int duplicate = anchors.getOrDefault(slug, 0); anchors.put(slug, duplicate + 1);
            return new View(column(3).anchor(slug + (duplicate == 0 ? "" : "-" + duplicate))
                .add(content.node, underline ? rule() : space().height(1)), content.height + (underline ? 5 : 4));
        }
        if (node instanceof ThematicBreak) return new View(rule(), 2);
        if (node instanceof BlockQuote) {
            View content = blocks(node, Math.max(1, width - 10), 5);
            return new View(row(4).add(column(0).width(2).background(0xFF8B8B8B), space().width(0), content.node), content.height);
        }
        if (node instanceof FencedCodeBlock) {
            FencedCodeBlock code = (FencedCodeBlock) node;
            return code(code.getLiteral(), code.getInfo(), width);
        }
        if (node instanceof IndentedCodeBlock) return code(((IndentedCodeBlock) node).getLiteral(), "Code", width);
        if (node instanceof HtmlBlock) return code(((HtmlBlock) node).getLiteral(), "HTML", width);
        if (node instanceof BulletList || node instanceof OrderedList) {
            Ui.Node list = column(4); int height = 0, count = 0;
            int number = node instanceof OrderedList ? ((OrderedList) node).getStartNumber() : 0;
            for (org.commonmark.node.Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                String marker = node instanceof OrderedList ? Integer.toString(number++) + "." : "-";
                if (item.getFirstChild() instanceof TaskListItemMarker)
                    marker = ((TaskListItemMarker) item.getFirstChild()).isChecked() ? "[x]" : "[ ]";
                int markerWidth = Math.max(12, font.getStringWidth(marker) + 4);
                View content = blocks(item, Math.max(1, width - markerWidth - 3), 4);
                int rowHeight = Math.max(12, content.height);
                list.add(row(3).height(rowHeight).add(text(marker).height(12).width(markerWidth), content.node));
                height += rowHeight; count++;
            }
            return new View(list, height + Math.max(0, count - 1) * 4);
        }
        if (node instanceof TableBlock) return table(node, width);
        return blocks(node, width, 4);
    }
    private View code(String literal, String language, int width) {
        if (literal.endsWith("\n")) literal = literal.substring(0, literal.length() - 1);
        String value = literal;
        RichText.Layout layout = RichText.layout(Collections.singletonList(new RichText.Span(value.replace("\t", "    "), RichText.CODE, 0xFFE0E0E0, null)),
            Math.max(1, width - 12), font::getStringWidth);
        String label = language == null || language.isEmpty() ? "Code" : language;
        Ui.Node code = column(5).pad(6).background(0xFF303030).add(
            row(4).height(20).add(text(label, 0xFFCCCCCC), button("Copy", () -> GuiScreen.setClipboardString(value)).width(40)),
            Ui.richText(layout, openLink));
        return new View(code, layout.height + 37);
    }
    private View inline(org.commonmark.node.Node node, int width, int style) {
        List<Part> parts = new ArrayList<>();
        append(node.getFirstChild(), style, null, parts);
        Ui.Node content = column(3); int height = 0, count = 0;
        List<RichText.Span> text = new ArrayList<>();
        for (Part part : parts) {
            if (part.image == null) { text.add(part.span); continue; }
            if (!text.isEmpty()) { View run = styled(text, width); content.add(run.node); height += run.height; count++; text.clear(); }
            Ui.Node image = Ui.markdownImage(part.image, part.alt, width,
                destination -> openLink.accept(part.destination == null ? destination : part.destination));
            // Image node owns its intrinsic height, which may change once a download completes.
            int imageHeight = imageHeight(part.image, width);
            content.add(image); height += imageHeight; count++;
        }
        if (!text.isEmpty()) { View run = styled(text, width); content.add(run.node); height += run.height; count++; }
        return new View(content, Math.max(12, height + Math.max(0, count - 1) * 3));
    }
    private int imageHeight(String source, int width) {
        MarkdownImages.Image image = MarkdownImages.get(source);
        if (image.texture == null) return 24;
        return Math.min(160, Math.max(1, Math.min(width, image.width) * image.height / image.width)) + 15;
    }
    private View styled(List<RichText.Span> spans, int width) {
        RichText.Layout layout = RichText.layout(spans, width, font::getStringWidth);
        return new View(Ui.richText(layout, openLink), layout.height);
    }
    private void append(org.commonmark.node.Node first, int style, String link, List<Part> parts) {
        for (org.commonmark.node.Node node = first; node != null; node = node.getNext()) {
            if (node instanceof Text) parts.add(new Part(new RichText.Span(((Text) node).getLiteral(), style, TEXT, link)));
            else if (node instanceof Code) parts.add(new Part(new RichText.Span(((Code) node).getLiteral(), style | RichText.CODE, TEXT, link)));
            else if (node instanceof HardLineBreak) parts.add(new Part(new RichText.Span("\n", style, TEXT, link)));
            else if (node instanceof SoftLineBreak) parts.add(new Part(new RichText.Span(" ", style, TEXT, link)));
            else if (node instanceof HtmlInline) parts.add(new Part(new RichText.Span(((HtmlInline) node).getLiteral(), style | RichText.CODE, TEXT, link)));
            else if (node instanceof TaskListItemMarker) parts.add(new Part(new RichText.Span(((TaskListItemMarker) node).isChecked() ? "[x] " : "[ ] ", style, TEXT, link)));
            else if (node instanceof Image) parts.add(new Part(((Image) node).getDestination(), plain(node), link));
            else {
                int nested = style;
                if (node instanceof StrongEmphasis) nested |= RichText.BOLD;
                if (node instanceof Emphasis) nested |= RichText.ITALIC;
                if (node instanceof Strikethrough) nested |= RichText.STRIKE;
                append(node.getFirstChild(), nested, node instanceof Link ? ((Link) node).getDestination() : link, parts);
            }
        }
    }
    private String plain(org.commonmark.node.Node node) {
        StringBuilder text = new StringBuilder();
        for (org.commonmark.node.Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text) text.append(((Text) child).getLiteral());
            else if (child instanceof Code) text.append(((Code) child).getLiteral());
            else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) text.append(' ');
            else text.append(plain(child));
        }
        return text.toString();
    }
    private View table(org.commonmark.node.Node node, int width) {
        List<TableRow> rows = new ArrayList<>(); collectRows(node, rows);
        int columns = 0;
        for (TableRow tableRow : rows) { int count = 0; for (org.commonmark.node.Node cell = tableRow.getFirstChild(); cell != null; cell = cell.getNext()) count++; columns = Math.max(columns, count); }
        if (columns == 0) return new View(space(), 0);
        boolean stacked = width / columns < 64;
        Ui.Node table = column(1); int height = 0;
        List<String> headers = new ArrayList<>();
        for (TableRow tableRow : rows) {
            Ui.Node line = stacked ? column(3) : row(1); int rowHeight = 0, index = 0;
            for (org.commonmark.node.Node cell = tableRow.getFirstChild(); cell != null; cell = cell.getNext()) {
                TableCell value = (TableCell) cell;
                if (value.isHeader()) headers.add(plain(cell));
                int cellWidth = stacked ? width : (width - columns + 1) / columns;
                List<Part> parts = new ArrayList<>(); append(cell.getFirstChild(), value.isHeader() ? RichText.BOLD : 0, null, parts);
                List<RichText.Span> spans = new ArrayList<>();
                if (stacked && !value.isHeader() && index < headers.size()) spans.add(new RichText.Span(headers.get(index) + ": ", RichText.BOLD, TEXT, null));
                for (Part part : parts) spans.add(part.image == null ? part.span : new RichText.Span(part.alt, 0, TEXT,
                    part.destination == null ? part.image : part.destination));
                RichText.Layout layout = RichText.layout(spans, Math.max(1, cellWidth - 8), font::getStringWidth);
                if (!stacked && value.getAlignment() != null) RichText.align(layout, Math.max(1, cellWidth - 8),
                    value.getAlignment() == TableCell.Alignment.RIGHT ? 2 : value.getAlignment() == TableCell.Alignment.CENTER ? 1 : 0);
                View content = new View(Ui.richText(layout, openLink), layout.height);
                boolean containsImage = false;
                for (Part part : parts) if (part.image != null) containsImage = true;
                if (containsImage) {
                    content = inline(cell, Math.max(1, cellWidth - 8), value.isHeader() ? RichText.BOLD : 0);
                    if (stacked && !value.isHeader() && index < headers.size()) content = new View(column(3).add(
                        heading(headers.get(index)).height(12), content.node), content.height + 15);
                }
                Ui.Node cellNode = column(0).pad(4).add(content.node).background(value.isHeader() ? 0xFFB0B0B0 : 0x22777777);
                if (stacked) { cellNode.height(content.height + 8); rowHeight += content.height + 8 + (index > 0 ? 3 : 0); }
                else { cellNode.width(cellWidth); rowHeight = Math.max(rowHeight, content.height + 8); }
                line.add(cellNode); index++;
            }
            line.height(rowHeight); table.add(line); height += rowHeight + 1;
        }
        return new View(table, Math.max(0, height - 1));
    }
    private void collectRows(org.commonmark.node.Node node, List<TableRow> rows) {
        for (org.commonmark.node.Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableRow) rows.add((TableRow) child); else collectRows(child, rows);
        }
    }
    private static final class Part {
        final RichText.Span span;
        final String image, alt, destination;
        Part(RichText.Span span) { this.span = span; image = alt = destination = null; }
        Part(String image, String alt, String destination) { span = null; this.image = image; this.alt = alt; this.destination = destination; }
    }
}
