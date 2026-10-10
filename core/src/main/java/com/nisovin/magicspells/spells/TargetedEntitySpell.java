package com.nisovin.magicspells.spells;

import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;

/**
 * Targeted entity casts. Prefer {@link #castAtEntity(SpellData)}.
 * Existing boolean overloads remain for soft migration; the SpellData default
 * bridges to them until spell bodies override {@code castAtEntity(SpellData)}.
 */
public interface TargetedEntitySpell {

	default CastResult castAtEntity(SpellData data) {
		boolean success = data.hasCaster()
			? castAtEntity(data.caster(), data.target(), data.power(), data.args())
			: castAtEntity(data.target(), data.power(), data.args());
		return new CastResult(success ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
	}

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
