package com.nisovin.magicspells.util.projectile;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;

/**
 * Legacy {@code tippedarrow} projectile key. Tipped status is potion data on {@link Arrow}.
 */
public class ProjectileManagerTippedArrow extends ProjectileManager {
	
	@Override
	public Class<? extends Projectile> getProjectileClass() {
		return Arrow.class;
	}
	
}
