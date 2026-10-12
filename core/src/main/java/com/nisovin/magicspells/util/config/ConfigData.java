package com.nisovin.magicspells.util.config;

import org.bukkit.entity.Entity;
import org.bukkit.Location;

import com.nisovin.magicspells.util.SpellData;

public interface ConfigData<T> {

	T get(Entity caster, Entity target, Location location, float power, String[] args);

	default T get(Entity caster, Entity target, float power, String[] args) {
		return get(caster, target, target == null ? caster == null ? null : caster.getLocation() : target.getLocation(),
				power, args);
	}

	default T get(Entity caster, float power, String[] args) {
		return get(caster, null, power, args);
	}

	default T get(SpellData data) {
		if (data == null)
			return get(null, null, null, 1f, null);
		// When location was not set on SpellData, fall back to the loose overload that
		// derives location from target/caster (same as get(caster, target, power, args)).
		if (data.hasLocation())
			return get(data.caster(), data.target(), data.location(), data.power(), data.args());
		return get(data.caster(), data.target(), data.power(), data.args());
	}

	default boolean isConstant() {
		return true;
	}

}
