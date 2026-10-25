package mira.platform.forge1710.jira;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import mira.core.jira.Model.*;

/** Explicit versioned wire/storage format. Never uses Java object deserialization. */
public final class DatabaseCodec {
    public static final int LIMIT = 64 * 1024 * 1024;
    private static final int MAGIC = 0x4d495241, VERSION = 1;
    private DatabaseCodec() {}
    public static byte[] state(State state) throws IOException {
        if (state.entities.size() > 100000 || state.players.size() > 100000 || state.activity.size() > 1000000) throw new IOException("Database entry limit");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = output(bytes, LIMIT);
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(state.revision);
        strings(out, state.players); out.writeInt(state.entities.size());
        for (Entity e : state.entities.values()) {
            text(out, e.id); text(out, e.kind.name()); text(out, e.workspace); strings(out, e.fields);
            out.writeInt(e.versions.size()); for (Map.Entry<String, Long> v : e.versions.entrySet()) { text(out, v.getKey()); out.writeLong(v.getValue()); }
        }
        out.writeInt(state.activity.size());
        for (Activity a : state.activity) {
            if (a.differences.size() > 100000) throw new IOException("Activity entry limit");
            text(out, a.id); text(out, a.workspace); text(out, a.actor); text(out, a.name); text(out, a.summary); text(out, a.undoneBy);
            out.writeLong(a.time); out.writeLong(a.revision); out.writeBoolean(a.undoable); out.writeInt(a.differences.size());
            for (Difference d : a.differences) {
                text(out, d.entity); text(out, d.field); nullable(out, d.before); nullable(out, d.after); out.writeLong(d.version); out.writeBoolean(d.created);
            }
        }
        out.flush(); if (bytes.size() > LIMIT) throw new IOException("World database exceeds 64 MiB"); return bytes.toByteArray();
    }
    public static State state(byte[] bytes) throws IOException {
        DataInputStream in = input(bytes, LIMIT); State state = new State();
        if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported Mira database format");
        state.revision = in.readLong(); if (state.revision < 0) throw new IOException("Invalid revision");
        strings(in, state.players, 100000);
        for (int n = count(in, 100000); n > 0; n--) {
            String id = text(in); Kind kind = enumeration(Kind.class, text(in)); Entity e = new Entity(id, kind, text(in));
            strings(in, e.fields, 32);
            for (int j = count(in, 32); j > 0; j--) { String key = text(in); long v = in.readLong(); if (v < 0 || v > state.revision || e.versions.put(key, v) != null) throw new IOException("Invalid field revision"); }
            if (state.entities.put(id, e) != null) throw new IOException("Duplicate entity");
        }
        for (int n = count(in, 1000000); n > 0; n--) {
            Activity a = new Activity(); a.id = text(in); a.workspace = text(in); a.actor = text(in); a.name = text(in); a.summary = text(in); a.undoneBy = text(in);
            a.time = in.readLong(); a.revision = in.readLong(); a.undoable = in.readBoolean();
            for (int j = count(in, 100000); j > 0; j--) {
                Difference d = new Difference(); d.entity = text(in); d.field = text(in); d.before = nullable(in); d.after = nullable(in); d.version = in.readLong(); d.created = in.readBoolean(); a.differences.add(d);
            }
            state.activity.add(a);
        }
        end(in); return state;
    }
    public static byte[] command(Command command) throws IOException {
        if (command.patches.size() > 512 || command.arguments.size() > 1024) throw new IOException("Edit exceeds 512 entities or 1024 arguments");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = output(bytes, 4 * 1024 * 1024);
        out.writeInt(VERSION); text(out, command.id); text(out, command.workspace); text(out, command.target); text(out, command.operation.name()); strings(out, command.arguments);
        out.writeInt(command.patches.size());
        for (Patch p : command.patches) {
            if (p.values.size() > 32 || p.expected.size() > 32) throw new IOException("Edit field limit");
            text(out, p.id); text(out, p.kind.name()); text(out, p.workspace); strings(out, p.values);
            out.writeInt(p.expected.size()); for (Map.Entry<String, Long> v : p.expected.entrySet()) { text(out, v.getKey()); out.writeLong(v.getValue()); }
        }
        out.flush(); if (bytes.size() > 4 * 1024 * 1024) throw new IOException("Edit exceeds 4 MiB"); return bytes.toByteArray();
    }
    public static Command command(byte[] bytes) throws IOException {
        DataInputStream in = input(bytes, 4 * 1024 * 1024);
        if (in.readInt() != VERSION) throw new IOException("Incompatible Mira protocol");
        Command c = new Command(); c.id = text(in); c.workspace = text(in); c.target = text(in); c.operation = enumeration(Operation.class, text(in)); strings(in, c.arguments, 1024);
        for (int i = count(in, 512); i > 0; i--) {
            String id = text(in); Kind kind = enumeration(Kind.class, text(in)); Patch p = new Patch(id, kind, text(in)); strings(in, p.values, 32);
            for (int j = count(in, 32); j > 0; j--) { String key = text(in); if (p.expected.put(key, in.readLong()) != null) throw new IOException("Duplicate expected field"); }
            c.patches.add(p);
        }
        end(in); return c;
    }
    public static final class Reply {
        public String request = "", result = "OK", message = "", player = "";
        public State state;
    }
    public static byte[] reply(Reply reply) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = output(bytes, LIMIT + 4096);
        out.writeInt(VERSION); text(out, reply.request); text(out, reply.result); text(out, reply.message); text(out, reply.player);
        byte[] state = reply.state == null ? new byte[0] : state(reply.state); out.writeInt(state.length); out.write(state); out.flush(); return bytes.toByteArray();
    }
    public static Reply reply(byte[] bytes) throws IOException {
        DataInputStream in = input(bytes, LIMIT + 4096); if (in.readInt() != VERSION) throw new IOException("Incompatible Mira protocol");
        Reply r = new Reply(); r.request = text(in); r.result = text(in); r.message = text(in); r.player = text(in);
        byte[] state = new byte[count(in, LIMIT)]; in.readFully(state); if (state.length > 0) r.state = state(state); end(in); return r;
    }
    static DataInputStream input(byte[] bytes, int max) throws IOException { if (bytes.length > max) throw new IOException("Payload limit"); return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private static DataOutputStream output(ByteArrayOutputStream bytes, int limit) {
        return new DataOutputStream(new OutputStream() {
            @Override public void write(int value) throws IOException { if (bytes.size() >= limit) throw new IOException("Database/payload size limit"); bytes.write(value); }
            @Override public void write(byte[] value, int offset, int length) throws IOException { if (bytes.size() > limit - length) throw new IOException("Database/payload size limit"); bytes.write(value, offset, length); }
        });
    }
    static int count(DataInputStream in, int max) throws IOException { int n = in.readInt(); if (n < 0 || n > max) throw new IOException("Entry limit"); return n; }
    private static void text(DataOutputStream out, String value) throws IOException {
        if (value == null) throw new IOException("Null text"); byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 4 * 1024 * 1024) throw new IOException("Field limit"); out.writeInt(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in) throws IOException { byte[] bytes = new byte[count(in, 4 * 1024 * 1024)]; in.readFully(bytes); return new String(bytes, StandardCharsets.UTF_8); }
    private static void nullable(DataOutputStream out, String value) throws IOException { out.writeBoolean(value != null); if (value != null) text(out, value); }
    private static String nullable(DataInputStream in) throws IOException { return in.readBoolean() ? text(in) : null; }
    private static void strings(DataOutputStream out, Map<String, String> map) throws IOException {
        out.writeInt(map.size()); for (Map.Entry<String, String> e : map.entrySet()) { text(out, e.getKey()); text(out, e.getValue()); }
    }
    private static void strings(DataInputStream in, Map<String, String> map, int max) throws IOException {
        for (int i = count(in, max); i > 0; i--) { String key = text(in); if (map.put(key, text(in)) != null) throw new IOException("Duplicate field"); }
    }
    private static <E extends Enum<E>> E enumeration(Class<E> type, String value) throws IOException {
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException e) { throw new IOException("Unknown " + type.getSimpleName(), e); }
    }
    private static void end(DataInputStream in) throws IOException { if (in.read() != -1) throw new IOException("Trailing data"); }
}
