package com.nisovin.magicspells.spells;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;

/**
 * Targeted location casts. Prefer {@link #castAtLocation(SpellData)}.
 */
public interface TargetedLocationSpell {

	default CastResult castAtLocation(SpellData data) {
		boolean success = data.hasCaster()
			? castAtLocation(data.caster(), data.location(), data.power(), data.args())
			: castAtLocation(data.location(), data.power(), data.args());
		return new CastResult(success ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
	}

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
