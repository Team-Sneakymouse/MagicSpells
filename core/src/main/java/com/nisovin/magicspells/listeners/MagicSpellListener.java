package com.nisovin.magicspells.listeners;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import com.nisovin.magicspells.Perm;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.zones.NoMagicZoneManager;
import com.nisovin.magicspells.events.ParticleProjectileHitEvent;
import com.nisovin.magicspells.spelleffects.effecttypes.EntityEffect;
import com.nisovin.magicspells.spelleffects.effecttypes.ArmorStandEffect;

public class MagicSpellListener implements Listener {

	private NoMagicZoneManager noMagicZoneManager;
		
	public MagicSpellListener(MagicSpells plugin) {
		noMagicZoneManager = MagicSpells.getNoMagicZoneManager();
	}

	@EventHandler
	public void onSpellTarget(SpellTargetEvent event) {
		// Check if target has noTarget permission / spectator gamemode / is in noMagicZone / is an invisible marker armorstand
		Entity target = event.getTarget();
		Spell spell = event.getSpell();
		if (target == null) return;

		Entity caster = event.getCaster();
		boolean selfTarget = caster != null && target.equals(caster);

		// notarget means "others cannot target me", not "I cannot target myself" (menus, self-buffs).
		if (!selfTarget && Perm.NO_TARGET.has(target)) event.setCancelled(true);
		if (!selfTarget && target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
			event.setCancelled(true);
		}
		if (spell != null && noMagicZoneManager != null && noMagicZoneManager.willFizzle(target, spell)) event.setCancelled(true);
		if (isMSEntity(target)) event.setCancelled(true);
		if (target instanceof ArmorStand && target.isInvisible() && ((ArmorStand) target).isMarker()) event.setCancelled(true);
	}

	@EventHandler
	public void onProjectileHit(ParticleProjectileHitEvent event) {
		// Check if target is an armor stand with the specified tag
		Entity target = event.getTarget();

		if (target == null) return;
		if (isMSEntity(target)) event.setCancelled(true);
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		// remove entities in unloaded chunks
		for (Entity entity : event.getChunk().getEntities()) {
			if (!isMSEntity(entity)) continue;
			entity.remove();
		}
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();
		if (!isMSEntity(entity)) return;
		event.setCancelled(true);
	}

	private boolean isMSEntity(Entity entity) {
		return entity.getScoreboardTags().contains(ArmorStandEffect.ENTITY_TAG) || entity.getScoreboardTags().contains(EntityEffect.ENTITY_TAG);
	}

}
