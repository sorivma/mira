package mira.platform.forge1710.jira;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import mira.core.jira.JiraDatabase;
import mira.core.jira.Model.State;
import mira.core.jira.Rules;
import net.minecraft.world.WorldServer;

/** One database in the overworld save directory, shared by all dimensions. */
public final class WorldDatabase implements JiraDatabase.Store {
    private final Path file;
    private final WorldServer world;
    private WorldDatabase(WorldServer world) {
        this(world.getSaveHandler().getWorldDirectory().toPath(), world);
    }
    private WorldDatabase(Path directory, WorldServer world) {
        this.world = world; file = directory.resolve("data").resolve("mira-workspaces.dat");
    }
    public static JiraDatabase open(WorldServer overworld) throws IOException {
        return open(new WorldDatabase(overworld));
    }
    static JiraDatabase openDirectory(Path directory) throws IOException { return open(new WorldDatabase(directory, null)); }
    private static JiraDatabase open(WorldDatabase store) throws IOException {
        State state = new State();
        if (Files.exists(store.file)) {
            state = read(store.file);
            // Fail closed rather than silently loading an older backup and losing accepted changes.
            try { Rules.validate(state); } catch (RuntimeException e) { throw new IOException("Invalid Mira database; restore a known-good backup manually", e); }
        } else if (Files.exists(store.backup())) throw new IOException("Mira database is missing but its backup exists; restore it manually");
        return new JiraDatabase(state, store);
    }
    private Path backup() { return file.resolveSibling(file.getFileName() + ".bak"); }
    @Override public void write(State state) throws IOException {
        if (world != null) try { world.getSaveHandler().checkSessionLock(); } catch (net.minecraft.world.MinecraftException e) { throw new IOException("World session lock was lost", e); }
        byte[] bytes = DatabaseCodec.state(state), digest = digest(bytes);
        Files.createDirectories(file.getParent()); Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (FileOutputStream stream = new FileOutputStream(temp.toFile()); DataOutputStream out = new DataOutputStream(stream)) {
                out.writeInt(bytes.length); out.write(digest); out.write(bytes); out.flush(); stream.getFD().sync();
            }
            if (Files.exists(file)) {
                Path backupTemp = backup().resolveSibling(backup().getFileName() + ".tmp");
                Files.copy(file, backupTemp, StandardCopyOption.REPLACE_EXISTING); replace(backupTemp, backup());
            }
            replace(temp, file);
        } finally { Files.deleteIfExists(temp); }
    }
    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static State read(Path file) throws IOException {
        if (Files.size(file) > DatabaseCodec.LIMIT + 36L) throw new IOException("Mira database exceeds limit");
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int size = in.readInt(); if (size < 0 || size > DatabaseCodec.LIMIT) throw new IOException("Invalid database length");
            byte[] expected = new byte[32], bytes = new byte[size]; in.readFully(expected); in.readFully(bytes);
            if (in.read() != -1 || !Arrays.equals(expected, digest(bytes))) throw new IOException("Mira database checksum mismatch; restore a known-good backup manually");
            return DatabaseCodec.state(bytes);
        }
    }
    private static byte[] digest(byte[] bytes) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch (NoSuchAlgorithmException e) { throw new IOException(e); }
    }
}
