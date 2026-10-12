package com.nisovin.magicspells.castmodifiers;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.ManaChangeEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsGenericPlayerEvent;

public interface IModifier {

	boolean apply(SpellCastEvent event);
	boolean apply(ManaChangeEvent event);
	boolean apply(SpellTargetEvent event);
	boolean apply(SpellTargetLocationEvent event);
	boolean apply(MagicSpellsGenericPlayerEvent event);
	boolean apply(SpellCastedEvent event);

	/**
	 * Apply modifiers with a caster-only condition check, using context from {@code data}.
	 */
	default ModifierResult apply(SpellData data) {
		return apply(data.caster(), data);
	}

	/**
	 * Apply modifiers with an entity-target condition check ({@code data.caster()} vs {@code data.target()}).
	 */
	default ModifierResult applyTarget(SpellData data) {
		return apply(data.caster(), data.target(), data);
	}

	/**
	 * Apply modifiers with a location-target condition check ({@code data.caster()} vs {@code data.location()}).
	 */
	default ModifierResult applyLocation(SpellData data) {
		return apply(data.caster(), data.location(), data);
	}

	/**
	 * @deprecated use {@link #apply(SpellData)}
	 */
	@Deprecated
	ModifierResult apply(Entity caster, SpellData data);

	/**
	 * @deprecated use {@link #applyTarget(SpellData)} after putting the target on {@code data}
	 */
	@Deprecated
	ModifierResult apply(Entity caster, Entity target, SpellData data);

	/**
	 * @deprecated use {@link #applyLocation(SpellData)} after putting the location on {@code data}
	 */
	@Deprecated
	ModifierResult apply(Entity caster, Location target, SpellData data);

	/**
	 * Caster-only required/denied check using {@code data.caster()}.
	 */
	default boolean check(SpellData data) {
		return check(data.caster());
	}

	/**
	 * Entity-target required/denied check.
	 */
	default boolean checkTarget(SpellData data) {
		return check(data.caster(), data.target());
	}

	/**
	 * Location-target required/denied check.
	 */
	default boolean checkLocation(SpellData data) {
		return check(data.caster(), data.location());
	}

	/**
	 * @deprecated use {@link #check(SpellData)}
	 */
	@Deprecated
	boolean check(Entity livingEntity);

	/**
	 * @deprecated use {@link #checkTarget(SpellData)}
	 */
	@Deprecated
	boolean check(Entity livingEntity, Entity entity);

	/**
	 * @deprecated use {@link #checkLocation(SpellData)}
	 */
	@Deprecated
	boolean check(Entity livingEntity, Location location);

}
