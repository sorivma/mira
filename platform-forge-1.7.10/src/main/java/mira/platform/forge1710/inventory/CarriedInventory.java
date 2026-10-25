package mira.platform.forge1710.inventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import mira.core.taskbody.InventorySnapshot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

/** Read-only inventory collection. New storage mods register a reader, not a new task widget. */
public final class CarriedInventory {
    public interface Reader {
        boolean accepts(ItemStack stack);
        String identity(ItemStack stack);
        void contents(EntityPlayer player, ItemStack stack, Consumer<ItemStack> output) throws Exception;
    }
    private static final List<Reader> readers = new ArrayList<>();
    static { PortableStorage.register(); }
    private CarriedInventory() {}
    /** Register during initialization; a later registration may override a built-in reader. */
    public static void register(Reader reader) { readers.add(0, reader); }

    public static InventorySnapshot collect(EntityPlayer player) {
        InventorySnapshot result = new InventorySnapshot();
        result.server = !player.worldObj.isRemote;
        Scan scan = new Scan(player, result);
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) scan.stack(player.inventory.getStackInSlot(i), false, 0);
        scan.stack(player.inventory.getItemStack(), false, 0);
        PortableStorage.equipped(player, stack -> scan.stack(stack, true, 0), result.unavailable);
        return result;
    }

    private static final class Scan {
        final EntityPlayer player;
        final InventorySnapshot result;
        final Set<ItemStack> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());
        final Set<String> bags = new HashSet<>();
        int visited;
        Scan(EntityPlayer player, InventorySnapshot result) { this.player = player; this.result = result; }
        void stack(ItemStack stack, boolean bag, int depth) {
            if (stack == null || stack.stackSize <= 0 || !seen.add(stack)) return;
            if (++visited > 2048 || depth > 6) { gap("Portable storage scan limit"); return; }
            if (result.entries.size() < 512) {
                try { result.entries.add(new InventorySnapshot.Entry(ItemDescriptors.describe(stack), stack.stackSize, bag)); }
                catch (RuntimeException | LinkageError error) { gap("Unreadable carried item"); return; }
            }
            else { gap("Inventory snapshot limit"); return; }
            for (Reader reader : readers) {
                try {
                    if (!reader.accepts(stack)) continue;
                    String id = reader.identity(stack);
                    if (!id.isEmpty() && !bags.add(id)) return;
                    reader.contents(player, stack, child -> stack(child, true, depth + 1));
                } catch (Exception | LinkageError error) { gap("Portable storage unavailable"); }
                return;
            }
        }
        void gap(String message) { if (!result.unavailable.contains(message)) result.unavailable.add(message); }
    }

    static void list(NBTTagList list, Consumer<ItemStack> output) {
        for (int i = 0; i < Math.min(1024, list.tagCount()); i++) output.accept(ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i)));
    }
    static void inventory(IInventory inventory, Consumer<ItemStack> output) {
        if (inventory != null) for (int i = 0; i < inventory.getSizeInventory(); i++) output.accept(inventory.getStackInSlot(i));
    }
    static boolean inherits(ItemStack stack, String type) {
        for (Class<?> value = stack.getItem().getClass(); value != null; value = value.getSuperclass())
            if (value.getName().equals(type)) return true;
        return false;
    }
}
