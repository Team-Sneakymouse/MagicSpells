package com.nisovin.magicspells.spelleffects.effecttypes;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.EntityPdc;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.nisovin.magicspells.util.config.ConfigDataUtil;

public class FireworksEffect extends SpellEffect {

	private static final NamespacedKey FIREWORK_FLAG = EntityPdc.key(MagicSpells.FIREWORK_METADATA_KEY);


	private ConfigData<Integer> type;
	private ConfigData<Integer> flightDuration;

	private boolean trail;
	private boolean flicker;

	private int[] colors = new int[] { 0xFF0000 };
	private int[] fadeColors = new int[] { 0xFF0000 };

	@Override
	public void loadFromConfig(ConfigurationSection config) {
		type = ConfigDataUtil.getInteger(config, "type", 0);
		flightDuration = ConfigDataUtil.getInteger(config, "flight", 0);

		trail = config.getBoolean("trail", false);
		flicker = config.getBoolean("flicker", false);

		String[] c = config.getString("colors", "FF0000").replace(" ", "").split(",");
		if (c.length > 0) {
			colors = new int[c.length];
			for (int i = 0; i < colors.length; i++) {
				try {
					colors[i] = Integer.parseInt(c[i], 16);
				} catch (NumberFormatException e) {
					colors[i] = 0;
				}
			}
		}

		String[] fc = config.getString("fade-colors", "").replace(" ", "").split(",");
		if (fc.length > 0) {
			fadeColors = new int[fc.length];
			for (int i = 0; i < fadeColors.length; i++) {
				try {
					fadeColors[i] = Integer.parseInt(fc[i], 16);
				} catch (NumberFormatException e) {
					fadeColors[i] = 0;
				}
			}
		}
	}

	@Override
	public Runnable playEffectLocation(Location location, SpellData data) {
		FireworkEffect.Type t = FireworkEffect.Type.BALL;
		int type = this.type.get(data);
		if (type == 1) t = FireworkEffect.Type.BALL_LARGE;
		else if (type == 2) t = FireworkEffect.Type.STAR;
		else if (type == 3) t = FireworkEffect.Type.CREEPER;
		else if (type == 4) t = FireworkEffect.Type.BURST;

		Color[] c1 = new Color[colors.length];
		for (int i = 0; i < colors.length; i++) {
			c1[i] = Color.fromRGB(colors[i]);
		}
		Color[] c2 = new Color[fadeColors.length];
		for (int i = 0; i < fadeColors.length; i++) {
			c2[i] = Color.fromRGB(fadeColors[i]);
		}

		FireworkEffect effect = FireworkEffect.builder()
				.flicker(flicker)
				.trail(trail)
				.with(t)
				.withColor(c1)
				.withFade(c2)
				.build();
		Firework firework = location.getWorld().spawn(location, Firework.class);
		FireworkMeta meta = firework.getFireworkMeta();

		meta.addEffect(effect);
		meta.setPower(0);

		firework.setFireworkMeta(meta);
		firework.setSilent(true);
		EntityPdc.setFlag(firework, FIREWORK_FLAG);

		MagicSpells.scheduleDelayedTask(() -> {
			if (!firework.isValid()) return;
			if (firework.isDead()) return;
			firework.detonate();
		}, flightDuration.get(data));

		return null;
	}

}
