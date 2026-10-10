package com.nisovin.magicspells.spells;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Targeted location casts. Implement the caster+location {@code String[] args} overload.
 * The location-only args form defaults to {@code false}; override when needed.
 * No-args wrappers are deprecated defaults that pass {@code null} args.
 */
public interface TargetedLocationSpell {

	boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args);

	default boolean castAtLocation(Location target, float power, String[] args) {
		return false;
	}

	@Deprecated
	default boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return castAtLocation(caster, target, power, null);
	}

	@Deprecated
	default boolean castAtLocation(Location target, float power) {
		return castAtLocation(target, power, null);
	}

}
