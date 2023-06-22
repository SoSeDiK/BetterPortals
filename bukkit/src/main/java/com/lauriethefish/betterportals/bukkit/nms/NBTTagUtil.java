package com.lauriethefish.betterportals.bukkit.nms;

import com.lauriethefish.betterportals.bukkit.util.VersionUtil;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Used to add simple marker tags to items, for example, the portal wand
 */
public class NBTTagUtil {
    private static final String MARKER_PREFIX = "BetterPortals_marker_";
    private static final String MARKER_VALUE = "marked";
    private static final Class<?> CRAFT_ITEM_STACK = CraftBukkitClassUtil.findCraftBukkitClass("inventory.CraftItemStack");

    private static final Class<?> NBT_TAG_STRING;
    private static final Class<?> NBT_TAG_COMPOUND;
    private static final Class<?> ITEM_STACK;
    private static final Class<?> NBT_BASE;

    private static final Method GET_TAG;
    private static final Method GET_STRING;
    private static final Method TAG_SET;
    private static final Method AS_NMS_COPY;
    private static final Method AS_BUKKIT_COPY;
    private static final Constructor<?> STRING_TAG_CTOR;
    private static final Constructor<?> TAG_COMPOUND_CTOR;

    static  {
        NBT_TAG_STRING = ReflectionUtil.findClass("net.minecraft.nbt.NBTTagString");
        NBT_TAG_COMPOUND = ReflectionUtil.findClass("net.minecraft.nbt.NBTTagCompound");
        ITEM_STACK = ReflectionUtil.findClass("net.minecraft.world.item.ItemStack");
        NBT_BASE = ReflectionUtil.findClass("net.minecraft.nbt.NBTBase");

        if (VersionUtil.isMcVersionAtLeast("1.19.0")) {
            GET_TAG = VersionUtil.isMcVersionAtLeast("1.20.0") ? ReflectionUtil.findMethod(ITEM_STACK, "v") : ReflectionUtil.findMethod(ITEM_STACK, "u");
            TAG_SET = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "a", String.class, NBT_BASE);
            GET_STRING = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "l", String.class);
        } else if (VersionUtil.isMcVersionAtLeast("1.18.0")) {
            GET_TAG = ReflectionUtil.findMethod(ITEM_STACK, "t");
            TAG_SET = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "a", String.class, NBT_BASE);
            GET_STRING = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "l", String.class);
        } else {
            GET_TAG = ReflectionUtil.findMethod(ITEM_STACK, "getTag");
            TAG_SET = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "set", String.class, NBT_BASE);
            GET_STRING = ReflectionUtil.findMethod(NBT_TAG_COMPOUND, "getString", String.class);
        }

        AS_NMS_COPY = ReflectionUtil.findMethod(CRAFT_ITEM_STACK, "asNMSCopy", ItemStack.class);
        AS_BUKKIT_COPY = ReflectionUtil.findMethod(CRAFT_ITEM_STACK, "asBukkitCopy", ITEM_STACK);
        STRING_TAG_CTOR = ReflectionUtil.findConstructor(NBT_TAG_STRING, String.class);
        TAG_COMPOUND_CTOR = ReflectionUtil.findConstructor(NBT_TAG_COMPOUND);
    }

    /**
     * Adds a marker NBT tag to <code>item</code>.
     * @param item Item to add the tag to
     * @param name Name of the tag
     * @return A new {@link ItemStack} with the tag. (original is unmodified)
     */
    @NotNull
    public static ItemStack addMarkerTag(@NotNull ItemStack item, @NotNull String name) {
        Object nmsItem = getNMSItemStack(item);

        // Get the NBT tag, or create one if the item doesn't have one
        Object existingTag = ReflectionUtil.invokeMethod(nmsItem, GET_TAG);

        Object itemTag = existingTag == null ? ReflectionUtil.invokeConstructor(TAG_COMPOUND_CTOR) : existingTag;
        Object stringValue = ReflectionUtil.invokeConstructor(STRING_TAG_CTOR, MARKER_VALUE);

        ReflectionUtil.invokeMethod(itemTag, TAG_SET, MARKER_PREFIX + name, stringValue); // Set the value

        return getBukkitItemStack(nmsItem);
    }

    /**
     * Checks if <code>item</code> has a marker tag with <code>name</code>.
     * @param item The item to check
     * @param name The name of the NBT marker tag
     * @return Whether it has the tag
     */
    public static boolean hasMarkerTag(@NotNull ItemStack item, @NotNull String name)	{
        Object nmsItem = getNMSItemStack(item);

        Object itemTag = ReflectionUtil.invokeMethod(nmsItem, GET_TAG); // Otherwise, get the item's NBT tag
        if(itemTag == null) {
            return false;
        }

        String value = (String) ReflectionUtil.invokeMethod(itemTag, GET_STRING, MARKER_PREFIX + name);
        return MARKER_VALUE.equals(value); // Return the value of the key
    }

    @NotNull
    private static Object getNMSItemStack(@NotNull ItemStack item) {
        return ReflectionUtil.invokeMethod(null, AS_NMS_COPY, item);
    }

    @NotNull
    private static ItemStack getBukkitItemStack(@NotNull Object nmsItem) {
        return (ItemStack) ReflectionUtil.invokeMethod(null, AS_BUKKIT_COPY, nmsItem);
    }
}
