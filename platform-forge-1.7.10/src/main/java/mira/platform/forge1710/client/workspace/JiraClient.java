package mira.platform.forge1710.client.workspace;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import mira.core.jira.Model.*;
import mira.platform.forge1710.jira.DatabaseCodec;
import mira.platform.forge1710.jira.JiraNetwork;
import net.minecraft.client.Minecraft;

/** Receives immutable bytes on Netty, applies snapshots and callbacks only on the client tick thread. */
public final class JiraClient implements WorkspaceBackend {
    private static JiraClient instance = new JiraClient();
    private final Queue<Delivery> mailbox = new ConcurrentLinkedQueue<>();
    private final List<Consumer<State>> listeners = new ArrayList<>();
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private State state = new State();
    private boolean ready;
    private String player = "";
    private long requested;
    private String status = "Connecting to world workspace database...";
    private JiraClient() {}
    static WorkspaceBackend backend() { return instance; }
    public static void receive(Object connection, byte[] bytes) { instance.mailbox.add(new Delivery(connection, bytes)); }
    public static void reset() { JiraNetwork.resetClient(); instance = new JiraClient(); }
    public static void update() { instance.tick(); }
    @Override public State state() { return state; }
    @Override public String player() {
        return player;
    }
    @Override public boolean ready() { return ready; }
    @Override public String status() { return status; }
    @Override public void listen(Consumer<State> listener) { listeners.add(listener); listener.accept(state); }
    @Override public void submit(Command command, Consumer<DatabaseCodec.Reply> complete) {
        if (!ready || Minecraft.getMinecraft().thePlayer == null) {
            DatabaseCodec.Reply r = new DatabaseCodec.Reply(); r.result = "ERROR"; r.message = "World workspace is not connected"; complete.accept(r); return;
        }
        Pending p = new Pending(command, complete); pending.put(command.id, p); send(p);
    }
    private void send(Pending p) {
        try { JiraNetwork.request(p.command); p.sent = System.currentTimeMillis(); }
        catch (IOException e) {
            pending.remove(p.command.id); DatabaseCodec.Reply r = new DatabaseCodec.Reply(); r.result = "ERROR"; r.message = e.getMessage(); p.complete.accept(r);
        }
    }
    private void tick() {
        Delivery delivery;
        while ((delivery = mailbox.poll()) != null) {
            if (delivery.connection != Minecraft.getMinecraft().getNetHandler()) continue;
            try {
                DatabaseCodec.Reply reply = DatabaseCodec.reply(delivery.bytes);
                player = reply.player;
                if (reply.state != null && (!ready || reply.state.revision >= state.revision)) {
                    state = reply.state; ready = true; status = "World saved / revision " + state.revision;
                    for (Consumer<State> listener : new ArrayList<>(listeners)) listener.accept(state);
                    PinnedTaskHud.refresh(state);
                }
                if (!reply.result.equals("OK")) status = reply.message;
                Pending p = pending.remove(reply.request); if (p != null) p.complete.accept(reply);
            } catch (IOException e) { status = "Mira synchronization failed: " + e.getMessage(); }
        }
        if (Minecraft.getMinecraft().thePlayer == null) return;
        long now = System.currentTimeMillis();
        if (!ready && now - requested > 2000) {
            requested = now; try { JiraNetwork.request(null); } catch (IOException e) { status = e.getMessage(); }
        }
        for (Pending p : new ArrayList<>(pending.values())) if (now - p.sent > 5000) send(p);
    }
    private static final class Pending {
        final Command command; final Consumer<DatabaseCodec.Reply> complete; long sent;
        Pending(Command command, Consumer<DatabaseCodec.Reply> complete) { this.command = command; this.complete = complete; }
    }
    private static final class Delivery {
        final Object connection; final byte[] bytes;
        Delivery(Object connection, byte[] bytes) { this.connection = connection; this.bytes = bytes; }
    }
}
