package mira.platform.forge1710.inventory;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mira.core.taskbody.InventorySnapshot;
import mira.core.taskbody.ItemSpec;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** Requests only the sender's inventory. All mod/storage reads run on the server tick thread. */
public final class InventoryNetwork {
    public static SimpleNetworkWrapper channel;
    public static volatile InventorySnapshot clientSnapshot;
    public static volatile long receivedAt;
    private static final Map<UUID, EntityPlayerMP> pending = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> last = new ConcurrentHashMap<>();
    public static void initialize() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("mira.inventory");
        channel.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        channel.registerMessage(ReplyHandler.class, Reply.class, 1, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(new InventoryNetwork());
    }
    public static final class Request implements IMessage {
        public void fromBytes(ByteBuf bytes) {}
        public void toBytes(ByteBuf bytes) {}
    }
    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        public IMessage onMessage(Request message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            pending.put(player.getUniqueID(), player);
            return null;
        }
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, EntityPlayerMP> query : pending.entrySet()) {
            if (!pending.remove(query.getKey(), query.getValue())) continue;
            if (now - last.getOrDefault(query.getKey(), 0L) < 400) continue;
            EntityPlayerMP player = query.getValue();
            if (player.playerNetServerHandler == null || player.isDead) continue;
            last.put(query.getKey(), now);
            channel.sendTo(new Reply(CarriedInventory.collect(player)), player);
        }
    }
    public static final class Reply implements IMessage {
        InventorySnapshot snapshot;
        public Reply() {}
        Reply(InventorySnapshot snapshot) { this.snapshot = snapshot; }
        public void toBytes(ByteBuf bytes) {
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList entries = new NBTTagList();
            for (InventorySnapshot.Entry entry : snapshot.entries) {
                NBTTagCompound value = new NBTTagCompound();
                value.setString("item", entry.item.item); value.setInteger("damage", entry.item.damage);
                value.setString("hash", entry.item.tagHash); value.setLong("count", entry.count); value.setBoolean("bag", entry.bag);
                NBTTagList ores = new NBTTagList();
                for (String ore : entry.item.ores) ores.appendTag(new NBTTagString(ore));
                value.setTag("ores", ores); entries.appendTag(value);
            }
            root.setTag("entries", entries);
            NBTTagList gaps = new NBTTagList();
            for (String gap : snapshot.unavailable) gaps.appendTag(new NBTTagString(gap));
            root.setTag("gaps", gaps);
            ByteBufUtils.writeTag(bytes, root);
        }
        public void fromBytes(ByteBuf bytes) {
            NBTTagCompound root = ByteBufUtils.readTag(bytes);
            snapshot = new InventorySnapshot(); snapshot.server = true;
            if (root == null) return;
            NBTTagList entries = root.getTagList("entries", 10);
            for (int i = 0; i < Math.min(512, entries.tagCount()); i++) {
                NBTTagCompound value = entries.getCompoundTagAt(i);
                List<String> ores = new ArrayList<>();
                NBTTagList groups = value.getTagList("ores", 8);
                for (int j = 0; j < Math.min(64, groups.tagCount()); j++) ores.add(groups.getStringTagAt(j));
                ItemSpec item = new ItemSpec(value.getString("item"), value.getInteger("damage"), value.getString("hash"), ores, "", "");
                snapshot.entries.add(new InventorySnapshot.Entry(item, Math.max(0, value.getLong("count")), value.getBoolean("bag")));
            }
            NBTTagList gaps = root.getTagList("gaps", 8);
            for (int i = 0; i < Math.min(32, gaps.tagCount()); i++) snapshot.unavailable.add(gaps.getStringTagAt(i));
        }
    }
    public static final class ReplyHandler implements IMessageHandler<Reply, IMessage> {
        public IMessage onMessage(Reply message, MessageContext context) {
            clientSnapshot = message.snapshot;
            receivedAt = System.currentTimeMillis();
            return null;
        }
    }
}
