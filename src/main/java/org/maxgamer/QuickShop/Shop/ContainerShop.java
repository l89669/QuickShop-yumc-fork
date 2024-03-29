package org.maxgamer.QuickShop.Shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.maxgamer.QuickShop.QuickShop;
import org.maxgamer.QuickShop.Shop.Item.DisplayItem;
import org.maxgamer.QuickShop.Util.MsgUtil;
import org.maxgamer.QuickShop.Util.Util;

import pw.yumc.YumCore.bukkit.P;

public class ContainerShop implements Shop {
    private final QuickShop plugin = (QuickShop) P.instance;
    private final String signTitle = plugin.getConfigManager().getGuiTitle();
    private DisplayItem displayItem;
    private final ItemStack item;
    private final Location loc;
    private String owner;
    private double price;
    private ShopType shopType;
    private boolean unlimited;

    /**
     * Adds a new shop.
     *
     * @param loc
     *            The location of the chest block
     * @param price
     *            The cost per item
     * @param item
     *            The itemstack with the properties we want. This is .cloned, no
     *            need to worry about references
     * @param owner
     *            The player who owns this shop.
     */
    public ContainerShop(final Location loc, final double price, final ItemStack item, final String owner) {
        this.loc = loc;
        this.price = price;
        this.owner = owner;
        this.item = item.clone();
        this.item.setAmount(1);
        this.displayItem = DisplayItem.create(this);
        this.shopType = ShopType.SELLING;
    }

    private ContainerShop(final ContainerShop s) {
        this.displayItem = s.displayItem;
        this.shopType = s.shopType;
        this.item = s.item;
        this.loc = s.loc;
        this.unlimited = s.unlimited;
        this.owner = s.owner;
        this.price = s.price;
    }

    /**
     * Add an item to shops chest.
     *
     * @param item
     *            The itemstack. The amount does not matter, just everything
     *            else
     * @param amount
     *            The amount to add to the shop.
     */
    @Override
    public void add(final ItemStack item, final int amount) {
        if (this.unlimited) { return; }
        final Inventory inv = this.getInventory();
        int remains = amount;
        while (remains > 0) {
            final int stackSize = Math.min(remains, item.getMaxStackSize());
            item.setAmount(stackSize);
            inv.addItem(item);
            remains = remains - stackSize;
        }
    }

    /**
     * Buys amount of item from Player p. Does NOT check our inventory, or
     * balances
     *
     * @param p
     *            The player to buy from
     * @param amount
     *            The amount to buy
     */
    @Override
    public void buy(final Player p, int amount) {
        if (amount < 0) {
            this.sell(p, -amount);
        }
        if (this.isUnlimited()) {
            final ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; amount > 0 && i < contents.length; i++) {
                final ItemStack stack = contents[i];
                if (stack == null) {
                    continue; // No item
                }
                if (matches(stack)) {
                    final int stackSize = Math.min(amount, stack.getAmount());
                    stack.setAmount(stack.getAmount() - stackSize);
                    amount -= stackSize;
                }
            }
            // Send the players new inventory to them
            p.getInventory().setContents(contents);
            // This should not happen.
            if (amount > 0) {
                plugin.getLogger().log(Level.WARNING, "Could not take all items from a players inventory on purchase! " + p.getName() + ", missing: " + amount + ", item: " + this.getDataName() + "!");
            }
        } else {
            final ItemStack[] playerContents = p.getInventory().getContents();
            final Inventory chestInv = this.getInventory();
            for (int i = 0; amount > 0 && i < playerContents.length; i++) {
                ItemStack item = playerContents[i];
                if (item != null && this.matches(item)) {
                    // Copy it, we don't want to interfere
                    item = item.clone();
                    // Amount = total, item.getAmount() = how many items in the
                    // stack
                    final int stackSize = Math.min(amount, item.getAmount());
                    // If Amount is item.getAmount(), then this sets the amount
                    // to 0
                    // Else it sets it to the remainder
                    playerContents[i].setAmount(playerContents[i].getAmount() - stackSize);
                    // We can modify this, it is a copy.
                    item.setAmount(stackSize);
                    // Add the items to the players inventory
                    chestInv.addItem(item);
                    amount -= stackSize;
                }
            }
            // Now update the players inventory.
            p.getInventory().setContents(playerContents);
        }
    }

    /**
     * Returns a clone of this shop. References to the same display item,
     * itemstack, location and owner as this shop does. Do not modify them or
     * you will modify this shop.
     *
     * **NOT A DEEP CLONE**
     */
    @Override
    public ContainerShop clone() {
        return new ContainerShop(this);
    }

    /**
     * Deletes the shop from the list of shops and queues it for database
     * deletion *DOES* delete it from memory
     */
    @Override
    public void delete() {
        delete(true);
    }

    /**
     * Deletes the shop from the list of shops and queues it for database
     * deletion
     *
     * @param fromMemory
     *            True if you are *NOT* iterating over this currently, *false if
     *            you are iterating*
     */
    @SuppressWarnings("deprecation")
    @Override
    public void delete(final boolean fromMemory) {
        // Delete the display item
        if (this.getDisplayItem() != null) {
            this.getDisplayItem().remove();
        }
        // Delete the signs around it
        for (final Sign s : this.getSigns()) {
            s.getBlock().setType(Material.AIR);
        }
        // Delete it from the database
        final int x = this.getLocation().getBlockX();
        final int y = this.getLocation().getBlockY();
        final int z = this.getLocation().getBlockZ();
        final String world = this.getLocation().getWorld().getName();
        // Async database execute
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				plugin.getDB().execute("DELETE FROM shops WHERE x = '" + x + "' AND y = '" + y + "' AND z = '" + z + "' AND world = '" + world + "'");
			}
		});
        // Refund if necessary
        if (plugin.getConfig().getBoolean("shop.refund")) {
            plugin.getEcon().deposit(this.getOwner(), plugin.getConfig().getDouble("shop.cost"));
        }
        if (fromMemory) {
            // Delete it from memory
            plugin.getShopManager().removeShop(this);
        }
    }

    /**
     * Returns the shop that shares it's inventory with this one.
     *
     * @return the shop that shares it's inventory with this one. Will return
     *         null if this shop is not attached to another.
     */
    public ContainerShop getAttachedShop() {
        if (this.getLocation() != null) { return null; }
        final Block c = Util.getSecondHalf(this.getLocation().getBlock());
        if (c == null) { return null; }
        final Shop shop = plugin.getShopManager().getShop(c.getLocation());
        return shop == null ? null : (ContainerShop) shop;
    }

    /**
     * Convenience method. Equivilant to
     * org.maxgamer.quickshop.Util.getName(shop.getItem()).
     *
     * @return The name of this shops item
     */
    @Override
    public String getDataName() {
        return Util.getName(this.getItem());
    }

    /**
     * Returns the display item associated with this shop.
     *
     * @return The display item associated with this shop.
     */
    public DisplayItem getDisplayItem() {
        return this.displayItem;
    }

    /**
     * @return The durability of the item
     */
    @Override
    public short getDurability() {
        return this.item.getDurability();
    }

    /**
     * @return The enchantments the shop has on its items.
     */
    public Map<Enchantment, Integer> getEnchants() {
        return this.item.getItemMeta().getEnchants();
    }

    /**
     * @return The chest this shop is based on.
     */
    public Inventory getInventory() throws IllegalStateException {
        try {
            final InventoryHolder container = (InventoryHolder) this.loc.getBlock().getState();
            return container.getInventory();
        } catch (final Exception e) {
            throw new IllegalStateException("容器已经不存在了!", e);
        }
    }

    /**
     * @return Returns a dummy itemstack of the item this shop is selling.
     */
    @Override
    public ItemStack getItem() {
        return item;
    }

    /**
     * @return The location of the shops chest
     */
    @Override
    public Location getLocation() {
        return this.loc;
    }

    /**
     * @return The ItemStack type of this shop
     */
    public Material getMaterial() {
        return this.item.getType();
    }

    /**
     * @return The name of the player who owns the shop.
     */
    @Override
    public String getOwner() {
        return this.owner;
    }

    /**
     * @return The price per item this shop is selling
     */
    @Override
    public double getPrice() {
        return this.price;
    }

    /**
     * Returns the number of free spots in the chest for the particular item.
     * @return
     */
    @Override
    public int getRemainingSpace() {
        if (this.unlimited) { return 10000; }
        return Util.countSpace(this.getInventory(), item);
    }

    /**
     * Returns the number of items this shop has in stock.
     *
     * @return The number of items available for purchase.
     */
    @Override
    public int getRemainingStock() {
        if (this.unlimited) { return 10000; }
        return Util.countItems(this.getInventory(), this.getItem());
    }

    @Override
    public ShopType getShopType() {
        return this.shopType;
    }

    /**
     * Returns a list of signs that are attached to this shop (QuickShop and
     * blank signs only)
     *
     * @return a list of signs that are attached to this shop (QuickShop and
     *         blank signs only)
     */
    @Override
    public List<Sign> getSigns() {
        final ArrayList<Sign> signs = new ArrayList<>(1);
        if (this.getLocation().getWorld() == null) { return signs; }
        final Block[] blocks = new Block[4];
        blocks[0] = loc.getBlock().getRelative(1, 0, 0);
        blocks[1] = loc.getBlock().getRelative(-1, 0, 0);
        blocks[2] = loc.getBlock().getRelative(0, 0, 1);
        blocks[3] = loc.getBlock().getRelative(0, 0, -1);
        for (final Block b : blocks) {
            if (b.getType() != Material.WALL_SIGN) {
                continue;
            }
            if (!isAttached(b)) {
                continue;
            }
            final Sign sign = (Sign) b.getState();
            if (sign.getLine(0).contains("[QuickShop]") || signTitle.startsWith(sign.getLine(0))) {
                signs.add(sign);
            } else {
                boolean text = false;
                for (final String s : sign.getLines()) {
                    if (!s.isEmpty()) {
                        text = true;
                        break;
                    }
                }
                if (!text) {
                    signs.add(sign);
                }
            }
        }
        return signs;
    }

    @Override
    public boolean isAttached(final Block b) {
        if (b.getType() != Material.WALL_SIGN) {
            new IllegalArgumentException(b + " Is not a sign!").printStackTrace();
        }
        return this.getLocation().getBlock().equals(Util.getAttached(b));
    }

    @Override
    public boolean isBuying() {
        return this.shopType == ShopType.BUYING;
    }

    /**
     * Returns true if this shop is a double chest, and the other half is
     * selling/buying the same as this is buying/selling.
     *
     * @return true if this shop is a double chest, and the other half is
     *         selling/buying the same as this is buying/selling.
     */
    public boolean isDoubleShop() {
        final ContainerShop nextTo = this.getAttachedShop();
        if (nextTo == null) { return false; }
        if (nextTo.matches(this.getItem())) {
            // They're both trading the same item
            if (this.getShopType() == nextTo.getShopType()) {
                // They're both buying or both selling => Not a double shop,
                // just two shops.
                return false;
            }
            // One is buying, one is selling.
            return true;
        }
        return false;
    }

    @Override
    public boolean isSelling() {
        return this.shopType == ShopType.SELLING;
    }

    @Override
    public boolean isUnlimited() {
        return this.unlimited;
    }

    @Override
    public boolean isValid() {
        checkDisplay();
        return Util.canBeShop(this.getLocation().getBlock());
    }

    /**
     * Returns true if the ItemStack matches what this shop is selling/buying
     *
     * @param item
     *            The ItemStack
     * @return True if the ItemStack is the same (Excludes amounts)
     */
    @Override
    public boolean matches(final ItemStack item) {
        return Util.matches(this.item, item);
    }

    @Override
    public void onClick() {
        this.setSignText();
    }

    @Override
    public void onLoad() {
        checkDisplay();
    }

    @Override
    public void onUnload() {
        if (this.getDisplayItem() != null) {
            this.getDisplayItem().remove();
            this.displayItem = null;
        }
    }

    /**
     * Removes an item from the shop.
     *
     * @param item
     *            The itemstack. The amount does not matter, just everything
     *            else
     * @param amount
     *            The amount to remove from the shop.
     */
    @Override
    public void remove(final ItemStack item, final int amount) {
        if (this.unlimited) { return; }
        final Inventory inv = this.getInventory();
        int remains = amount;
        while (remains > 0) {
            final int stackSize = Math.min(remains, item.getMaxStackSize());
            item.setAmount(stackSize);
            inv.removeItem(item);
            remains = remains - stackSize;
        }
    }

    /**
     * Sells amount of item to Player p. Does NOT check our inventory, or
     * balances
     *
     * @param p
     *            The player to sell to
     * @param amount
     *            The amount to sell
     */
    @Override
    public void sell(final Player p, int amount) {
        if (amount < 0) {
            this.buy(p, -amount);
        }
        // Items to drop on floor
        final ArrayList<ItemStack> floor = new ArrayList<>(5);
        final Inventory pInv = p.getInventory();
        if (this.isUnlimited()) {
            final ItemStack item = this.item.clone();
            while (amount > 0) {
                final int stackSize = Math.min(amount, this.item.getMaxStackSize());
                item.setAmount(stackSize);
                pInv.addItem(item);
                amount -= stackSize;
            }
        } else {
            final ItemStack[] chestContents = this.getInventory().getContents();
            for (int i = 0; amount > 0 && i < chestContents.length; i++) {
                // Can't clone it here, it could be null
                ItemStack item = chestContents[i];
                if (item != null && this.matches(item)) {
                    // Copy it, we don't want to interfere
                    item = item.clone();
                    // Amount = total, item.getAmount() = how many items in the
                    // stack
                    final int stackSize = Math.min(amount, item.getAmount());
                    // If Amount is item.getAmount(), then this sets the amount
                    // to 0
                    // Else it sets it to the remainder
                    chestContents[i].setAmount(chestContents[i].getAmount() - stackSize);
                    // We can modify this, it is a copy.
                    item.setAmount(stackSize);
                    // Add the items to the players inventory
                    floor.addAll(pInv.addItem(item).values());
                    amount -= stackSize;
                }
            }
            // We now have to update the chests inventory manually.
            this.getInventory().setContents(chestContents);
        }
        for (ItemStack aFloor : floor) {
            p.getWorld().dropItem(p.getLocation(), aFloor);
        }
    }

    /**
     * Changes the owner of this shop to the given player.
     *
     * @param owner
     *            The name of the owner. You must do shop.update() after to save
     *            it after a reboot.
     */
    @Override
    public void setOwner(final String owner) {
        this.owner = owner;
    }

    /**
     * Sets the price of the shop. Does not update it in the database. Use
     * shop.update() for that.
     *
     * @param price
     *            The new price of the shop.
     */
    @Override
    public void setPrice(final double price) {
        this.price = price;
    }

    /**
     * Changes a shop type to Buying or Selling. Also updates the signs nearby.
     *
     * @param shopType
     *            The new type (ShopType.BUYING or ShopType.SELLING)
     */
    @Override
    public void setShopType(final ShopType shopType) {
        this.shopType = shopType;
        this.setSignText();
    }

    /**
     * Updates signs attached to the shop
     */
    @Override
    public void setSignText() {
        if (!Util.isLoaded(this.getLocation())) { return; }
        final ContainerShop shop = this;
        // 1.9不能异步修改木牌
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                final String[] lines = new String[4];
                lines[0] = signTitle;
                if (shop.isBuying()) {
                    final int remsp = shop.getRemainingSpace();
                    lines[1] = MsgUtil.p("signs.buying", "" + (remsp == 10000 ? "无限" : remsp));
                }
                if (shop.isSelling()) {
                    final int remst = shop.getRemainingStock();
                    lines[1] = MsgUtil.p("signs.selling", "" + (remst == 10000 ? "无限" : remst));
                }
                lines[2] = Util.getNameForSign(shop.item);
                lines[3] = MsgUtil.p("signs.price", "" + shop.getPrice());
                shop.setSignText(lines);
            }
        });
    }

    /**
     * Changes all lines of text on a sign near the shop
     *
     * @param lines
     *            The array of lines to change. Index is line number.
     */
    @Override
    public void setSignText(final String[] lines) {
        if (!Util.isLoaded(this.getLocation())) { return; }
        final List<Sign> signs = this.getSigns();
        // 1.9不能异步修改木牌
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (final Sign sign : signs) {
                    for (int i = 0; i < lines.length; i++) {
                        sign.setLine(i, lines[i]);
                    }
                    sign.update();
                }
            }
        });
    }

    @Override
    public void setUnlimited(final boolean unlimited) {
        this.unlimited = unlimited;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("商店 " + (loc.getWorld() == null ? "世界尚未载入" : "坐标: " + loc.getWorld().getName()) + "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", "
                + loc.getBlockZ() + ")");
        sb.append(" 所有者: ").append(getOwner());
        if (isUnlimited()) {
            sb.append(" 无限模式: true");
        }
        sb.append(" 价格: ").append(getPrice());
        sb.append(" 物品: ").append(getItem().toString());
        return sb.toString();
    }

    /**
     * Upates the shop into the database.
     */
    @Override
    public void update() {
        final int x = this.getLocation().getBlockX();
        final int y = this.getLocation().getBlockY();
        final int z = this.getLocation().getBlockZ();
        final String world = this.getLocation().getWorld().getName();
        final int unlimited = this.isUnlimited() ? 1 : 0;
        final String q = "UPDATE shops SET owner = ?, itemConfig = ?, unlimited = ?, type = ?, price = ? WHERE x = ? AND y = ? and z = ? and world = ?";
        final String owner = this.getOwner();
		final String item = Util.serialize(this.getItem());
		double price = this.getPrice();
		// Async database execute
		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				try {
					plugin.getDB().execute(q, owner, item, unlimited, shopType.toID(), price, x, y, z, world);
				} catch (final Exception e) {
					plugin.getLogger().warning("无法保存商店到数据库!!!");
					plugin.getLogger().warning("错误信息: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
    }

    private void checkDisplay() {
        if (!plugin.getConfigManager().isDisplay()) { return; }
        if (getLocation().getWorld() == null) { return; // not loaded
        }
        final boolean trans = Util.isTransparent(getLocation().clone().add(0.5, 1.2, 0.5).getBlock().getType());
        if (trans && this.getDisplayItem() == null) {
            this.displayItem = DisplayItem.create(this);
            this.getDisplayItem().spawn();
            return;
        }
        if (this.getDisplayItem() != null) {
            if (!trans) { // We have a display item in a block... delete it
                this.getDisplayItem().remove();
                this.displayItem = null;
                return;
            }
            if (this.getLocation().getWorld() == null) { return;// Ignore if world not loaded...
            }
            final DisplayItem disItem = this.getDisplayItem();
            final Location dispLoc = disItem.getDisplayLocation();
            if (dispLoc.getWorld() == null) { return; }
            if (dispLoc.getBlock() != null && dispLoc.getBlock().getType() == Material.WATER) { // Flowing
                // water.Stationery water does not move items.
                disItem.remove();
                return;
            }
            if (disItem.getItem() == null) {
                disItem.removeDupe();
                disItem.spawn();
                return;
            }
            final Item item = disItem.getItem();
            if (item.getTicksLived() > 5000 || !item.isValid() || item.isDead()) {
                disItem.respawn();
                disItem.removeDupe();
            } else if (item.getLocation().distanceSquared(dispLoc) > 1) {
                item.teleport(dispLoc, TeleportCause.PLUGIN);
            }
        }
    }
}