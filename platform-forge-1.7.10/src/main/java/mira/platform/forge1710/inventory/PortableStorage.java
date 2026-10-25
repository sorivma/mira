package mira.platform.forge1710.inventory;

import cpw.mods.fml.common.Loader;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Optional integrations are isolated here; unknown item NBT is never recursively treated as inventory. */
final class PortableStorage {
    static void register() {
        CarriedInventory.register(new CarriedInventory.Reader() {
            public boolean accepts(ItemStack stack) { return CarriedInventory.inherits(stack, "forestry.storage.items.ItemBackpack"); }
            public String identity(ItemStack stack) { return ""; }
            public void contents(EntityPlayer player, ItemStack stack, Consumer<ItemStack> output) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag == null) return;
                if (tag.hasKey("Slots", 10)) {
                    NBTTagCompound slots = tag.getCompoundTag("Slots");
                    for (Object key : slots.func_150296_c()) output.accept(ItemStack.loadItemStackFromNBT(slots.getCompoundTag((String) key)));
                } else CarriedInventory.list(tag.getTagList("Items", 10), output);
            }
        });
        CarriedInventory.register(new CarriedInventory.Reader() {
            public boolean accepts(ItemStack stack) { return CarriedInventory.inherits(stack, "com.darkona.adventurebackpack.item.ItemAdventureBackpack"); }
            public String identity(ItemStack stack) { return ""; }
            public void contents(EntityPlayer player, ItemStack stack, Consumer<ItemStack> output) {
                if (stack.hasTagCompound()) CarriedInventory.list(stack.getTagCompound().getCompoundTag("wearableData").getTagList("inventory", 10), output);
            }
        });
        CarriedInventory.register(new CarriedInventory.Reader() {
            public boolean accepts(ItemStack stack) { return CarriedInventory.inherits(stack, "de.eydamos.backpack.item.ItemBackpackBase"); }
            public String identity(ItemStack stack) {
                if (stack.getItemDamage() % 100 == 99) return "Backpack:ender";
                return stack.hasTagCompound() ? "Backpack:" + stack.getTagCompound().getString("backpack-UID") : "";
            }
            public void contents(EntityPlayer player, ItemStack stack, Consumer<ItemStack> output) throws Exception {
                if (player.worldObj.isRemote) throw new IllegalStateException("Backpack contents require a server snapshot");
                if (stack.getItemDamage() % 100 == 99) { CarriedInventory.inventory(player.getInventoryEnderChest(), output); return; }
                if (!stack.hasTagCompound()) return;
                String uid = stack.getTagCompound().getString("backpack-UID");
                if (uid.isEmpty()) return;
                Object handler = Class.forName("de.eydamos.backpack.Backpack").getField("saveFileHandler").get(null);
                NBTTagCompound data = (NBTTagCompound) handler.getClass().getMethod("loadBackpack", String.class).invoke(handler, uid);
                NBTTagCompound inventories = data.getCompoundTag("backpackInventories");
                CarriedInventory.list(inventories.getTagList("backpack", 10), output);
                CarriedInventory.list(inventories.getTagList("craftingGrid", 10), output);
            }
        });
    }

    static void equipped(EntityPlayer player, Consumer<ItemStack> output, List<String> gaps) {
        if (Loader.isModLoaded("Baubles")) try {
            IInventory inventory = (IInventory) Class.forName("baubles.api.BaublesApi").getMethod("getBaubles", EntityPlayer.class).invoke(null, player);
            CarriedInventory.inventory(inventory, output);
        } catch (Exception | LinkageError error) { gaps.add("Baubles equipment unavailable"); }
        if (Loader.isModLoaded("adventurebackpack")) try {
            Class<?> type = Class.forName("com.darkona.adventurebackpack.playerProperties.BackpackProperty");
            Object properties = type.getMethod("get", EntityPlayer.class).invoke(null, player);
            if (properties != null) output.accept((ItemStack) type.getMethod("getWearable").invoke(properties));
        } catch (Exception | LinkageError error) { gaps.add("Equipped Adventure Backpack unavailable"); }
        if (Loader.isModLoaded("Backpack") && !player.worldObj.isRemote) try {
            Object handler = Class.forName("de.eydamos.backpack.Backpack").getField("saveFileHandler").get(null);
            NBTTagCompound data = (NBTTagCompound) handler.getClass().getMethod("loadPlayer", String.class).invoke(handler, player.getUniqueID().toString());
            if (data.hasKey("personalBackpack", 10)) output.accept(ItemStack.loadItemStackFromNBT(data.getCompoundTag("personalBackpack")));
        } catch (Exception | LinkageError error) { gaps.add("Personal Backpack unavailable"); }
    }
}
