package com.nisovin.magicspells.spells;

import org.bukkit.entity.LivingEntity;

/**
 * Targeted entity casts. Prefer the {@code String[] args} overloads.
 * No-args methods are deprecated; implementing spells suppress that on overrides until
 * this interface is inverted (args primary, no-args default wrappers). See COMPILE-WARNINGS.md.
 */
public interface TargetedEntitySpell {

	default boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(caster, target, power);
	}

	default boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(target, power);
	}

	@Deprecated
	boolean castAtEntity(LivingEntity caster, LivingEntity target, float power);

	@Deprecated
	boolean castAtEntity(LivingEntity target, float power);

}
