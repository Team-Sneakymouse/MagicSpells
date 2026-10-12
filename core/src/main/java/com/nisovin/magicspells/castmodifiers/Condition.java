package com.nisovin.magicspells.castmodifiers;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public abstract class Condition {

	public abstract boolean initialize(String var);

	public abstract boolean check(LivingEntity livingEntity);

	public abstract boolean check(LivingEntity livingEntity, LivingEntity target);

	public abstract boolean check(LivingEntity livingEntity, Location location);

	/**
	 * Entity-capable check. Default: only living entities satisfy living conditions.
	 * Override for conditions that work on any {@link Entity} (e.g. scoreboard tags).
	 */
	public boolean check(Entity entity) {
		return entity instanceof LivingEntity living && check(living);
	}

	/**
	 * Entity-capable check against another entity. Default requires both living.
	 */
	public boolean check(Entity entity, Entity target) {
		return entity instanceof LivingEntity living
			&& target instanceof LivingEntity livingTarget
			&& check(living, livingTarget);
	}

	/**
	 * Entity-capable check against a location. Default requires a living caster.
	 */
	public boolean check(Entity entity, Location location) {
		return entity instanceof LivingEntity living && check(living, location);
	}

}
