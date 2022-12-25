package com.lauriethefish.betterportals.bukkit.nms;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.lauriethefish.betterportals.bukkit.util.VersionUtil;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class EntityUtil {
    private static final Method GET_HANDLE;
    private static final Field DATA_WATCHER;
    private static final boolean USE_DIRECT_ENTITY_PACKET;
    private static final Method GET_ENTITY_SPAWN_PACKET;
    private static final Constructor<?> ENTITY_TRACKER_ENTRY_NEW;

    static {
        Class<?> NMS_ENTITY = ReflectionUtil.findClass("net.minecraft.world.entity.Entity");
        Class<?> NMS_DATA_WATCHER = ReflectionUtil.findClass("net.minecraft.network.syncher.DataWatcher");

        GET_HANDLE = ReflectionUtil.findMethod(CraftBukkitClassUtil.findCraftBukkitClass("entity.CraftEntity"), "getHandle");

        DATA_WATCHER = ReflectionUtil.findFieldByType(NMS_ENTITY, NMS_DATA_WATCHER);

        // On newer versions of the game, the Entity NMS class have an abstract method for getting the correct spawn packet that is overridden by every entity.
        USE_DIRECT_ENTITY_PACKET = true;
        ENTITY_TRACKER_ENTRY_NEW = null;

        Class<?> NMS_PACKET = ReflectionUtil.findClass("net.minecraft.network.protocol.Packet");

        int abstractMask = VersionUtil.isMcVersionAtLeast("1.19.3") ? Modifier.PUBLIC : Modifier.ABSTRACT;
        GET_ENTITY_SPAWN_PACKET = ReflectionUtil.findMethodByTypes(NMS_ENTITY, NMS_PACKET, abstractMask, abstractMask);
    }

    /**
     * Gets the entity's wrapped data watcher
     * @param entity The entity to wrap the data watcher of
     * @return The wrapped data watcher
     */
    @NotNull
    public static WrappedDataWatcher getActualDataWatcher(@NotNull Entity entity) {
        return WrappedDataWatcher.getEntityWatcher(entity);
    }

    /**
     * Gets the list of wrapped data values of the provided entity
     * @param entity The entity to wrap the data watcher of
     * @return The wrapped data watcher
     */
    @NotNull
    public static List<WrappedDataValue> getActualWrappedDataValues(@NotNull Entity entity) {
        WrappedDataWatcher dataWatcher = getActualDataWatcher(entity);
        List<WrappedDataValue> wrappedDataValues = new ArrayList<>();
        for(WrappedWatchableObject entry : dataWatcher.getWatchableObjects()) {
            if(entry == null) continue;

            WrappedDataWatcher.WrappedDataWatcherObject watcherObject = entry.getWatcherObject();
            wrappedDataValues.add(new WrappedDataValue(watcherObject.getIndex(), watcherObject.getSerializer(), entry.getRawValue()));
        }
        return wrappedDataValues;
    }

    /**
     * Getting a valid spawn packet that works correctly for a specific {@link Entity} is surprisingly difficult.
     * This method uses some NMS to get the correct spawn packet.
     * @param entity The entity to get the spawn packet of
     * @return A container with the valid packet, or <code>null</code> since some entities can't be spawned with a packet.
     */
    public static @Nullable PacketContainer getRawEntitySpawnPacket(@NotNull Entity entity) {
        if (entity instanceof EnderDragonPart) return null;
        if (entity instanceof Marker) return null;

        Object nmsEntity = ReflectionUtil.invokeMethod(entity, GET_HANDLE);
        if(USE_DIRECT_ENTITY_PACKET) {
            return PacketContainer.fromPacket(ReflectionUtil.invokeMethod(nmsEntity, GET_ENTITY_SPAWN_PACKET));
        }   else    {
            // Create a dummy tracker entry
            Object trackerEntry = ReflectionUtil.invokeConstructor(ENTITY_TRACKER_ENTRY_NEW, nmsEntity, 0, 0, 0, false);
            Object nmsPacket = ReflectionUtil.invokeMethod(trackerEntry, GET_ENTITY_SPAWN_PACKET);
            if(nmsPacket == null) {return null;}

            return PacketContainer.fromPacket(nmsPacket);
        }
    }
}
