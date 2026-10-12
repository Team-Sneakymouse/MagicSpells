package com.nisovin.magicspells.spelleffects.effecttypes;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;

public class ParticlesPersonalEffect extends ParticlesEffect {

	protected ConfigData<ParticlePosition> target;

	@Override
	public void loadFromConfig(ConfigurationSection config) {
		super.loadFromConfig(config);

		target = ConfigDataUtil.getEnum(config, "target", ParticlePosition.class, ParticlePosition.POSITION);
	}

	@Override
	protected Runnable playEffectEntity(Entity entity, SpellData data) {
		Player player = getTarget(entity, data);
		if (player == null) return null;

		Location location = applyOffsets(entity.getLocation(), data);

		Particle particle = this.particle.get(data);
		particle.builder()
				.location(location)
				.count(count.get(data))
				.offset(xSpread.get(data), ySpread.get(data), zSpread.get(data))
				.extra(speed.get(data))
				.data(getParticleData(particle, location, data))
				.force(force)
				.receivers(player)
				.spawn();

		return null;
	}

	@Override
	public Runnable playEffectLocation(Location location, SpellData data) {
		Player player = getTarget(null, data);
		if (player == null) return null;

		Particle particle = this.particle.get(data);
		particle.builder()
				.location(location)
				.count(count.get(data))
				.offset(xSpread.get(data), ySpread.get(data), zSpread.get(data))
				.extra(speed.get(data))
				.data(getParticleData(particle, location, data))
				.force(force)
				.receivers(player)
				.spawn();

		return null;
	}

	private Player getTarget(Entity entity, SpellData data) {
		return switch (target.get(data)) {
			case CASTER -> {
				Entity caster = data.caster();
				yield caster instanceof Player player ? player : null;
			}
			case TARGET -> {
				Entity target = data.target();
				yield target instanceof Player player ? player : null;
			}
			case POSITION -> entity instanceof Player player ? player : null;
		};
	}

}
