package org.maxgamer.QuickShop;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.maxgamer.QuickShop.Command.QuickShopCommands;
import org.maxgamer.QuickShop.Config.ConfigManager;
import org.maxgamer.QuickShop.Database.Database;
import org.maxgamer.QuickShop.Database.DatabaseCore;
import org.maxgamer.QuickShop.Database.DatabaseHelper;
import org.maxgamer.QuickShop.Database.MySQLCore;
import org.maxgamer.QuickShop.Database.SQLiteCore;
import org.maxgamer.QuickShop.Economy.Economy;
import org.maxgamer.QuickShop.Economy.EconomyCore;
import org.maxgamer.QuickShop.Economy.Economy_Vault;
import org.maxgamer.QuickShop.Listeners.BlockListener;
import org.maxgamer.QuickShop.Listeners.ChatListener;
import org.maxgamer.QuickShop.Listeners.LockListener;
import org.maxgamer.QuickShop.Listeners.PlayerListener;
import org.maxgamer.QuickShop.Listeners.ProtectListener;
import org.maxgamer.QuickShop.Listeners.WorldListener;
import org.maxgamer.QuickShop.Shop.ContainerShop;
import org.maxgamer.QuickShop.Shop.Shop;
import org.maxgamer.QuickShop.Shop.ShopManager;
import org.maxgamer.QuickShop.Shop.ShopType;
import org.maxgamer.QuickShop.Util.MsgUtil;
import org.maxgamer.QuickShop.Util.Util;
import org.maxgamer.QuickShop.Watcher.LogWatcher;

import pw.yumc.YumCore.config.FileConfig;
import pw.yumc.YumCore.global.L10N;
import pw.yumc.YumCore.statistic.Statistics;
import pw.yumc.YumCore.update.SubscribeTask;

public class QuickShop extends JavaPlugin {
    /** 初始化 QuickShop 的接口 */
    public static QuickShop instance;
    /** 插件的配置文件 */
    public FileConfig config;
    // private HeroChatListener heroChatListener;
    // Listeners (These don't)
    /** The Config Manager used to read config */
    private ConfigManager configManager;
    /** The database for storing all our data for persistence */
    private Database database;
    /** The economy we hook into for transactions */
    private Economy economy;
    public BukkitTask itemWatcherTask;
    private LogWatcher logWatcher;
    /** The Shop Manager used to store shops */
    private ShopManager shopManager;

    /**
     * Prints debug information if QuickShop is configured to do so.
     *
     * @param s
     *            The string to print.
     */
    public void debug(final String s) {
        if (!configManager.isDebug()) { return; }
        this.getLogger().info(ChatColor.YELLOW + "[Debug] " + s);
    }

    @Override
    public FileConfiguration getConfig() {
        return config;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * @return Returns the database handler for queries etc.
     */
    public Database getDB() {
        return this.database;
    }

    /**
     * Returns the economy for moving currency around
     *
     * @return The economy for moving currency around
     */
    public EconomyCore getEcon() {
        return economy;
    }

    public int getShopLimit(final Player p) {
        int max = configManager.getLimitdefault();
        for (final Entry<String, Integer> entry : configManager.getLimits().entrySet()) {
            if (entry.getValue() > max && p.hasPermission(entry.getKey())) {
                max = entry.getValue();
            }
        }
        return max;
    }

    /**
     * Returns the ShopManager. This is used for fetching, adding and removing
     * shops.
     *
     * @return The ShopManager.
     */
    public ShopManager getShopManager() {
        return this.shopManager;
    }

    /**
     * Tries to load the economy and its core. If this fails, it will try to use
     * vault. If that fails, it will return false.
     *
     * @return true if successful, false if the core is invalid or is not found,
     *         and vault cannot be used.
     */
    public boolean loadEcon() {
        final EconomyCore core = new Economy_Vault();
        if (!core.isValid()) {
            getLogger().warning("无法找到经济管理类插件...");
            getLogger().warning("卸载插件!!!");
            this.getPluginLoader().disablePlugin(this);
            return false;
        }
        this.economy = new Economy(core);
        return true;
    }

    public void loadShop() {
        /* 从数据库载入商店信息到内存 */
        int count = 0; // 商店个数
        int unload = 0;
        Connection con;
        try {
            getLogger().info("开始从数据库载入商店数据...");
            con = database.getConnection();
            final PreparedStatement ps = con.prepareStatement("SELECT * FROM shops");
            final ResultSet rs = ps.executeQuery();
            int errors = 0;
            while (rs.next()) {
                int x = 0;
                int y = 0;
                int z = 0;
                String worldName = null;
                try {
                    x = rs.getInt("x");
                    y = rs.getInt("y");
                    z = rs.getInt("z");
                    worldName = rs.getString("world");
                    final World world = Bukkit.getWorld(worldName);
                    final ItemStack item = Util.deserialize(rs.getString("itemConfig"));
                    final String owner = rs.getString("owner");
                    final double price = rs.getDouble("price");
                    final Location loc = new Location(world, x, y, z);
                    /* Skip invalid shops, if we know of any */
                    if (world != null && loc.getChunk().isLoaded() && !(loc.getBlock().getState() instanceof InventoryHolder)) {
                        getLogger().info("商店不是一个可存储的方块 坐标 " + rs.getString("world") + ", " + x + ", " + y + ", " + z + ". 删除...");
                        database.execute("DELETE FROM shops WHERE x = ? AND y = ? and z = ? and world = ?", x, y, z, worldName);
                        continue;
                    }
                    final int type = rs.getInt("type");
                    final Shop shop = new ContainerShop(loc, price, item, owner);
                    shop.setUnlimited(rs.getBoolean("unlimited"));
                    shop.setShopType(ShopType.fromID(type));
                    shopManager.loadShop(rs.getString("world"), shop);
                    if (loc.getWorld() != null && loc.getChunk().isLoaded()) {
                        shop.onLoad();
                    }
                    count++;
                } catch (final IllegalStateException e) {
                    unload++;
                } catch (final Exception e) {
                    errors++;
                    e.printStackTrace();
                    getLogger().warning("载入商店数据时发生错误! 商店位置: " + worldName + " (" + x + ", " + y + ", " + z + ")...");
                    if (errors < 3) {
                        getLogger().warning("删除错误的商店数据...");
                        database.execute("DELETE FROM shops WHERE x = ? AND y = ? and z = ? and world = ?", x, y, z, worldName);
                    } else {
                        getLogger().warning("过多的错误数据 可能您的数据库文件已损坏! 请检查数据库文件!");
                        e.printStackTrace();
                        break;
                    }
                }
            }
        } catch (final SQLException e) {
            getLogger().warning("无法载入商店数据...");
            getLogger().warning("错误信息: " + e.getMessage());
            e.printStackTrace();
        }
        getLogger().info("已载入 " + count + " 个商店 剩余 " + unload + " 个商店将在区块载入后加载...");
    }

    /**
     * Logs the given string to qs.log, if QuickShop is configured to do so.
     *
     * @param s
     *            The string to log. It will be prefixed with the date and time.
     */
    public void log(final String s) {
        if (this.logWatcher == null) { return; }
        final Date date = Calendar.getInstance().getTime();
        final Timestamp time = new Timestamp(date.getTime());
        this.logWatcher.add("[" + time.toString() + "] " + s);
    }

    @Override
    public void onDisable() {
        if (itemWatcherTask != null) {
            itemWatcherTask.cancel();
        }
        if (logWatcher != null) {
            logWatcher.task.cancel();
            logWatcher.close(); // Closes the file
        }
        /* Remove all display items, and any dupes we can find */
        if (shopManager != null) {
            shopManager.clear();
        }
        /* Empty the buffer */
        if (database != null) {
            database.close();
            try {
                database.getConnection().close();
            } catch (final SQLException ignored) {
            }
        }
        if (configManager != null) {
            configManager.getWarnings().clear();
        }
    }

    @Override
    public void onEnable() {
        if (!loadEcon()) { return; }
        Util.initialize();
        // Create the shop manager.
        shopManager = new ShopManager(this);
        configManager = new ConfigManager(this);
        // Initialize Util
        if (configManager.isLogAction()) {
            // Logger Handler
            this.logWatcher = new LogWatcher(this, new File(this.getDataFolder(), "qs.log"));
            logWatcher.task = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this.logWatcher, 150, 150);
        }
        if (configManager.isShopLock()) {
            final LockListener ll = new LockListener(this);
            getServer().getPluginManager().registerEvents(ll, this);
        }
        try {
            final ConfigurationSection dbCfg = getConfig().getConfigurationSection("database");
            DatabaseCore dbCore;
            if (dbCfg != null && dbCfg.getBoolean("mysql")) {
                getLogger().info("启用MySQL 开始连接数据库...");
                // MySQL database - Required database be created first.
                final String user = dbCfg.getString("user");
                final String pass = dbCfg.getString("password");
                final String host = dbCfg.getString("host");
                final String port = dbCfg.getString("port");
                final String database = dbCfg.getString("database");
                dbCore = new MySQLCore(host, user, pass, database, port);
            } else {
                // SQLite database - Doing this handles file creation
                dbCore = new SQLiteCore(new File(this.getDataFolder(), "shops.db"));
            }
            this.database = new Database(dbCore);
            // Make the database up to date
            DatabaseHelper.setup(getDB());
        } catch (final Exception e) {
            getLogger().warning("数据库连接错误或配置错误...");
            getLogger().warning("错误信息: " + e.getMessage());
            e.printStackTrace();
            getLogger().warning("关闭插件!!!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadShop();
        MsgUtil.loadTransactionMessages();
        MsgUtil.clean();
        // Register events
        final PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new BlockListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new ProtectListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        // Command handlers
        new QuickShopCommands();

        if (configManager.getFindDistance() > 100) {
            getLogger().warning("商店查找半径过大 可能导致服务器Lag! 推荐使用低于 100 的配置!");
        }
        this.getLogger().info("载入完成! 版本: " + this.getDescription().getVersion() + " 重制 by 喵♂呜");
        new Statistics();
        new SubscribeTask();
    }

    @Override
    public void onLoad() {
        instance = this;
        config = new FileConfig();
        MsgUtil.init(this);
        L10N.getItemName(new ItemStack(Material.AIR));
    }

    /** Reloads QuickShops config */
    @Override
    public void reloadConfig() {
        config.reload();
        L10N.reload();
    }
}