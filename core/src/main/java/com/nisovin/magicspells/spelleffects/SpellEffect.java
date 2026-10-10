package com.nisovin.magicspells.spelleffects;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.spelleffects.trackers.BuffTracker;
import com.nisovin.magicspells.spelleffects.trackers.OrbitTracker;
import com.nisovin.magicspells.spelleffects.trackers.BuffEffectlibTracker;
import com.nisovin.magicspells.spelleffects.trackers.OrbitEffectlibTracker;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.util.VectorUtils;

public abstract class SpellEffect {

	protected final Random random = ThreadLocalRandom.current();

	private ConfigData<Integer> delay;

	private ConfigData<Double> chance;
	private ConfigData<Double> zOffset;
	private ConfigData<Double> heightOffset;
	private ConfigData<Double> forwardOffset;
	private ConfigData<Float> addPitch;
	private ConfigData<Float> addYaw;
	private ConfigData<Float> setPitch;
	private boolean doSetPitch;
	private ConfigData<Float> setYaw;
	private  boolean doSetYaw;

	private Vector offset;
	private Vector relativeOffset;

	// for line
	private ConfigData<Double> maxDistance;
	private ConfigData<Double> distanceBetween;
	private ConfigData<Double> endLocationHeightOffset;
	private ConfigData<Double> startLocationHeightOffset;

	// for buff/orbit
	private ConfigData<Float> orbitXAxis;
	private ConfigData<Float> orbitYAxis;
	private ConfigData<Float> orbitZAxis;
	private ConfigData<Float> orbitRadius;
	private ConfigData<Float> orbitYOffset;
	private ConfigData<Float> horizOffset;
	private ConfigData<Float> horizExpandRadius;
	private ConfigData<Float> vertExpandRadius;
	private ConfigData<Float> secondsPerRevolution;

	private ConfigData<Boolean> dragEntity;

	private ConfigData<Integer> horizExpandDelay;
	private ConfigData<Integer> vertExpandDelay;
	private ConfigData<Integer> effectInterval;
	public ConfigData<Integer> specialEffectInterval;

	private boolean counterClockwise;

	private List<String> modifiersList;
	private List<String> casterModifiersList;
	private List<String> targetModifiersList;
	private List<String> locationModifiersList;

	private ModifierSet modifiers;
	private ModifierSet casterModifiers;
	private ModifierSet targetModifiers;
	private ModifierSet locationModifiers;

	public final void loadFromConfiguration(ConfigurationSection config) {
		delay = ConfigDataUtil.getInteger(config, "delay", 0);
		chance = ConfigDataUtil.getDouble(config, "chance", -1);
		zOffset = ConfigDataUtil.getDouble(config, "z-offset", 0);
		heightOffset = ConfigDataUtil.getDouble(config, "height-offset", 0);
		forwardOffset = ConfigDataUtil.getDouble(config, "forward-offset", 0);
		addPitch = ConfigDataUtil.getFloat(config, "add-pitch", 0F);
		addYaw = ConfigDataUtil.getFloat(config, "add-yaw", 0F);
		doSetPitch = !config.getString("set-pitch", "").isEmpty();
		setPitch = ConfigDataUtil.getFloat(config, "set-pitch", 0F);
		doSetYaw = !config.getString("set-yaw", "").isEmpty();
		setYaw = ConfigDataUtil.getFloat(config, "set-yaw", 0F);

		String[] offsetStr = config.getString("offset", "0,0,0").split(",");
		String[] relativeStr = config.getString("relative-offset", "0,0,0").split(",");
		offset = new Vector(Double.parseDouble(offsetStr[0]), Double.parseDouble(offsetStr[1]), Double.parseDouble(offsetStr[2]));
		relativeOffset = new Vector(Double.parseDouble(relativeStr[0]), Double.parseDouble(relativeStr[1]), Double.parseDouble(relativeStr[2]));

		maxDistance = ConfigDataUtil.getDouble(config, "max-distance", 100);
		distanceBetween = ConfigDataUtil.getDouble(config, "distance-between", 1);
		endLocationHeightOffset = ConfigDataUtil.getDouble(config, "end-location-height-offset", 0);
		startLocationHeightOffset = ConfigDataUtil.getDouble(config, "start-location-height-offset", 0);

		String path = "orbit-";
		orbitXAxis = ConfigDataUtil.getFloat(config, path + "x-axis", 0F);
		orbitYAxis = ConfigDataUtil.getFloat(config, path + "y-axis", 0F);
		orbitZAxis = ConfigDataUtil.getFloat(config, path + "z-axis", 0F);
		orbitRadius = ConfigDataUtil.getFloat(config, path + "radius", 1F);
		orbitYOffset = ConfigDataUtil.getFloat(config, path + "y-offset", 0F);
		horizOffset = ConfigDataUtil.getFloat(config, path + "horiz-offset", 0F);
		horizExpandRadius = ConfigDataUtil.getFloat(config, path + "horiz-expand-radius", 0);
		vertExpandRadius = ConfigDataUtil.getFloat(config, path + "vert-expand-radius", 0);
		secondsPerRevolution = ConfigDataUtil.getFloat(config, path + "seconds-per-revolution", 3);

		horizExpandDelay = ConfigDataUtil.getInteger(config, path + "horiz-expand-delay", 0);
		vertExpandDelay = ConfigDataUtil.getInteger(config, path + "vert-expand-delay", 0);
		effectInterval = ConfigDataUtil.getInteger(config, "effect-interval", TimeUtil.TICKS_PER_SECOND);
		specialEffectInterval = ConfigDataUtil.getInteger(config, "special-effect-interval", -1);

		dragEntity = ConfigDataUtil.getBoolean(config, "drag-entity", false);

		counterClockwise = config.getBoolean(path + "counter-clockwise", false);

		modifiersList = config.getStringList("modifiers");
		casterModifiersList = config.getStringList("caster-modifiers");
		targetModifiersList = config.getStringList("target-modifiers");
		locationModifiersList = config.getStringList("location-modifiers");

		loadFromConfig(config);
	}

	public void initializeModifiers(Spell spell) {
		if (modifiersList != null) {
			modifiers = new ModifierSet(modifiersList, spell);
			modifiersList = null;
		}

		if (casterModifiersList != null) {
			casterModifiers = new ModifierSet(casterModifiersList, spell);
			casterModifiersList = null;
		}

		if (targetModifiersList != null) {
			targetModifiers = new ModifierSet(targetModifiersList, spell);
			targetModifiersList = null;
		}

		if (locationModifiersList != null) {
			locationModifiers = new ModifierSet(locationModifiersList, spell);
			locationModifiersList = null;
		}
	}

	protected ModifierResult checkModifiers(SpellData data) {
		if (data == null) return new ModifierResult(null, true);

		ModifierResult result = null;
		if (casterModifiers != null && data.caster() != null) {
			result = casterModifiers.apply(data);
			if (!result.check()) return result;
		}

		if (targetModifiers != null && data.caster() != null && data.target() != null) {
			if (result != null) data = result.data();
			return targetModifiers.applyTarget(data);
		}

		return result == null ? new ModifierResult(data, true) : result;
	}

	public ModifierResult checkModifiers(SpellData data, Location location) {
		if (data == null) return new ModifierResult(null, true);

		ModifierResult result = null;
		if (casterModifiers != null) {
			result = casterModifiers.apply(data);
			if (!result.check()) return result;
		}

		if (targetModifiers != null && data.caster() != null && data.target() != null) {
			if (result != null) data = result.data();

			result = targetModifiers.applyTarget(data);
			if (!result.check()) return result;
		}

		if (locationModifiers != null && data.caster() != null) {
			if (result != null) data = result.data();
			return locationModifiers.applyLocation(data.location(location));
		}

		return result == null ? new ModifierResult(data, true) : result;
	}

	protected abstract void loadFromConfig(ConfigurationSection config);

	public Location applyOffsets(Location loc) {
		return applyOffsets(loc, null);
	}

	public Location applyOffsets(Location loc, SpellData data) {
		return applyOffsets(loc, offset, relativeOffset, zOffset.get(data), heightOffset.get(data), forwardOffset.get(data), (doSetPitch) ? setPitch.get(data) : loc.getPitch() + addPitch.get(data), (doSetYaw) ? setYaw.get(data) : loc.getYaw() + addYaw.get(data));
	}

	public Location applyOffsets(Location loc, Vector offset, Vector relativeOffset, double zOffset, double heightOffset, double forwardOffset, float pitch, float yaw) {
		if (offset.getX() != 0 || offset.getY() != 0 || offset.getZ() != 0) loc.add(offset);
		if (relativeOffset.getX() != 0 || relativeOffset.getY() != 0 || relativeOffset.getZ() != 0)
			loc.add(VectorUtils.rotateVector(relativeOffset, loc));
		if (zOffset != 0) {
			Vector locDirection = loc.getDirection().normalize();
			Vector horizOffset = new Vector(-locDirection.getZ(), 0.0, locDirection.getX()).normalize();
			loc.add(horizOffset.multiply(zOffset));
		}
		if (heightOffset != 0) loc.setY(loc.getY() + heightOffset);
		if (forwardOffset != 0) loc.add(loc.getDirection().setY(0).normalize().multiply(forwardOffset));
		loc.setPitch(pitch);
		loc.setYaw(yaw);
		return loc;
	}

	/**
	 * Plays an effect on the specified entity.
	 *
	 * @param entity the entity to play the effect on
	 */
	@Deprecated
	public Runnable playEffect(final Entity entity) {
		return playEffect(entity, null);
	}

	/**
	 * Plays an effect on the specified entity.
	 *
	 * @param entity the entity to play the effect on
	 * @param data   the spell data of the casting spell
	 */
	public Runnable playEffect(final Entity entity, SpellData data) {
		double chance = this.chance.get(data);
		if (chance > 0 && chance < 1 && random.nextDouble() > chance) return null;

		ModifierResult result = checkModifiers(data);
		if (!result.check()) return null;
		data = result.data();

		if (modifiers != null && entity instanceof LivingEntity le) {
			result = modifiers.apply(data.caster(le));
			if (!result.check()) return null;
			data = result.data();
		}

		int delay = this.delay.get(data);
		if (delay <= 0) return playEffectEntity(entity, data);

		SpellData finalData = data;
		MagicSpells.scheduleDelayedTask(() -> playEffectEntity(entity, finalData), delay);

		return null;
	}

	@Deprecated
	protected Runnable playEffectEntity(Entity entity) {
		return playEffectLocationReal(entity == null ? null : entity.getLocation(), null);
	}

	protected Runnable playEffectEntity(Entity entity, SpellData data) {
		return playEffectLocationReal(entity == null ? null : entity.getLocation(), data);
	}

	/**
	 * Plays an effect at the specified location.
	 *
	 * @param location location to play the effect at
	 */
	@Deprecated
	public final Runnable playEffect(final Location location) {
		return playEffect(location, (SpellData) null);
	}

	/**
	 * Plays an effect at the specified location.
	 *
	 * @param location location to play the effect at
	 * @param data     the spell data of the casting spell
	 */
	public final Runnable playEffect(final Location location, SpellData data) {
		double chance = this.chance.get(data);
		if (chance > 0 && chance < 1 && random.nextDouble() > chance) return null;

		ModifierResult result = checkModifiers(data, location);
		if (!result.check()) return null;
		data = result.data();

		int delay = this.delay.get(data);
		if (delay <= 0) return playEffectLocationReal(location, data);

		SpellData finalData = data;
		MagicSpells.scheduleDelayedTask(() -> playEffectLocationReal(location, finalData), delay);

		return null;
	}

	@Deprecated
	public final Effect playEffectLib(final Location location) {
		return playEffectLib(location, null);
	}

	public final Effect playEffectLib(final Location location, SpellData data) {
		double chance = this.chance.get(data);
		if (chance > 0 && chance < 1 && random.nextDouble() > chance) return null;

		ModifierResult result = checkModifiers(data, location);
		if (!result.check()) return null;
		data = result.data();

		int delay = this.delay.get(data);
		if (delay <= 0) return playEffectLibLocationReal(location, data);

		SpellData finalData = data;
		MagicSpells.scheduleDelayedTask(() -> playEffectLibLocationReal(location, finalData), delay);

		return null;
	}

	@Deprecated
	public final Entity playEntityEffect(final Location location) {
		return playEntityEffect(location, null);
	}

	public final Entity playEntityEffect(final Location location, SpellData data) {
		double chance = this.chance.get(data);
		if (chance > 0 && chance < 1 && random.nextDouble() > chance) return null;

		ModifierResult result = checkModifiers(data, location);
		if (!result.check()) return null;
		data = result.data();

		int delay = this.delay.get(data);
		if (delay <= 0) return playEntityEffectLocationReal(location, data);

		SpellData finalData = data;
		MagicSpells.scheduleDelayedTask(() -> playEntityEffectLocationReal(location, finalData), delay);

		return null;
	}

	@Deprecated
	public final ArmorStand playArmorStandEffect(final Location location) {
		return playArmorStandEffect(location, null);
	}

	public final ArmorStand playArmorStandEffect(final Location location, SpellData data) {
		double chance = this.chance.get(data);
		if (chance > 0 && chance < 1 && random.nextDouble() > chance) return null;

		ModifierResult result = checkModifiers(data, location);
		if (!result.check()) return null;
		data = result.data();

		int delay = this.delay.get(data);
		if (delay <= 0) return playArmorStandEffectLocationReal(location, data);

		SpellData finalData = data;
		MagicSpells.scheduleDelayedTask(() -> playEffectLibLocationReal(location, finalData), delay);

		return null;
	}

	private Runnable playEffectLocationReal(Location location, SpellData data) {
		if (location == null) return playEffectLocation(null, data);
		Location loc = location.clone();

		applyOffsets(loc, data);
		return playEffectLocation(loc, data);
	}

	private Effect playEffectLibLocationReal(Location location, SpellData data) {
		if (location == null) return playEffectLibLocation(null, data);
		Location loc = location.clone();
		applyOffsets(loc, data);
		return playEffectLibLocation(loc, data);
	}

	public Entity playEntityEffectLocationReal(Location location, SpellData data) {
		if (location == null) return playEntityEffectLocation(null, data);
		Location loc = location.clone();
		applyOffsets(loc, data);
		return playEntityEffectLocation(loc, data);
	}

	private ArmorStand playArmorStandEffectLocationReal(Location location, SpellData data) {
		if (location == null) return playArmorStandEffectLocation(null, data);
		Location loc = location.clone();
		applyOffsets(loc, data);
		return playArmorStandEffectLocation(loc, data);
	}

	@Deprecated
	protected Runnable playEffectLocation(Location location) {
		//expect to be overridden
		return null;
	}

	protected Runnable playEffectLocation(Location location, SpellData data) {
		//expect to be overridden
		return playEffectLocation(location);
	}

	@Deprecated
	protected Effect playEffectLibLocation(Location location) {
		//expect to be overridden
		return null;
	}

	protected Effect playEffectLibLocation(Location location, SpellData data) {
		//expect to be overridden
		return playEffectLibLocation(location);
	}

	@Deprecated
	protected Entity playEntityEffectLocation(Location location) {
		//expect to be overridden
		return null;
	}

	protected Entity playEntityEffectLocation(Location location, SpellData data) {
		//expect to be overridden
		return playEntityEffectLocation(location);
	}

	@Deprecated
	protected ArmorStand playArmorStandEffectLocation(Location location) {
		//expect to be overridden
		return null;
	}

	protected ArmorStand playArmorStandEffectLocation(Location location, SpellData data) {
		//expect to be overridden
		return playArmorStandEffectLocation(location);
	}

	@Deprecated
	public void playTrackingLinePatterns(Location origin, Location target, Entity originEntity, Entity targetEntity) {
		// no op, effects should override this with their own behavior
	}

	public void playTrackingLinePatterns(Location origin, Location target, Entity originEntity, Entity targetEntity, SpellData data) {
		// no op, effects should override this with their own behavior
		playTrackingLinePatterns(origin, target, originEntity, targetEntity);
	}

	/**
	 * Plays an effect between two locations (such as a smoke trail type effect).
	 *
	 * @param startLoc the starting location
	 * @param endLoc the ending location
	 */
	@Deprecated
	public Runnable playEffect(Location startLoc, Location endLoc) {
		return playEffect(startLoc, endLoc, null);
	}

	/**
	 * Plays an effect between two locations (such as a smoke trail type effect).
	 *
	 * @param startLoc the starting location
	 * @param endLoc the ending location
	 */
	public Runnable playEffect(Location startLoc, Location endLoc, SpellData data) {
		double maxDistanceSquared = maxDistance.get(data);
		maxDistanceSquared *= maxDistanceSquared;

		Location start = startLoc.clone().add(0, startLocationHeightOffset.get(data), 0);
		Location end = endLoc.clone().add(0, endLocationHeightOffset.get(data), 0);

		double distanceBetween = this.distanceBetween.get(data);
		int c = (int) Math.ceil(start.distance(end) / distanceBetween) - 1;
		if (c <= 0) return null;

		Vector v = end.toVector().subtract(start.toVector()).normalize().multiply(distanceBetween);
		Location l = start.clone();

		double heightOffset = this.heightOffset.get(data);
		if (heightOffset != 0) l.setY(l.getY() + heightOffset);

		for (int i = 0; i < c; i++) {
			l.add(v);
			if (startLoc.distanceSquared(l) > maxDistanceSquared) return null;

			l.setDirection(v);
			playEffect(l, data);
		}

		return null;
	}

	@Deprecated
	public BuffEffectlibTracker playEffectlibEffectWhileActiveOnEntity(final Entity entity, final SpellEffectActiveChecker checker) {
		return new BuffEffectlibTracker(entity, checker, this, null);
	}

	public BuffEffectlibTracker playEffectlibEffectWhileActiveOnEntity(final Entity entity, final SpellEffectActiveChecker checker, SpellData data) {
		return new BuffEffectlibTracker(entity, checker, this, data);
	}

	@Deprecated
	public OrbitEffectlibTracker playEffectlibEffectWhileActiveOrbit(final Entity entity, final SpellEffectActiveChecker checker) {
		return new OrbitEffectlibTracker(entity, checker, this, null);
	}

	public OrbitEffectlibTracker playEffectlibEffectWhileActiveOrbit(final Entity entity, final SpellEffectActiveChecker checker, SpellData data) {
		return new OrbitEffectlibTracker(entity, checker, this, data);
	}

	@Deprecated
	public BuffTracker playEffectWhileActiveOnEntity(final Entity entity, final SpellEffectActiveChecker checker) {
		return new BuffTracker(entity, checker, this, null);
	}

	public BuffTracker playEffectWhileActiveOnEntity(final Entity entity, final SpellEffectActiveChecker checker, SpellData data) {
		return new BuffTracker(entity, checker, this, data);
	}

	@Deprecated
	public OrbitTracker playEffectWhileActiveOrbit(final Entity entity, final SpellEffectActiveChecker checker) {
		return new OrbitTracker(entity, checker, this, null);
	}

	public OrbitTracker playEffectWhileActiveOrbit(final Entity entity, final SpellEffectActiveChecker checker, final SpellData data) {
		return new OrbitTracker(entity, checker, this, data);
	}

	// override if you need to do additional stuff when disabling spell effects
	public void turnOff() {

	}

	@FunctionalInterface
	public interface SpellEffectActiveChecker {
		boolean isActive(Entity entity);
	}

	public ConfigData<Integer> getDelay() {
		return delay;
	}

	public ConfigData<Double> getChance() {
		return chance;
	}

	public ConfigData<Double> getZOffset() {
		return zOffset;
	}

	public ConfigData<Double> getHeightOffset() {
		return heightOffset;
	}

	public ConfigData<Double> getForwardOffset() {
		return forwardOffset;
	}

	public Vector getOffset() {
		return offset;
	}

	public Vector getRelativeOffset() {
		return relativeOffset;
	}

	public ConfigData<Double> getMaxDistance() {
		return maxDistance;
	}

	public ConfigData<Double> getDistanceBetween() {
		return distanceBetween;
	}

	public ConfigData<Float> getOrbitXAxis() {
		return orbitXAxis;
	}

	public ConfigData<Float> getOrbitYAxis() {
		return orbitYAxis;
	}

	public ConfigData<Float> getOrbitZAxis() {
		return orbitZAxis;
	}

	public ConfigData<Float> getOrbitRadius() {
		return orbitRadius;
	}

	public ConfigData<Float> getOrbitYOffset() {
		return orbitYOffset;
	}

	public ConfigData<Float> getHorizOffset() {
		return horizOffset;
	}

	public ConfigData<Float> getHorizExpandRadius() {
		return horizExpandRadius;
	}

	public ConfigData<Float> getVertExpandRadius() {
		return vertExpandRadius;
	}

	public ConfigData<Float> getSecondsPerRevolution() {
		return secondsPerRevolution;
	}

	public ConfigData<Integer> getHorizExpandDelay() {
		return horizExpandDelay;
	}

	public ConfigData<Integer> getVertExpandDelay() {
		return vertExpandDelay;
	}

	public ConfigData<Integer> getEffectInterval() {
		return effectInterval;
	}

	public ConfigData<Boolean> isDraggingEntity() {
		return dragEntity;
	}

	public boolean isCounterClockwise() {
		return counterClockwise;
	}

	public ModifierSet getModifiers() {
		return modifiers;
	}

}
