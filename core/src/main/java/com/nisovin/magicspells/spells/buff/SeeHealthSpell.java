package com.nisovin.magicspells.spells.buff;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastData;
import com.nisovin.magicspells.util.ColorUtil;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.config.ConfigData;

public class SeeHealthSpell extends BuffSpell {

	private final static String COLORS = "01234567890abcdef";

	private final Map<UUID, CastData> players;

	private ConfigData<Integer> barSize;
	private int interval;

	private String symbol;

	private Updater updater;

	public SeeHealthSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		barSize = getConfigDataInt("bar-size", 20);
		interval = getConfigInt("update-interval", 5);
		symbol = getConfigString("symbol", "=");

		players = new HashMap<>();
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		if (!(entity instanceof Player)) return false;
		players.put(entity.getUniqueId(), new CastData(power, args));

		if (updater == null) updater = new Updater();
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return players.containsKey(entity.getUniqueId());
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		players.remove(entity.getUniqueId());

		if (updater != null && players.isEmpty()) {
			updater.stop();
			updater = null;
		}
	}

	@Override
	protected void turnOff() {
		for (UUID id : players.keySet()) {
			Player player = Bukkit.getPlayer(id);
			if (player == null) continue;
			if (!player.isValid()) continue;
			player.updateInventory();
		}

		players.clear();
		if (updater != null) {
			updater.stop();
			updater = null;
		}
	}

	private String getRandomColor() {
		return ColorUtil.sectionCode(COLORS.charAt(random.nextInt(COLORS.length())));
	}

	private void showHealthBar(Player player, LivingEntity entity, CastData data) {
		double pct = entity.getHealth() / Util.getMaxHealth(entity);

		String color = ColorUtil.GREEN;
		if (pct <= 0.2) color = ColorUtil.DARK_RED;
		else if (pct <= 0.4) color = ColorUtil.RED;
		else if (pct <= 0.6) color = ColorUtil.GOLD;
		else if (pct <= 0.8) color = ColorUtil.YELLOW;

		int barSize = this.barSize.get(player, entity, data.power(), data.args());

		StringBuilder sb = new StringBuilder(barSize);
		sb.append(getRandomColor());
		int remain = (int) Math.round(barSize * pct);
		sb.append(color);
		sb.append(symbol.repeat(remain));

		if (remain < barSize) {
			sb.append(ColorUtil.DARK_GRAY);
			sb.append(symbol.repeat(barSize - remain));
		}

		player.sendActionBar(Util.getMiniMessage(sb.toString()));
	}

	public static String getColors() {
		return COLORS;
	}

	public Map<UUID, CastData> getPlayers() {
		return players;
	}

	public int getInterval() {
		return interval;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	private class Updater implements Runnable {

		private final int taskId;

		private Updater() {
			taskId = MagicSpells.scheduleRepeatingTask(this, 0, interval);
		}

		@Override
		public void run() {
			for (Map.Entry<UUID, CastData> entry : players.entrySet()) {
				UUID id = entry.getKey();
				Player player = Bukkit.getPlayer(id);
				if (player == null || !player.isValid()) continue;

				CastData data = entry.getValue();
				TargetInfo<LivingEntity> target = getTargetedEntity(player, data.power(), data.args());
				if (!target.noTarget()) showHealthBar(player, target.target(), data);
			}
		}

		public void stop() {
			MagicSpells.cancelTask(taskId);
		}

	}

}
