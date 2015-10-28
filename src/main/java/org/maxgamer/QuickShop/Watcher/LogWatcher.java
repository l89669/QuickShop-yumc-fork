package org.maxgamer.QuickShop.Watcher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import org.bukkit.scheduler.BukkitTask;
import org.maxgamer.QuickShop.QuickShop;

public class LogWatcher implements Runnable {
	private PrintStream ps;
	private ArrayList<String> logs = new ArrayList<String>(5);
	public BukkitTask task;

	public LogWatcher(QuickShop plugin, File log) {
		try {
			if (!log.exists()) {
				log.createNewFile();
			}
			FileOutputStream fos = new FileOutputStream(log, true);
			this.ps = new PrintStream(fos);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			plugin.getLogger().severe("日志文件未找到!");
		} catch (IOException e) {
			e.printStackTrace();
			plugin.getLogger().severe("无法创建日志文件!");
		}
	}
 
	@Override
	public void run() {
		synchronized (logs) {
			for (String s : logs) {
				ps.println(s);
			}
			logs.clear();
		}
	}

	public void add(String s) {
		synchronized (logs) {
			logs.add(s);
		}
	}

	public void close() {
		this.ps.close();
	}
}