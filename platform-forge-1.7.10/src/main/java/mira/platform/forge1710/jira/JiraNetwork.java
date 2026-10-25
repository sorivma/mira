package mira.platform.forge1710.jira;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import mira.core.jira.JiraDatabase;
import mira.core.jira.Model.*;
import mira.platform.forge1710.MiraMod;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.logging.log4j.LogManager;

/** Chunked, bounded transport. Domain and disk work run exclusively on the server tick thread. */
public final class JiraNetwork {
    private static final int CHUNK = 24000, MAX_COMPRESSED = DatabaseCodec.LIMIT + 65536;
    private static SimpleNetworkWrapper channel;
    private static final AtomicLong transfers = new AtomicLong();
    private static final Map<UUID, Inbox> incoming = new ConcurrentHashMap<>();
    private static final Map<UUID, Queue<Pending>> requests = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> delivered = new HashMap<>(); // Server thread only.
    private static final Inbox clientInbox = new Inbox(MAX_COMPRESSED);
    private static WorldServer world;
    private static JiraDatabase database;
    private static String openError = "";
    private static long retryOpen;
    private JiraNetwork() {}
    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("mira.jira");
        channel.registerMessage(RequestHandler.class, RequestFrame.class, 0, Side.SERVER);
        channel.registerMessage(ReplyHandler.class, ReplyFrame.class, 1, Side.CLIENT);
        JiraNetwork events = new JiraNetwork(); FMLCommonHandler.instance().bus().register(events); MinecraftForge.EVENT_BUS.register(events);
    }
    public static JiraDatabase database() throws IOException {
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer current = server == null ? null : server.worldServerForDimension(0);
        if (current == null) throw new IOException("Overworld is not available");
        if (world != current) { world = current; database = null; openError = ""; retryOpen = 0; delivered.clear(); }
        if (database == null) {
            if (System.currentTimeMillis() < retryOpen) throw new IOException(openError);
            try { database = WorldDatabase.open(world); openError = ""; }
            catch (IOException e) { openError = e.getMessage(); retryOpen = System.currentTimeMillis() + 5000; throw e; }
        }
        return database;
    }
    public static void stop() { database = null; world = null; incoming.clear(); requests.clear(); delivered.clear(); retryOpen = 0; }
    public static void resetClient() { synchronized (clientInbox) { clientInbox.reset(); } }
    public static void request(Command command) throws IOException {
        byte[] payload = command == null ? new byte[0] : DatabaseCodec.command(command);
        byte[] packed = zip(payload); long transfer = transfers.incrementAndGet();
        for (int i = 0; i < pieces(packed.length); i++) channel.sendToServer(new RequestFrame(transfer, i, packed));
    }
    private static void send(EntityPlayerMP player, DatabaseCodec.Reply reply) throws IOException {
        reply.player = player.getUniqueID().toString();
        if (reply.message.length() > 1000) reply.message = reply.message.substring(0, 997) + "...";
        if (reply.request.length() > 80) reply.request = "";
        byte[] packed = zip(DatabaseCodec.reply(reply)); long transfer = transfers.incrementAndGet();
        for (int i = 0; i < pieces(packed.length); i++) channel.sendTo(new ReplyFrame(transfer, i, packed), player);
    }
    private static void enqueue(EntityPlayerMP player, byte[] packed) {
        Queue<Pending> queue = requests.computeIfAbsent(player.getUniqueID(), id -> new ArrayDeque<>());
        synchronized (queue) { if (queue.size() < 8) queue.add(new Pending(player, packed)); }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) enqueue((EntityPlayerMP) event.player, null);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player.getUniqueID(); incoming.remove(id); requests.remove(id); delivered.remove(id);
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) { if (!event.world.isRemote && event.world == world) stop(); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = MinecraftServer.getServer(); if (server == null || server.getConfigurationManager() == null) return;
        incoming.entrySet().removeIf(e -> e.getValue().expired());
        for (Map.Entry<UUID, Queue<Pending>> entry : requests.entrySet()) {
            Pending pending; Queue<Pending> queue = entry.getValue(); synchronized (queue) { pending = queue.poll(); }
            if (pending == null) continue;
            EntityPlayerMP player = pending.player;
            if (!server.getConfigurationManager().playerEntityList.contains(player) || player.playerNetServerHandler == null) continue;
            DatabaseCodec.Reply reply = new DatabaseCodec.Reply();
            try {
                byte[] bytes = pending.packed == null ? new byte[0] : unzip(pending.packed, 4 * 1024 * 1024);
                Command command = bytes.length == 0 ? null : DatabaseCodec.command(bytes);
                if (command != null) reply.request = command.id;
                JiraDatabase db = database(); String actor = player.getUniqueID().toString();
                db.rememberPlayer(actor, player.getCommandSenderName());
                if (command != null) {
                    db.execute(actor, player.getCommandSenderName(), player.canCommandSenderUseCommand(2, "miraadmin"), command);
                }
                reply.state = db.view(actor); delivered.put(player.getUniqueID(), reply.state.revision);
            } catch (Conflict e) { reply.result = "CONFLICT"; reply.message = e.getMessage(); }
            catch (Rejected | IOException e) { reply.result = "ERROR"; reply.message = e.getMessage(); }
            catch (RuntimeException e) { reply.result = "ERROR"; reply.message = "Mira could not process this request"; LogManager.getLogger("Mira").error(reply.message, e); }
            if (reply.state == null && database != null) reply.state = database.view(player.getUniqueID().toString());
            try { send(player, reply); } catch (IOException e) { LogManager.getLogger("Mira").error("Could not send Mira reply", e); }
        }
        if (database == null) return;
        long revision = database.revision();
        for (Object value : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value; Long previous = delivered.get(player.getUniqueID());
            if (previous == null || previous == revision) continue;
            DatabaseCodec.Reply reply = new DatabaseCodec.Reply(); reply.state = database.view(player.getUniqueID().toString());
            try { send(player, reply); delivered.put(player.getUniqueID(), revision); }
            catch (IOException e) { LogManager.getLogger("Mira").error("Could not push Mira update", e); }
        }
    }
    private static final class Pending {
        final EntityPlayerMP player; final byte[] packed;
        Pending(EntityPlayerMP player, byte[] packed) { this.player = player; this.packed = packed; }
    }
    public abstract static class Frame implements IMessage {
        long transfer; int index, total; byte[] payload;
        public Frame() {}
        Frame(long transfer, int index, byte[] data) {
            this.transfer = transfer; this.index = index; this.total = data.length;
            payload = Arrays.copyOfRange(data, index * CHUNK, Math.min(data.length, (index + 1) * CHUNK));
        }
        @Override public void toBytes(ByteBuf out) { out.writeLong(transfer); out.writeInt(index); out.writeInt(total); out.writeInt(payload.length); out.writeBytes(payload); }
        @Override public void fromBytes(ByteBuf in) {
            transfer = in.readLong(); index = in.readInt(); total = in.readInt(); int size = in.readInt();
            if (total < 1 || total > MAX_COMPRESSED || index < 0 || index >= pieces(total) || size != Math.min(CHUNK, total - index * CHUNK) || in.readableBytes() != size)
                throw new IllegalArgumentException("Invalid Mira frame");
            payload = new byte[size]; in.readBytes(payload);
        }
    }
    public static final class RequestFrame extends Frame {
        public RequestFrame() {} RequestFrame(long transfer, int index, byte[] bytes) { super(transfer, index, bytes); }
    }
    public static final class ReplyFrame extends Frame {
        public ReplyFrame() {} ReplyFrame(long transfer, int index, byte[] bytes) { super(transfer, index, bytes); }
    }
    public static final class RequestHandler implements IMessageHandler<RequestFrame, IMessage> {
        @Override public IMessage onMessage(RequestFrame frame, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            Inbox inbox = incoming.computeIfAbsent(player.getUniqueID(), id -> new Inbox(4 * 1024 * 1024 + 4096));
            byte[] bytes; synchronized (inbox) { bytes = inbox.accept(frame); }
            if (bytes != null) enqueue(player, bytes); return null;
        }
    }
    public static final class ReplyHandler implements IMessageHandler<ReplyFrame, IMessage> {
        @Override public IMessage onMessage(ReplyFrame frame, MessageContext context) {
            byte[] bytes; synchronized (clientInbox) { bytes = clientInbox.accept(frame); }
            if (bytes != null) {
                try { MiraMod.proxy.receiveJira(context.netHandler, unzip(bytes, DatabaseCodec.LIMIT + 4096)); }
                catch (IOException e) { LogManager.getLogger("Mira").error("Invalid Mira reply", e); }
            }
            return null;
        }
    }
    private static final class Inbox {
        final int limit; long transfer, touched; int index, total; ByteArrayOutputStream bytes;
        Inbox(int limit) { this.limit = limit; }
        synchronized boolean expired() { return touched != 0 && System.currentTimeMillis() - touched > 15000; }
        void reset() { bytes = null; index = total = 0; touched = 0; }
        byte[] accept(Frame frame) {
            if (frame.total > limit) { reset(); return null; }
            if (frame.index == 0) { reset(); transfer = frame.transfer; total = frame.total; bytes = new ByteArrayOutputStream(); }
            if (expired() || bytes == null || transfer != frame.transfer || total != frame.total || frame.index != index) { reset(); return null; }
            bytes.write(frame.payload, 0, frame.payload.length); touched = System.currentTimeMillis(); index++;
            if (bytes.size() == total) { byte[] result = bytes.toByteArray(); reset(); return result; }
            return null;
        }
    }
    private static int pieces(int size) { return (size + CHUNK - 1) / CHUNK; }
    private static byte[] zip(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); try (GZIPOutputStream gzip = new GZIPOutputStream(out)) { gzip.write(bytes); }
        byte[] result = out.toByteArray(); if (result.length > MAX_COMPRESSED) throw new IOException("Compressed payload limit"); return result;
    }
    private static byte[] unzip(byte[] bytes, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) { if (out.size() > limit - n) throw new IOException("Expanded payload limit"); out.write(buffer, 0, n); }
        }
        return out.toByteArray();
    }
}
