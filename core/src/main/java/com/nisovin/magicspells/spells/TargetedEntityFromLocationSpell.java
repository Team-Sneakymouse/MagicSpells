package com.nisovin.magicspells.spells;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;

/**
 * Targeted entity-from-location casts. {@link #castAtEntityFromLocation(SpellData)} is canonical;
 * boolean overloads are thin deprecated wrappers.
 */
public interface TargetedEntityFromLocationSpell {

	CastResult castAtEntityFromLocation(SpellData data);

	@Deprecated
	default boolean castAtEntityFromLocation(Entity caster, Location from, Entity target, float power,
			String[] args) {
		return castAtEntityFromLocation(new SpellData(caster, target, from, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	@Deprecated
	default boolean castAtEntityFromLocation(Location from, Entity target, float power, String[] args) {
		return castAtEntityFromLocation(new SpellData(null, target, from, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	@Deprecated
	default boolean castAtEntityFromLocation(Entity caster, Location from, Entity target, float power) {
		return castAtEntityFromLocation(caster, from, target, power, null);
	}

	@Deprecated
	default boolean castAtEntityFromLocation(Location from, Entity target, float power) {
		return castAtEntityFromLocation(from, target, power, null);
	}

}
