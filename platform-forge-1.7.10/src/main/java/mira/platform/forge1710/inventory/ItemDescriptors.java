package mira.platform.forge1710.inventory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mira.core.taskbody.ItemSpec;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

public final class ItemDescriptors {
    private ItemDescriptors() {}
    public static ItemSpec describe(ItemStack stack) {
        if (stack == null || stack.getItem() == null) throw new IllegalArgumentException("Missing item");
        String name = (String) Item.itemRegistry.getNameForObject(stack.getItem());
        if (name == null) throw new IllegalArgumentException("Unregistered item");
        List<String> ores = new ArrayList<>();
        for (int id : OreDictionary.getOreIDs(stack)) ores.add(OreDictionary.getOreName(id));
        Collections.sort(ores);
        String tag = stack.hasTagCompound() ? stack.getTagCompound().toString() : "";
        return new ItemSpec(name, stack.getItemDamage(),
            fingerprint(stack.getTagCompound()), ores, stack.getDisplayName(), tag);
    }
    public static ItemStack stack(ItemSpec spec) {
        Item item = (Item) Item.itemRegistry.getObject(spec.item);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item, 1, spec.damage == 32767 ? 0 : spec.damage);
        if (!spec.tag.isEmpty()) try { stack.setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(spec.tag)); }
        catch (Exception ignored) { /* The registry identity still renders if an old tag is no longer readable. */ }
        return stack;
    }
    private static String fingerprint(NBTBase tag) {
        if (tag == null) return "";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical(tag).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String canonical(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            List<String> keys = new ArrayList<>();
            for (Object key : compound.func_150296_c()) keys.add((String) key);
            Collections.sort(keys);
            StringBuilder result = new StringBuilder("{");
            for (String key : keys) result.append(key.length()).append(':').append(key).append(canonical(compound.getTag(key)));
            return result.append('}').toString();
        }
        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag.copy();
            StringBuilder result = new StringBuilder("[");
            while (list.tagCount() > 0) result.append(canonical(list.removeTag(0))).append(';');
            return result.append(']').toString();
        }
        return tag.getId() + ":" + tag.toString();
    }
}
