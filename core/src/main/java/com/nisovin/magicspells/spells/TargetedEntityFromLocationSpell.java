package com.nisovin.magicspells.spells;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Targeted entity-from-location casts. Implement the caster {@code String[] args} overload.
 * The no-caster args form defaults to {@code false}; override when needed.
 * No-args wrappers are deprecated defaults that pass {@code null} args.
 */
public interface TargetedEntityFromLocationSpell {

	boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power, String[] args);

	default boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		return false;
	}

	@Deprecated
	default boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power) {
		return castAtEntityFromLocation(caster, from, target, power, null);
	}

	@Deprecated
	default boolean castAtEntityFromLocation(Location from, LivingEntity target, float power) {
		return castAtEntityFromLocation(from, target, power, null);
	}

}
