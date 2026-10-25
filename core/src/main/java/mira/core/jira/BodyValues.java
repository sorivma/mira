package mira.core.jira;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import mira.core.taskbody.*;

/** Canonical, bounded block values; one database field per independently editable block. */
public final class BodyValues {
    private BodyValues() {}
    public static TaskWidget withNewIdentity(TaskWidget source) {
        String id = Model.id();
        if (source instanceof NoteWidget) { NoteWidget n = new NoteWidget(id, source.title); n.text = ((NoteWidget) source).text; return n; }
        if (source instanceof ChecklistWidget) {
            ChecklistWidget c = new ChecklistWidget(id, source.title);
            for (ChecklistWidget.Step old : ((ChecklistWidget) source).steps) { ChecklistWidget.Step step = new ChecklistWidget.Step(old.text); step.checked = old.checked; c.steps.add(step); } return c;
        }
        if (source instanceof MaterialsWidget) { MaterialsWidget m = new MaterialsWidget(id, source.title); for (MaterialsWidget.Material item : ((MaterialsWidget) source).materials) m.materials.add(item.copy()); return m; }
        if (source instanceof LocationWidget) { LocationWidget old = (LocationWidget) source, l = new LocationWidget(id, source.title); l.dimension = old.dimension; l.x = old.x; l.y = old.y; l.z = old.z; return l; }
        if (source instanceof LinksWidget) { LinksWidget l = new LinksWidget(id, source.title); l.blockers.addAll(((LinksWidget) source).blockers); return l; }
        throw new Model.Rejected("Unsupported task block");
    }
    public static String encode(TaskWidget widget) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(new OutputStream() {
                @Override public void write(int value) throws IOException { if (bytes.size() >= 786432) throw new IOException("Block too large"); bytes.write(value); }
                @Override public void write(byte[] value, int offset, int length) throws IOException { if (bytes.size() > 786432 - length) throw new IOException("Block too large"); bytes.write(value, offset, length); }
            });
            out.writeInt(1); string(out, widget.id); string(out, widget.title);
            if (widget instanceof NoteWidget) { out.writeByte(0); string(out, ((NoteWidget) widget).text); }
            else if (widget instanceof ChecklistWidget) {
                out.writeByte(1); ChecklistWidget w = (ChecklistWidget) widget; out.writeInt(w.steps.size());
                for (ChecklistWidget.Step step : w.steps) { string(out, step.text); out.writeBoolean(step.checked); }
            } else if (widget instanceof MaterialsWidget) {
                out.writeByte(2); MaterialsWidget w = (MaterialsWidget) widget; out.writeInt(w.materials.size());
                for (MaterialsWidget.Material m : w.materials) {
                    out.writeLong(m.required); string(out, m.match.name()); string(out, m.ore);
                    out.writeBoolean(m.includeBags); out.writeBoolean(m.automatic); out.writeBoolean(m.checked);
                    out.writeInt(m.alternatives.size());
                    for (ItemSpec item : m.alternatives) {
                        string(out, item.item); out.writeInt(item.damage); string(out, item.tagHash);
                        string(out, item.displayName); string(out, item.tag); out.writeInt(item.ores.size());
                        for (String ore : item.ores) string(out, ore);
                    }
                }
            } else if (widget instanceof LocationWidget) {
                out.writeByte(3); LocationWidget w = (LocationWidget) widget;
                out.writeInt(w.dimension); out.writeInt(w.x); out.writeInt(w.y); out.writeInt(w.z);
            } else if (widget instanceof LinksWidget) {
                out.writeByte(4); List<String> blockers = ((LinksWidget) widget).blockers; out.writeInt(blockers.size());
                for (String blocker : blockers) string(out, blocker);
            } else throw new Model.Rejected("Unsupported task block");
            out.flush();
            String result = Base64.getEncoder().encodeToString(bytes.toByteArray());
            decode(result); return result;
        } catch (IOException e) { throw new Model.Rejected("Cannot encode task block"); }
    }
    public static TaskWidget decode(String value) {
        try {
            if (value.length() > 1048576) throw new IOException("Block too large");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)));
            if (in.readInt() != 1) throw new IOException("Unsupported block format");
            String id = string(in), title = string(in); Rules.uuid(id); Rules.text(title, 0, 160);
            TaskWidget widget;
            switch (in.readUnsignedByte()) {
                case 0:
                    NoteWidget note = new NoteWidget(id, title); note.text = string(in); widget = note; break;
                case 1:
                    ChecklistWidget checklist = new ChecklistWidget(id, title);
                    for (int i = count(in, 512); i > 0; i--) {
                        ChecklistWidget.Step step = new ChecklistWidget.Step(string(in)); step.checked = in.readBoolean(); checklist.steps.add(step);
                    }
                    widget = checklist; break;
                case 2:
                    MaterialsWidget materials = new MaterialsWidget(id, title);
                    for (int i = count(in, 256); i > 0; i--) {
                        long required = in.readLong(); if (required < 1 || required > 1000000000000L) throw new IOException("Invalid material quantity");
                        MaterialsWidget.Match match = MaterialsWidget.Match.valueOf(string(in)); String ore = string(in);
                        boolean bags = in.readBoolean(), automatic = in.readBoolean(), checked = in.readBoolean();
                        List<ItemSpec> alternatives = new ArrayList<>();
                        for (int j = count(in, 64); j > 0; j--) {
                            String item = string(in); int damage = in.readInt(); String hash = string(in), name = string(in), tag = string(in);
                            List<String> ores = new ArrayList<>(); for (int k = count(in, 128); k > 0; k--) ores.add(string(in));
                            if (!item.matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_./-]+")) throw new IOException("Invalid item identity");
                            alternatives.add(new ItemSpec(item, damage, hash, ores, name, tag));
                        }
                        if (alternatives.isEmpty()) throw new IOException("Material needs an item");
                        MaterialsWidget.Material m = new MaterialsWidget.Material(alternatives.get(0)); m.alternatives.clear(); m.alternatives.addAll(alternatives);
                        m.required = required; m.match = match; m.ore = ore; m.includeBags = bags; m.automatic = automatic; m.checked = checked;
                        materials.materials.add(m);
                    }
                    widget = materials; break;
                case 3:
                    LocationWidget location = new LocationWidget(id, title); location.dimension = in.readInt();
                    location.x = in.readInt(); location.y = in.readInt(); location.z = in.readInt(); widget = location; break;
                case 4:
                    LinksWidget links = new LinksWidget(id, title);
                    for (int i = count(in, 256); i > 0; i--) { String key = string(in); Rules.text(key, 1, 80); links.blockers.add(key); }
                    widget = links; break;
                default: throw new IOException("Unknown task block");
            }
            if (in.read() != -1) throw new IOException("Trailing block data"); return widget;
        } catch (IOException | IllegalArgumentException e) { throw new Model.Rejected("Invalid task block: " + e.getMessage()); }
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int n = in.readInt(); if (n < 0 || n > max) throw new IOException("Block entry limit"); return n;
    }
    private static void string(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8); if (bytes.length > 262144) throw new IOException("Text too long");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String string(DataInputStream in) throws IOException {
        byte[] bytes = new byte[count(in, 262144)]; in.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8);
    }
}
