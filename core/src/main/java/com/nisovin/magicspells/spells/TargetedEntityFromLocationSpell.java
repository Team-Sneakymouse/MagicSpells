package com.nisovin.magicspells.spells;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;

/**
 * Targeted entity-from-location casts. Prefer {@link #castAtEntityFromLocation(SpellData)}.
 */
public interface TargetedEntityFromLocationSpell {

	default CastResult castAtEntityFromLocation(SpellData data) {
		boolean success = data.hasCaster()
			? castAtEntityFromLocation(data.caster(), data.location(), data.target(), data.power(), data.args())
			: castAtEntityFromLocation(data.location(), data.target(), data.power(), data.args());
		return new CastResult(success ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
	}

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
