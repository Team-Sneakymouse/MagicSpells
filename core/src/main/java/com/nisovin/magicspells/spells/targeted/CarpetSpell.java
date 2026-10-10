package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;

public class CarpetSpell extends TargetedSpell implements TargetedLocationSpell {

	private Map<Block, CarpetData> blocks;

	private Material material;
	private String materialName;

	private int touchCheckInterval;
	private ConfigData<Integer> radius;
	private ConfigData<Integer> duration;

	private boolean circle;
	private boolean removeOnTouch;
	private ConfigData<Boolean> powerAffectsRadius;

	private String spellOnTouchName;
	private Subspell spellOnTouch;

	private TouchChecker checker;

	public CarpetSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		materialName = getConfigString("block", "white_carpet");
		material = Util.getMaterial(materialName);
		if (material == null || !material.isBlock()) {
			MagicSpells.error("CarpetSpell '" + internalName + "' has an invalid block defined!");
			material = null;
		}

		radius = getConfigDataInt("radius", 1);
		duration = getConfigDataInt("duration", 0);
		touchCheckInterval = getConfigInt("touch-check-interval", 3);

		circle = getConfigBoolean("circle", false);
		removeOnTouch = getConfigBoolean("remove-on-touch", true);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", true);

		spellOnTouchName = getConfigString("spell-on-touch", "");

		blocks = new HashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		spellOnTouch = new Subspell(spellOnTouchName);
		if (!spellOnTouch.process()) {
			if (!spellOnTouchName.isEmpty())
				MagicSpells.error("CarpetSpell '" + internalName + "' has an invalid spell-on-touch defined!");
			spellOnTouch = null;
		}

		if (spellOnTouch != null)
			checker = new TouchChecker();
	}

	@Override
	public void turnOff() {
		super.turnOff();

		for (Map.Entry<Block, CarpetData> entry : blocks.entrySet()) {
			if (entry.getValue().change != null) entry.getValue().change.undo(false);
			else entry.getKey().setType(Material.AIR);
		}
		blocks.clear();
		if (checker != null)
			checker.stop();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL && caster instanceof Player player) {
			Location loc = null;
			if (targetSelf)
				loc = player.getLocation();
			else {
				Block b = getTargetedBlock(player, power, args);
				if (b != null && b.getType() != Material.AIR)
					loc = b.getLocation();
			}

			if (loc == null)
				return noTarget(player, args);

			layCarpet(player, loc, power, args);
		}
		return PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		if (!(caster instanceof Player))
			return false;
		if (targetSelf)
			layCarpet((Player) caster, caster.getLocation(), power, args);
		else
			layCarpet((Player) caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return castAtLocation(caster, target, power, null);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		layCarpet(null, target, power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power) {
		layCarpet(null, target, power, null);
		return true;
	}

	private void layCarpet(Player player, Location loc, float power, String[] args) {
		if (!loc.getBlock().getType().isOccluding()) {
			int c = 0;
			while (!loc.getBlock().getRelative(0, -1, 0).getType().isOccluding() && c <= 2) {
				loc.subtract(0, 1, 0);
				c++;
			}
		} else {
			int c = 0;
			while (loc.getBlock().getType().isOccluding() && c <= 2) {
				loc.add(0, 1, 0);
				c++;
			}
		}

		Block b;
		int y = loc.getBlockY();

		int rad = this.radius.get(player, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(player, null, power, args)))
			rad = Math.round(rad * power);

		SpellData data = new SpellData(player, loc, power, args);

		final List<AlteredBlockManager.Change> changeList = new ArrayList<>();
		int duration = this.duration.get(player, null, power, args);
		boolean temporary = duration > 0 || removeOnTouch || spellOnTouch != null;

		for (int x = loc.getBlockX() - rad; x <= loc.getBlockX() + rad; x++) {
			for (int z = loc.getBlockZ() - rad; z <= loc.getBlockZ() + rad; z++) {
				b = loc.getWorld().getBlockAt(x, y, z);
				if (circle && loc.getBlock().getLocation().distanceSquared(b.getLocation()) > rad * rad)
					continue;

				if (b.getType().isOccluding())
					b = b.getRelative(0, 1, 0);
				else if (!b.getRelative(0, -1, 0).getType().isOccluding())
					b = b.getRelative(0, -1, 0);

				if (!BlockUtils.isAir(b.getType()) && !b.getRelative(0, -1, 0).getType().isSolid())
					continue;

				AlteredBlockManager.Change change = null;
				if (temporary) {
					change = MagicSpells.getAlteredBlockManager().apply(internalName, b, material, false);
					changeList.add(change);
				} else {
					b.setType(material, false);
				}
				blocks.put(b, new CarpetData(player, power, args, change));
				playSpellEffects(EffectPosition.TARGET, b.getLocation().add(0.5, 0, 0.5), data);
			}
		}

		if (duration > 0 && !changeList.isEmpty()) {
			MagicSpells.scheduleDelayedTask(() -> {
				for (AlteredBlockManager.Change change : changeList) {
					Block block = change.block();
					if (!material.equals(block.getType()))
						continue;
					change.undo(false);
					if (blocks != null)
						blocks.remove(block);
				}
			}, duration);
		}
		if (player != null)
			playSpellEffects(EffectPosition.CASTER, player, data);
	}

	private record CarpetData(LivingEntity caster, float power, String[] args, AlteredBlockManager.Change change) {
	}

	private class TouchChecker implements Runnable {

		private int taskId;

		private TouchChecker() {
			taskId = MagicSpells.scheduleRepeatingTask(this, touchCheckInterval, touchCheckInterval);
		}

		@Override
		public void run() {
			if (blocks.isEmpty())
				return;
			for (Player player : Bukkit.getOnlinePlayers()) {

				Block b = player.getLocation().getBlock();
				CarpetData data = blocks.get(b);

				if (data == null)
					continue;
				if (player.equals(data.caster))
					continue;
				if (!material.equals(b.getType()))
					continue;

				if (removeOnTouch) {
					if (data.change != null) data.change.undo(false);
					else b.setType(Material.AIR);
					blocks.remove(b);
				}

				if (spellOnTouch != null) {
					SpellTargetEvent event = new SpellTargetEvent(CarpetSpell.this, data.caster, player, data.power,
							data.args);
					if (!event.callEvent())
						continue;

					spellOnTouch.subcast(data.caster, event.getTarget(), event.getPower(), data.args);
				}
			}
		}

		private void stop() {
			MagicSpells.cancelTask(taskId);
		}

	}

}
