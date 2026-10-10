package com.nisovin.magicspells.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.EntityPdc;

public class EntityListener implements Listener {

	private static final NamespacedKey FIREWORK_FLAG = EntityPdc.key(MagicSpells.FIREWORK_METADATA_KEY);

	@EventHandler
	public void onFireworkDamage(EntityDamageByEntityEvent event) {
		if (!EntityPdc.hasFlag(event.getDamager(), FIREWORK_FLAG)) return;
		event.setCancelled(true);
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		for (Entity entity : event.getChunk().getEntities()) {
			if (entity.getScoreboardTags().contains(MagicSpells.ENTITY_TAG)) {
				String expirationTimeMillisTag = entity.getScoreboardTags().stream()
					.filter(tag -> tag.startsWith(MagicSpells.EXPIRATION_TIME_MILLIS_TAG + ":"))
					.findFirst()
					.orElse(null);
				if (expirationTimeMillisTag == null) continue;
				try {
					long expirationTimeMillis = Long.parseLong(expirationTimeMillisTag.split(":")[1]);
					if (System.currentTimeMillis() > expirationTimeMillis) {
						entity.remove();
					}
				} catch (NumberFormatException e) {
					MagicSpells.error("Error parsing expiration time millis for entity " + entity.getName() + ": " + e.getMessage());
					continue;
				}
			}
		}
	}
}
