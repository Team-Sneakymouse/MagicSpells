package com.nisovin.magicspells.spells.passive;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItems;

public class DestroyItemListener extends PassiveListener {

	private final Set<MagicItemData> items = new HashSet<>();

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.split("\\|");
		for (String s : split) {
			s = s.trim();

			MagicItemData itemData = MagicItems.getMagicItemDataFromString(s);
			if (itemData == null) {
				MagicSpells.error("Invalid magic item '" + s + "' in destroyitem trigger on passive spell '" + passiveSpell.getInternalName() + "'");
				continue;
			}

			items.add(itemData);
		}
	}

	@OverridePriority
	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Item item)) return;
		if (!isCancelStateOk(event.isCancelled())) return;

		DamageCause cause = event.getCause();
		if (!(cause.equals(DamageCause.FIRE) || cause.equals(DamageCause.FIRE_TICK) || cause.equals(DamageCause.LAVA))) return;

		// Fatal check: skip when already cancelled — a prior cancel-default-action may have
		// restored health so monitors with require-cancelled-event can still cook.
		if (!event.isCancelled() && event.getFinalDamage() < item.getHealth()) return;

		Player caster = resolveDropper(item);
		if (caster == null) return;

		if (!hasSpell(caster) || !canTrigger(caster)) return;

		if (!items.isEmpty()) {
			if (!contains(item.getItemStack())) return;
		}

		boolean casted = passiveSpell.activate(caster, item.getLocation());

		if (cancelDefaultAction(casted)) {
			event.setCancelled(true);
			// Cancelling alone is not reliable for Item entities in fire/lava — restore health
			// and clear fire so the drop survives. MONITOR cooks skip the fatal check when
			// already cancelled (see above).
			item.setFireTicks(0);
			if (item.getHealth() <= event.getFinalDamage()) item.setHealth(5);
		}
	}

	/**
	 * Player drops set thrower; some paths only set owner. Prefer thrower, fall back to owner.
	 */
	private static Player resolveDropper(Item item) {
		UUID uuid = item.getThrower();
		if (uuid == null) uuid = item.getOwner();
		if (uuid == null) return null;
		return Bukkit.getPlayer(uuid);
	}

	private boolean contains(ItemStack item) {
		for (MagicItemData data : items) {
			if (MagicItems.matches(data, item)) return true;
		}
		return false;
	}

}
