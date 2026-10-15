package com.nisovin.magicspells.spelleffects.effecttypes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Vibration.Destination;
import org.bukkit.Particle.DustTransition;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ConfigReaderUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.nisovin.magicspells.util.config.ConfigDataUtil;

import static org.bukkit.Vibration.Destination.*;

public class ParticlesEffect extends SpellEffect {

	protected ConfigData<Particle> particle;

	protected ConfigData<Material> material;
	protected ConfigData<BlockData> blockData;
	protected ConfigData<DustOptions> dustOptions;
	protected ConfigData<DustTransition> dustTransition;

	protected ConfigData<ParticlePosition> vibrationOrigin;
	protected ConfigData<ParticlePosition> vibrationDestination;

	protected Vector vibrationOffset;
	protected Vector vibrationRelativeOffset;

	protected ConfigData<Integer> count;
	protected ConfigData<Integer> radius;
	protected ConfigData<Integer> arrivalTime;
	protected ConfigData<Integer> shriekDelay;

	protected ConfigData<Float> speed;
	protected ConfigData<Float> xSpread;
	protected ConfigData<Float> ySpread;
	protected ConfigData<Float> zSpread;
	protected ConfigData<Float> sculkChargeRotation;

	protected boolean force;
	protected boolean staticDestination;

	@Override
	public void loadFromConfig(ConfigurationSection config) {
		particle = ConfigDataUtil.getParticle(config, "particle-name", Particle.EXPLOSION);

		material = ConfigDataUtil.getMaterial(config, "material", null);
		blockData = ConfigDataUtil.getBlockData(config, "material", null);
		dustOptions = ConfigDataUtil.getDustOptions(config, "color", "size", new DustOptions(Color.RED, 1));
		dustTransition = ConfigDataUtil.getDustTransition(config, "color", "to-color", "size", new DustTransition(Color.RED, Color.BLACK, 1));

		vibrationOrigin = ConfigDataUtil.getEnum(config, "vibration-origin", ParticlePosition.class, ParticlePosition.POSITION);
		vibrationDestination = ConfigDataUtil.getEnum(config, "vibration-destination", ParticlePosition.class, ParticlePosition.POSITION);

		vibrationOffset = ConfigReaderUtil.readVector(config.getString("vibration-offset", "0,0,0"));
		vibrationRelativeOffset = ConfigReaderUtil.readVector(config.getString("vibration-relative-offset", "0,0,0"));

		count = ConfigDataUtil.getInteger(config, "count", 5);
		radius = ConfigDataUtil.getInteger(config, "radius", 50);
		arrivalTime = ConfigDataUtil.getInteger(config, "arrival-time", -1);
		shriekDelay = ConfigDataUtil.getInteger(config, "shriek-delay", 0);

		speed = ConfigDataUtil.getFloat(config, "speed", 0.2f);

		ConfigData<Float> horizSpread = ConfigDataUtil.getFloat(config, "horiz-spread", 0.2f);
		xSpread = ConfigDataUtil.getFloat(config, "x-spread", horizSpread);
		zSpread = ConfigDataUtil.getFloat(config, "z-spread", horizSpread);
		sculkChargeRotation = ConfigDataUtil.getFloat(config, "sculk-charge-rotation", 0);

		ConfigData<Float> vertSpread = ConfigDataUtil.getFloat(config, "vert-spread", 0.2f);
		ySpread = ConfigDataUtil.getFloat(config, "y-spread", vertSpread);

		force = config.getBoolean("force", false);
		staticDestination = config.getBoolean("static-destination", false);
	}

	@Override
	protected Runnable playEffectEntity(Entity entity, SpellData data) {
		Location location = applyOffsets(entity.getLocation(), data);

		Particle particle = this.particle.get(data);
		Object particleData = getParticleData(particle, entity, location, data);

		location.getWorld().spawnParticle(particle, location, count.get(data), xSpread.get(data), ySpread.get(data), zSpread.get(data), speed.get(data), particleData, force);

		return null;
	}

	@Override
	public Runnable playEffectLocation(Location location, SpellData data) {
		Particle particle = this.particle.get(data);

		particle.builder()
				.location(location)
				.count(count.get(data))
				.offset(xSpread.get(data), ySpread.get(data), zSpread.get(data))
				.extra(speed.get(data))
				.data(getParticleData(particle, location, data))
				.force(force)
				.receivers(getReceivers(location, radius.get(data)))
				.spawn();

		return null;
	}

	private static List<Player> getReceivers(Location location, int radius) {
		BoundingBox bounds = BoundingBox.of(location, radius, radius, radius);
		List<Player> receivers = new ArrayList<>();
		for (Player player : location.getWorld().getPlayers()) {
			// Keep Paper's strict broad-phase overlap and inclusive sqrt distance (also for radius 0).
			if (bounds.overlaps(player.getBoundingBox()) && location.distance(player.getLocation()) <= radius)
				receivers.add(player);
		}
		// Never return null: ParticleBuilder interprets it as all world players.
		return receivers;
	}

	protected Object getParticleData(Particle particle, Entity entity, Location location, SpellData data) {
		Class<?> type = particle.getDataType();

		if (type == ItemStack.class) {
			Material material = this.material.get(data);
			return material == null ? null : new ItemStack(material);
		}

		if (type == Vibration.class) {
			Location originLocation = getLocation(location, data, vibrationOrigin);
			if (originLocation == null) return null;

			Destination destination = switch (vibrationDestination.get(data)) {
				case CASTER -> {
					LivingEntity caster = data == null ? null : data.caster();
					if (caster == null) yield null;

					yield staticDestination ? new BlockDestination(applyOffsets(caster.getLocation(), vibrationOffset,
						vibrationRelativeOffset, 0, 0, 0, 0, 0)) : new EntityDestination(caster);
				}
				case TARGET -> {
					LivingEntity target = data == null ? null : data.target();
					if (target == null) yield null;

					yield staticDestination ? new BlockDestination(applyOffsets(target.getLocation(), vibrationOffset,
						vibrationRelativeOffset, 0, 0, 0, 0, 0)) : new EntityDestination(target);
				}
				case POSITION -> staticDestination ? new BlockDestination(applyOffsets(location, vibrationOffset,
					vibrationRelativeOffset, 0, 0, 0, 0, 0)) : new EntityDestination(entity);
			};
			if (destination == null) return null;

			return new Vibration(destination, arrivalTime.get(data));
		}

		if (type == BlockData.class) return blockData.get(data);
		if (type == DustOptions.class) return dustOptions.get(data);
		if (type == DustTransition.class) return dustTransition.get(data);
		if (type == Float.class) return sculkChargeRotation.get(data);
		if (type == Integer.class) return shriekDelay.get(data);

		return null;
	}

	protected Object getParticleData(Particle particle, Location location, SpellData data) {
		Class<?> type = particle.getDataType();

		if (type == ItemStack.class) {
			Material material = this.material.get(data);
			return material == null ? null : new ItemStack(material);
		}

		if (type == Vibration.class) {
			Location originLocation = getLocation(location, data, vibrationOrigin);
			if (originLocation == null) return null;

			Location targetLocation = getLocation(location, data, vibrationDestination);
			if (targetLocation == null) return null;

			Destination destination = new BlockDestination(applyOffsets(targetLocation, vibrationOffset,
				vibrationRelativeOffset, 0, 0, 0, 0, 0));

			return new Vibration(destination, arrivalTime.get(data));
		}

		if (type == BlockData.class) return blockData.get(data);
		if (type == DustOptions.class) return dustOptions.get(data);
		if (type == DustTransition.class) return dustTransition.get(data);

		return null;
	}

	protected Location getLocation(Location location, SpellData data, ConfigData<ParticlePosition> position) {
		return switch (position.get(data)) {
			case CASTER -> {
				LivingEntity caster = data == null ? null : data.caster();
				yield caster == null ? null : caster.getLocation();
			}
			case TARGET -> {
				LivingEntity target = data == null ? null : data.target();
				yield target == null ? null : target.getLocation();
			}
			case POSITION -> location.clone();
		};
	}

	protected enum ParticlePosition {

		CASTER,
		TARGET,
		POSITION

	}

}
