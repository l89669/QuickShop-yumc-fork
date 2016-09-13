package org.maxgamer.QuickShop.Shop.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.maxgamer.QuickShop.Shop.ContainerShop;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Serializer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.google.common.base.Optional;

/**
 * Minecraft 虚拟悬浮物品工具类
 * 需要depend ProtocolLib 4.x
 *
 * @author 橙子(chengzi)
 * @version 1.1.0
 */
public class FakeItem_18_110 extends DisplayItem {

    private static Map<String, List<FakeItem_18_110>> fakes = new HashMap<>();
    private static boolean registered = false;
    private static int lastId = Integer.MAX_VALUE;

    private final ItemStack itemStack;
    private final Location location;
    private final int eid;
    private final UUID uuid;
    private boolean created = false;

    public FakeItem_18_110(final ContainerShop containerShop, final ItemStack item) {
        this.itemStack = item;
        this.location = containerShop.getLocation().clone().add(0.5, 1, 0.5);
        this.eid = getFakeEntityId();
        this.uuid = UUID.randomUUID();
    }

    public static boolean isRegistered() {
        return registered;
    }

    public static void register(final Plugin plugin) {
        if (registered) {
            return;
        }
        final PluginManager pm = Bukkit.getPluginManager();
        final Plugin p = pm.getPlugin("ProtocolLib");
        if (p != null) {
            if (!p.isEnabled()) {
                pm.enablePlugin(p);
            }
            if (!p.isEnabled()) {
                throw new IllegalStateException("前置插件ProtocolLib启动失败 请检查版本.");
            }
        } else {
            throw new IllegalStateException("服务器未找到前置插件ProtocolLib.");
        }
        final PacketAdapter chunkPacketListener = new PacketAdapter(plugin, PacketType.Play.Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(final PacketEvent event) {
                try {
                    final PacketContainer packet = event.getPacket();
                    final Player p = event.getPlayer();
                    final int chunkX = packet.getIntegers().read(0);
                    final int chunkZ = packet.getIntegers().read(1);
                    final List<FakeItem_18_110> fakesInChunk = fakes.get(getChunkIdentifyString(p.getWorld().getChunkAt(chunkX, chunkZ)));
                    if (fakesInChunk != null) {
                        for (final FakeItem_18_110 fake : fakesInChunk) {
                            ProtocolLibrary.getProtocolManager().sendServerPacket(p, fake.getSpawnPacket());
                            ProtocolLibrary.getProtocolManager().sendServerPacket(p, fake.getMetadataPacket());
                        }
                    }
                } catch (final Exception e) {
                }
            }
        };
        final PacketAdapter chunkBulkPacketListener = new PacketAdapter(plugin, PacketType.Play.Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(final PacketEvent event) {
                try {
                    final PacketContainer packet = event.getPacket();
                    final Player p = event.getPlayer();
                    final int[] chunksX = packet.getIntegerArrays().read(0);
                    final int[] chunksZ = packet.getIntegerArrays().read(1);
                    for (int i = 0; i < chunksX.length; i++) {
                        final List<FakeItem_18_110> fakesInChunk = fakes.get(getChunkIdentifyString(p.getWorld().getChunkAt(chunksX[i], chunksZ[i])));
                        if (fakesInChunk != null) {
                            for (final FakeItem_18_110 fake : fakesInChunk) {
                                ProtocolLibrary.getProtocolManager().sendServerPacket(p, fake.getSpawnPacket());
                                ProtocolLibrary.getProtocolManager().sendServerPacket(p, fake.getMetadataPacket());
                            }

                        }
                    }
                } catch (final Exception e) {
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(chunkPacketListener);
        ProtocolLibrary.getProtocolManager().addPacketListener(chunkBulkPacketListener);
        registered = true;
    }

    private static String getChunkIdentifyString(final Chunk chunk) {
        return chunk.getWorld().getName() + "@@" + chunk.getX() + "@@" + chunk.getZ();
    }

    private static int getFakeEntityId() {
        return lastId--;
    }

    @Override
    public Location getDisplayLocation() {
        return location;
    }

    @Override
    public Item getItem() {
        return null;
    }

    @Override
    public void remove() {
        destory();
    }

    @Override
    public boolean removeDupe() {
        return true;
    }

    @Override
    public void respawn() {
        ProtocolLibrary.getProtocolManager().broadcastServerPacket(getDestoryPacket());
        ProtocolLibrary.getProtocolManager().broadcastServerPacket(getSpawnPacket());
        ProtocolLibrary.getProtocolManager().broadcastServerPacket(getMetadataPacket());
    }

    @Override
    public void spawn() {
        create();
    }

    private void create() {
        if (!registered) {
            throw new IllegalStateException("You have to call the register method first.");
        }
        if (created) {
            return;
        }
        ProtocolLibrary.getProtocolManager().broadcastServerPacket(getSpawnPacket());
        ProtocolLibrary.getProtocolManager().broadcastServerPacket(getMetadataPacket());

        final String chunkId = getChunkIdentifyString(location.getChunk());
        List<FakeItem_18_110> fakesInChunk = fakes.get(chunkId);
        if (fakesInChunk == null) {
            fakesInChunk = new ArrayList<>();
        }
        fakesInChunk.add(this);
        fakes.put(chunkId, fakesInChunk);
        created = true;
    }

    private void destory() {
        if (!created) {
            return;
        }
        ProtocolLibrary.getProtocolManager().broadcastServerPacket(getDestoryPacket());

        final String chunkId = getChunkIdentifyString(location.getChunk());
        final List<FakeItem_18_110> fakesInChunk = fakes.get(chunkId);
        if (fakesInChunk == null) {
            // NOTE: This is what should not happens if everything is correct.
            created = false;
            return;
        }
        fakesInChunk.remove(this);
        fakes.put(chunkId, fakesInChunk);
        created = false;
    }

    private PacketContainer getDestoryPacket() {
        final PacketContainer fakePacket = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY, true);
        fakePacket.getIntegerArrays().write(0, new int[] { eid });
        return fakePacket;
    }

    private PacketContainer getMetadataPacket() {
        final PacketContainer fakePacket = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
        fakePacket.getIntegers().write(0, eid);
        final WrappedDataWatcher wr = new WrappedDataWatcher();
        final Serializer serializer = WrappedDataWatcher.Registry.getItemStackSerializer(true);
        final WrappedDataWatcherObject object = new WrappedDataWatcher.WrappedDataWatcherObject(6, serializer);
        wr.setObject(object, Optional.of(itemStack));
        fakePacket.getWatchableCollectionModifier().write(0, wr.getWatchableObjects());
        return fakePacket;
    }

    private PacketContainer getSpawnPacket() {
        final PacketContainer fakePacket = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        fakePacket.getIntegers().write(0, eid);
        fakePacket.getModifier().write(1, uuid);
        fakePacket.getDoubles().write(0, location.getX());
        fakePacket.getDoubles().write(1, location.getY());
        fakePacket.getDoubles().write(2, location.getZ());
        fakePacket.getIntegers().write(6, 2);
        return fakePacket;
    }
}
