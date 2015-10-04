package org.maxgamer.QuickShop.Util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.maxgamer.QuickShop.QuickShop;
import org.maxgamer.QuickShop.Shop.Shop;

import cn.citycraft.PluginHelper.config.FileConfig;
import mkremins.fanciful.FancyMessage;

public class MsgUtil {
	private static QuickShop plugin;
	private static FileConfig messages;
	private static HashMap<UUID, LinkedList<String>> player_messages = new HashMap<UUID, LinkedList<String>>();

	static {
		plugin = QuickShop.instance;
	}

	/**
	 * Deletes any messages that are older than a week in the database, to save
	 * on space.
	 */
	public static void clean() {
		System.out.println("Cleaning purchase messages from database that are over a week old...");
		// 604800,000 msec = 1 week.
		final long weekAgo = System.currentTimeMillis() - 604800000;
		plugin.getDB().execute("DELETE FROM messages WHERE time < ?", weekAgo);
	}

	/**
	 * Empties the queue of messages a player has and sends them to the player.
	 *
	 * @param p
	 *            The player to message
	 * @return true if success, false if the player is offline or null
	 */
	public static boolean flush(final OfflinePlayer p) { // TODO Changed to UUID
		if (p != null && p.isOnline()) {
			final UUID pName = p.getUniqueId();
			final LinkedList<String> msgs = player_messages.get(pName);
			if (msgs != null) {
				for (final String msg : msgs) {
					p.getPlayer().sendMessage(msg);
				}
				plugin.getDB().execute("DELETE FROM messages WHERE owner = ?", pName.toString());
				msgs.clear();
			}
			return true;
		}
		return false;
	}

	/**
	 * Loads all the messages from messages.yml
	 */
	public static void loadCfgMessages() {
		// Load messages.yml
		messages = new FileConfig(plugin, "messages.yml");
		// Parse colour codes
		Util.parseColours(messages);
	}

	/**
	 * loads all player purchase messages from the database.
	 */
	public static void loadTransactionMessages() { // TODO Converted to UUID
		player_messages.clear(); // Delete old messages
		try {
			final ResultSet rs = plugin.getDB().getConnection().prepareStatement("SELECT * FROM messages").executeQuery();
			while (rs.next()) {
				final UUID owner = UUID.fromString(rs.getString("owner"));
				final String message = rs.getString("message");
				LinkedList<String> msgs = player_messages.get(owner);
				if (msgs == null) {
					msgs = new LinkedList<String>();
					player_messages.put(owner, msgs);
				}
				msgs.add(message);
			}
		} catch (final SQLException e) {
			e.printStackTrace();
			System.out.println("Could not load transaction messages from database. Skipping.");
		}
	}

	public static String p(final String loc, final String... args) {
		String raw = messages.getString(loc);
		if (raw == null || raw.isEmpty()) {
			return "Invalid message: " + loc;
		}
		if (args == null) {
			return raw;
		}
		for (int i = 0; i < args.length; i++) {
			raw = raw.replace("{" + i + "}", args[i] == null ? "null" : args[i]);
		}
		return raw;
	}

	/**
	 * @param player
	 *            The name of the player to message
	 * @param message
	 *            The message to send them Sends the given player a message if
	 *            they're online. Else, if they're not online, queues it for
	 *            them in the database.
	 */
	public static void send(final UUID player, final String message) { // TODO Converted to UUID
		final OfflinePlayer p = Bukkit.getOfflinePlayer(player);
		if (p == null || !p.isOnline()) {
			LinkedList<String> msgs = player_messages.get(player);
			if (msgs == null) {
				msgs = new LinkedList<String>();
				player_messages.put(player, msgs);
			}
			msgs.add(message);
			final String q = "INSERT INTO messages (owner, message, time) VALUES (?, ?, ?)";
			plugin.getDB().execute(q, player.toString(), message, System.currentTimeMillis());
		} else {
			p.getPlayer().sendMessage(message);
		}
	}

	public static void sendPurchaseSuccess(final Player p, final Shop shop, final int amount) {
		p.sendMessage(ChatColor.DARK_PURPLE + "+---------------------------------------------------+");
		p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.successful-purchase"));
		p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.item-name-and-price", "" + amount, shop.getDataName(), Util.format((amount * shop.getPrice()))));
		Map<Enchantment, Integer> enchs = shop.getItem().getItemMeta().getEnchants();
		if (enchs != null && !enchs.isEmpty()) {
			p.sendMessage(ChatColor.DARK_PURPLE + "+--------------------" + MsgUtil.p("menu.enchants") + "-----------------------+");
			for (final Entry<Enchantment, Integer> entries : enchs.entrySet()) {
				p.sendMessage(ChatColor.DARK_PURPLE + "| " + ChatColor.YELLOW + entries.getKey().getName() + " " + entries.getValue());
			}
		}
		enchs = shop.getItem().getItemMeta().getEnchants();
		if (enchs != null && !enchs.isEmpty()) {
			p.sendMessage(ChatColor.DARK_PURPLE + "+-----------------" + MsgUtil.p("menu.stored-enchants") + "--------------------+");
			for (final Entry<Enchantment, Integer> entries : enchs.entrySet()) {
				p.sendMessage(ChatColor.DARK_PURPLE + "| " + ChatColor.YELLOW + entries.getKey().getName() + " " + entries.getValue());
			}
		}
		try {
			Class.forName("org.bukkit.inventory.meta.EnchantmentStorageMeta");
			if (shop.getItem().getItemMeta() instanceof EnchantmentStorageMeta) {
				final EnchantmentStorageMeta stor = (EnchantmentStorageMeta) shop.getItem().getItemMeta();
				stor.getStoredEnchants();
				enchs = stor.getStoredEnchants();
				if (enchs != null && !enchs.isEmpty()) {
					p.sendMessage(ChatColor.DARK_PURPLE + "+-----------------" + MsgUtil.p("menu.stored-enchants") + "--------------------+");
					for (final Entry<Enchantment, Integer> entries : enchs.entrySet()) {
						p.sendMessage(ChatColor.DARK_PURPLE + "| " + ChatColor.YELLOW + entries.getKey().getName() + " " + entries.getValue());
					}
				}
			}
		} catch (final ClassNotFoundException e) {
			// They don't have an up to date enough build of CB to do this.
			// TODO: Remove this when it becomes redundant
		}
		p.sendMessage(ChatColor.DARK_PURPLE + "+---------------------------------------------------+");
	}

	public static void sendSellSuccess(final Player p, final Shop shop, final int amount) {
		p.sendMessage(ChatColor.DARK_PURPLE + "+---------------------------------------------------+");
		p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.successfully-sold"));
		p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.item-name-and-price", "" + amount, shop.getDataName(), Util.format((amount * shop.getPrice()))));
		if (plugin.getConfig().getBoolean("show-tax")) {
			final double tax = plugin.getConfig().getDouble("tax");
			final double total = amount * shop.getPrice();
			if (tax != 0) {
				if (!p.getUniqueId().equals(shop.getOwner())) {
					p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.sell-tax", "" + Util.format((tax * total))));
				} else {
					p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.sell-tax-self"));
				}
			}
		}
		Map<Enchantment, Integer> enchs = shop.getItem().getItemMeta().getEnchants();
		if (enchs != null && !enchs.isEmpty()) {
			p.sendMessage(ChatColor.DARK_PURPLE + "+--------------------" + MsgUtil.p("menu.enchants") + "-----------------------+");
			for (final Entry<Enchantment, Integer> entries : enchs.entrySet()) {
				p.sendMessage(ChatColor.DARK_PURPLE + "| " + ChatColor.YELLOW + entries.getKey().getName() + " " + entries.getValue());
			}
		}
		try {
			Class.forName("org.bukkit.inventory.meta.EnchantmentStorageMeta");
			if (shop.getItem().getItemMeta() instanceof EnchantmentStorageMeta) {
				final EnchantmentStorageMeta stor = (EnchantmentStorageMeta) shop.getItem().getItemMeta();
				stor.getStoredEnchants();
				enchs = stor.getStoredEnchants();
				if (enchs != null && !enchs.isEmpty()) {
					p.sendMessage(ChatColor.DARK_PURPLE + "+--------------------" + MsgUtil.p("menu.stored-enchants") + "-----------------------+");
					for (final Entry<Enchantment, Integer> entries : enchs.entrySet()) {
						p.sendMessage(ChatColor.DARK_PURPLE + "| " + ChatColor.YELLOW + entries.getKey().getName() + " " + entries.getValue());
					}
				}
			}
		} catch (final ClassNotFoundException e) {
			// They don't have an up to date enough build of CB to do this.
			// TODO: Remove this when it becomes redundant
		}
		p.sendMessage(ChatColor.DARK_PURPLE + "+---------------------------------------------------+");
	}

	public static void sendShopInfo(final Player p, final Shop shop) {
		sendShopInfo(p, shop, shop.getRemainingStock());
	}

	public static void sendShopInfo(final Player p, final Shop shop, final int stock) {
		// Potentially faster with an array?
		final ItemStack items = shop.getItem();
		p.sendMessage("");
		p.sendMessage("");
		p.sendMessage(ChatColor.DARK_PURPLE + "+---------------------------------------------------+");
		p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.shop-information"));
		p.sendMessage(ChatColor.DARK_PURPLE + "| "
				+ MsgUtil.p("menu.owner", Bukkit.getOfflinePlayer(shop.getOwner()).getName() == null ? (shop.isUnlimited() ? "系统商店" : "未知") : Bukkit.getOfflinePlayer(shop.getOwner()).getName()));
		final FancyMessage fm = new FancyMessage();
		fm.text(ChatColor.DARK_PURPLE + "| ").then(MsgUtil.p("menu.item", shop.getDataName())).itemTooltip(items).send(p);
		if (Util.isTool(items.getType())) {
			p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.damage-percent-remaining", Util.getToolPercentage(items)));
		}
		if (shop.isSelling()) {
			p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.stock", "" + stock));
		} else {
			final int space = shop.getRemainingSpace();
			p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.space", "" + space));
		}
		p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.price-per", shop.getDataName(), Util.format(shop.getPrice())));
		if (shop.isBuying()) {
			p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.this-shop-is-buying"));
		} else {
			p.sendMessage(ChatColor.DARK_PURPLE + "| " + MsgUtil.p("menu.this-shop-is-selling"));
		}
		// Map<Enchantment, Integer> enchs = items.getItemMeta().getEnchants();
		// if (enchs != null && !enchs.isEmpty()) {
		// p.sendMessage(ChatColor.DARK_PURPLE + "+--------------------" + MsgUtil.getMessage("menu.enchants") + "-----------------------+");
		// for (final Entry<Enchantment, Integer> entries : enchs.entrySet()) {
		// p.sendMessage(ChatColor.DARK_PURPLE + "| " + ChatColor.YELLOW + entries.getKey().getName() + " " + entries.getValue());
		// }
		// }
		// try {
		// Class.forName("org.bukkit.inventory.meta.EnchantmentStorageMeta");
		// if (items.getItemMeta() instanceof EnchantmentStorageMeta) {
		// final EnchantmentStorageMeta stor = (EnchantmentStorageMeta) items.getItemMeta();
		// stor.getStoredEnchants();
		// enchs = stor.getStoredEnchants();
		// if (enchs != null && !enchs.isEmpty()) {
		// p.sendMessage(ChatColor.DARK_PURPLE + "+-----------------" + MsgUtil.getMessage("menu.stored-enchants") + "--------------------+");
		// for (final Entry<Enchantment, Integer> entries : enchs.entrySet()) {
		// p.sendMessage(ChatColor.DARK_PURPLE + "| " + ChatColor.YELLOW + entries.getKey().getName() + " " + entries.getValue());
		// }
		// }
		// }
		// } catch (final ClassNotFoundException e) {
		// // They don't have an up to date enough build of CB to do this.
		// // TODO: Remove this when it becomes redundant
		// }
		p.sendMessage(ChatColor.DARK_PURPLE + "+---------------------------------------------------+");
	}
}