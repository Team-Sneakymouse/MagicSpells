package com.nisovin.magicspells.spells;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;

/**
 * Targeted location casts. {@link #castAtLocation(SpellData)} is canonical;
 * boolean overloads are thin deprecated wrappers.
 */
public interface TargetedLocationSpell {

	CastResult castAtLocation(SpellData data);

	@Deprecated
	default boolean castAtLocation(Entity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Deprecated
	default boolean castAtLocation(Location target, float power, String[] args) {
		return castAtLocation(new SpellData(null, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Deprecated
	default boolean castAtLocation(Entity caster, Location target, float power) {
		return castAtLocation(caster, target, power, null);
	}

	@Deprecated
	default boolean castAtLocation(Location target, float power) {
		return castAtLocation(target, power, null);
	}

}
