package com.nisovin.magicspells.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public final class EntityUtil {

	private EntityUtil() {
	}

	public static LivingEntity asLiving(Entity entity) {
		return entity instanceof LivingEntity living ? living : null;
	}

}
