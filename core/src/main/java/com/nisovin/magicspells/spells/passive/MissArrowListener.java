package com.nisovin.magicspells.spells.passive;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.ProjectileHitEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

/**
 * Fires when a shot arrow hits a block (misses living entities).
 * Uses {@link ProjectileHitEvent#getHitEntity()} instead of Bukkit Metadata.
 */
public class MissArrowListener extends PassiveListener {

	private final Set<MagicItemData> items = new HashSet<>();

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.split("\\|");
		for (String s : split) {
			s = s.trim();

			MagicItemData itemData = MagicItems.getMagicItemDataFromString(s);
			if (itemData == null) {
				MagicSpells.error("Invalid magic item '" + s + "' in missarrow trigger on passive spell '" + passiveSpell.getInternalName() + "'");
				continue;
			}

			items.add(itemData);
		}
	}

	@OverridePriority
	@EventHandler
	public void onHit(ProjectileHitEvent event) {
		if (!(event.getEntity() instanceof Arrow)) return;
		if (event.getHitEntity() != null) return;

		LivingEntity caster = getAttacker(event);
		if (caster == null || !hasSpell(caster) || !canTrigger(caster)) return;

		if (!items.isEmpty()) {
			EntityEquipment eq = caster.getEquipment();
			if (eq == null) return;

			if (!contains(eq.getItemInMainHand())) return;
		}

		passiveSpell.activate(caster, event.getEntity().getLocation());
	}

	private boolean contains(ItemStack item) {
		for (MagicItemData data : items) {
			if (MagicItems.matches(data, item)) return true;
		}
		return false;
	}

	private LivingEntity getAttacker(ProjectileHitEvent event) {
		Projectile e = event.getEntity();
		if (!(e instanceof Arrow)) return null;
		if (e.getShooter() instanceof LivingEntity living)
			return living;
		return null;
	}

}
