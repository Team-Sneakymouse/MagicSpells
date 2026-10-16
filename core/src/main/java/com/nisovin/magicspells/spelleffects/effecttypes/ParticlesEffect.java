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
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.data.BlockData;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Particle.Trail;
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

	protected ConfigData<Color> color;
	protected ConfigData<Color> argbColor;

	protected ConfigData<Material> material;
	protected ConfigData<BlockData> blockData;
	protected ConfigData<DustOptions> dustOptions;
	protected ConfigData<Particle.Spell> spellOptions;
	protected ConfigData<DustTransition> dustTransition;

	protected ConfigData<ParticlePosition> vibrationOrigin;
	protected ConfigData<ParticlePosition> vibrationDestination;

	protected Vector vibrationOffset;
	protected Vector vibrationRelativeOffset;

	protected ConfigData<Color> trailColor;
	protected ConfigData<Integer> trailDuration;
	protected ConfigData<Vector> trailTargetOffset;
	protected ConfigData<ParticlePosition> trailOrigin;
	protected ConfigData<ParticlePosition> trailTarget;
	protected ConfigData<Vector> trailTargetRelativeOffset;

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

		// ENTITY_EFFECT needs ARGB; FLASH / TINTED_LEAVES need RGB. Fall back argb → color → red.
		color = ConfigDataUtil.getColor(config, "color", Color.RED);
		ConfigData<Color> configuredArgb = ConfigDataUtil.getARGBColor(config, "argb-color", null);
		argbColor = (caster, target, location, power, args) -> {
			Color c = configuredArgb.get(caster, target, location, power, args);
			return c != null ? c : color.get(caster, target, location, power, args);
		};

		material = ConfigDataUtil.getMaterial(config, "material", null);
		blockData = ConfigDataUtil.getBlockData(config, "material", null);
		dustOptions = ConfigDataUtil.getDustOptions(config, "color", "size", new DustOptions(Color.RED, 1));
		dustTransition = ConfigDataUtil.getDustTransition(config, "color", "to-color", "size", new DustTransition(Color.RED, Color.BLACK, 1));

		ConfigData<Particle.Spell> configuredSpell = ConfigDataUtil.getSpellOptions(config, "spell.color", "spell.power",
				new Particle.Spell(Color.RED, 1f));
		spellOptions = (caster, target, location, power, args) -> {
			Particle.Spell spell = configuredSpell.get(caster, target, location, power, args);
			if (spell != null) return spell;
			Color c = color.get(caster, target, location, power, args);
			return c == null ? null : new Particle.Spell(c, 1f);
		};

		vibrationOrigin = ConfigDataUtil.getEnum(config, "vibration-origin", ParticlePosition.class, ParticlePosition.POSITION);
		vibrationDestination = ConfigDataUtil.getEnum(config, "vibration-destination", ParticlePosition.class, ParticlePosition.POSITION);

		vibrationOffset = ConfigReaderUtil.readVector(config.getString("vibration-offset", "0,0,0"));
		vibrationRelativeOffset = ConfigReaderUtil.readVector(config.getString("vibration-relative-offset", "0,0,0"));

		ConfigData<Color> configuredTrailColor = ConfigDataUtil.getColor(config, "trail.color", null);
		trailColor = (caster, target, location, power, args) -> {
			Color c = configuredTrailColor.get(caster, target, location, power, args);
			return c != null ? c : color.get(caster, target, location, power, args);
		};
		trailOrigin = ConfigDataUtil.getEnum(config, "trail.origin", ParticlePosition.class, ParticlePosition.POSITION);
		trailTarget = ConfigDataUtil.getEnum(config, "trail.target", ParticlePosition.class, ParticlePosition.POSITION);
		trailDuration = ConfigDataUtil.getInteger(config, "trail.duration", 20);
		trailTargetOffset = ConfigDataUtil.getVector(config, "trail.target-offset", new Vector());
		trailTargetRelativeOffset = ConfigDataUtil.getVector(config, "trail.target-relative-offset", new Vector());

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
		Location spawnLocation = getSpawnLocation(particle, location, data);
		if (spawnLocation == null) return null;

		Object particleData = getParticleData(particle, entity, location, data);
		if (particle.getDataType() != Void.class && particleData == null) return null;

		spawnLocation.getWorld().spawnParticle(particle, spawnLocation, count.get(data), xSpread.get(data), ySpread.get(data), zSpread.get(data), speed.get(data), particleData, force);

		return null;
	}

	@Override
	public Runnable playEffectLocation(Location location, SpellData data) {
		Particle particle = this.particle.get(data);

		Location spawnLocation = getSpawnLocation(particle, location, data);
		if (spawnLocation == null) return null;

		Object particleData = getParticleData(particle, null, location, data);
		if (particle.getDataType() != Void.class && particleData == null) return null;

		particle.builder()
				.location(spawnLocation)
				.count(count.get(data))
				.offset(xSpread.get(data), ySpread.get(data), zSpread.get(data))
				.extra(speed.get(data))
				.data(particleData)
				.force(force)
				.receivers(getReceivers(spawnLocation, radius.get(data)))
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

	protected Object getParticleData(Particle particle, Location location, SpellData data) {
		return getParticleData(particle, null, location, data);
	}

	protected Object getParticleData(Particle particle, Entity entity, Location location, SpellData data) {
		Class<?> type = particle.getDataType();

		if (type == Color.class) return resolveColorData(particle, data);

		if (type == ItemStack.class) {
			Material material = this.material.get(data);
			return material == null ? null : new ItemStack(material);
		}

		if (type == Vibration.class) {
			Location originLocation = getLocation(location, data, vibrationOrigin);
			if (originLocation == null) return null;

			Destination destination = switch (vibrationDestination.get(data)) {
				case CASTER -> {
					Entity caster = data == null ? null : data.caster();
					if (caster == null) yield null;

					yield staticDestination ? new BlockDestination(applyOffsets(caster.getLocation(), vibrationOffset,
						vibrationRelativeOffset, 0, 0, 0, 0, 0)) : new EntityDestination(caster);
				}
				case TARGET -> {
					Entity target = data == null ? null : data.target();
					if (target == null) yield null;

					yield staticDestination ? new BlockDestination(applyOffsets(target.getLocation(), vibrationOffset,
						vibrationRelativeOffset, 0, 0, 0, 0, 0)) : new EntityDestination(target);
				}
				case POSITION -> entity == null || staticDestination
					? new BlockDestination(applyOffsets(location, vibrationOffset, vibrationRelativeOffset, 0, 0, 0, 0, 0))
					: new EntityDestination(entity);
			};
			if (destination == null) return null;

			return new Vibration(destination, arrivalTime.get(data));
		}

		if (type == Trail.class) {
			Color trailColor = this.trailColor.get(data);
			if (trailColor == null) return null;

			Integer duration = trailDuration.get(data);
			if (duration == null || duration <= 0) return null;

			Location target = getLocation(location, data, trailTarget);
			if (target == null) return null;

			Vector offset = trailTargetOffset.get(data);
			Vector relativeOffset = trailTargetRelativeOffset.get(data);
			return new Trail(applyOffsets(target, offset, relativeOffset, 0, 0, 0, 0, 0), trailColor, duration);
		}

		if (type == BlockData.class) return blockData.get(data);
		if (type == DustOptions.class) return dustOptions.get(data);
		if (type == Particle.Spell.class) return spellOptions.get(data);
		if (type == DustTransition.class) return dustTransition.get(data);
		if (type == Float.class) return sculkChargeRotation.get(data);
		if (type == Integer.class) return shriekDelay.get(data);

		return null;
	}

	private Object resolveColorData(Particle particle, SpellData data) {
		// ENTITY_EFFECT supports alpha; FLASH / TINTED_LEAVES are RGB-only.
		return particle == Particle.ENTITY_EFFECT ? argbColor.get(data) : color.get(data);
	}

	protected Location getSpawnLocation(Particle particle, Location position, SpellData data) {
		return switch (particle) {
			case TRAIL -> getLocation(position, data, trailOrigin);
			case VIBRATION -> getLocation(position, data, vibrationOrigin);
			default -> position;
		};
	}

	protected Location getLocation(Location location, SpellData data, ConfigData<ParticlePosition> position) {
		ParticlePosition resolved = position.get(data);
		if (resolved == null) return null;

		return switch (resolved) {
			case CASTER -> {
				Entity caster = data == null ? null : data.caster();
				yield caster == null ? null : caster.getLocation();
			}
			case TARGET -> {
				Entity target = data == null ? null : data.target();
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
