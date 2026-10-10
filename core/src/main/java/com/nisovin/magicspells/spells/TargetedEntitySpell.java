package com.nisovin.magicspells.spells;

import org.bukkit.entity.LivingEntity;

/**
 * Targeted entity casts. Implement the caster+target {@code String[] args} overload.
 * The target-only args form defaults to {@code false}; override when needed.
 * No-args wrappers are deprecated defaults that pass {@code null} args.
 */
public interface TargetedEntitySpell {

	boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args);

	default boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return false;
	}

	@Deprecated
	default boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Deprecated
	default boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
	}

}
