package com.nisovin.magicspells.spelleffects.effecttypes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;

public class SoundPersonalEffect extends SoundEffect {

	private ConfigData<SoundTarget> target;

	private boolean broadcast;
	private boolean useListenerAsTarget;
	private boolean resolveSoundPerPlayer;
	private boolean resolvePitchPerPlayer;
	private boolean resolveVolumePerPlayer;
	private boolean resolveCategoryPerPlayer;

	@Override
	public void loadFromConfig(ConfigurationSection config) {
		super.loadFromConfig(config);

		target = ConfigDataUtil.getEnum(config, "target", SoundTarget.class, SoundTarget.POSITION);

		broadcast = config.getBoolean("broadcast", false);
		useListenerAsTarget = config.getBoolean("use-listener-as-target", false);
		resolveSoundPerPlayer = config.getBoolean("resolve-sound-per-player", false);
		resolvePitchPerPlayer = config.getBoolean("resolve-pitch-per-player", false);
		resolveVolumePerPlayer = config.getBoolean("resolve-volume-per-player", false);
		resolveCategoryPerPlayer = config.getBoolean("resolve-category-per-player", false);
	}

	@Override
	public Runnable playEffectEntity(Entity entity, SpellData data) {
		if (broadcast) {
			broadcast(data);
			return null;
		}

		Player target = getTarget(entity, data);
		if (target != null) {
			if (useListenerAsTarget && data != null)
				data = new SpellData(data.caster(), target, data.power(), data.args());
			target.playSound(applyOffsets(entity.getLocation(), data), sound.get(data), category.get(data),
					volume.get(data), pitch.get(data));
		}

		return null;
	}

	@Override
	public Runnable playEffectLocation(Location location, SpellData data) {
		if (broadcast) {
			broadcast(data);
			return null;
		}

		Player target = getTarget(null, data);
		if (target != null) {
			if (useListenerAsTarget && data != null)
				data = new SpellData(data.caster(), target, location, data.power(), data.args());
			target.playSound(location, sound.get(data), category.get(data), volume.get(data), pitch.get(data));
		}

		return null;
	}

	private Player getTarget(Entity entity, SpellData data) {
		return switch (target.get(data)) {
			case CASTER -> {
				if (data == null)
					yield null;

				Entity caster = data.caster();
				yield caster instanceof Player player ? player : null;
			}
			case TARGET -> {
				if (data == null)
					yield null;

				Entity target = data.target();
				yield target instanceof Player player ? player : null;
			}
			case POSITION -> entity instanceof Player player ? player : null;
		};
	}

	private void broadcast(SpellData data) {
		float pitch = resolvePitchPerPlayer ? 0 : this.pitch.get(data);
		float volume = resolveVolumePerPlayer ? 0 : this.volume.get(data);
		String sound = resolveSoundPerPlayer ? null : this.sound.get(data);
		SoundCategory category = resolveCategoryPerPlayer ? null : this.category.get(data);

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (useListenerAsTarget && data != null)
				data = new SpellData(data.caster(), player, data.location(), data.power(), data.args());

			if (resolveSoundPerPlayer)
				sound = this.sound.get(data);
			if (resolvePitchPerPlayer)
				pitch = this.pitch.get(data);
			if (resolveVolumePerPlayer)
				volume = this.volume.get(data);
			if (resolveCategoryPerPlayer)
				category = this.category.get(data);

			if (sound == null || category == null)
				continue;
			player.playSound(player.getLocation(), sound, category, pitch, volume);
		}
	}

	private enum SoundTarget {

		CASTER,
		TARGET,
		POSITION

	}

}
