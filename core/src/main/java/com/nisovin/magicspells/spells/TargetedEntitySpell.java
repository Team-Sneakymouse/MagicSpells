package com.nisovin.magicspells.spells;

import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;

/**
 * Targeted entity casts. {@link #castAtEntity(SpellData)} is canonical;
 * boolean overloads are thin deprecated wrappers.
 */
public interface TargetedEntitySpell {

	CastResult castAtEntity(SpellData data);

	@Deprecated
	default boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Deprecated
	default boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
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
