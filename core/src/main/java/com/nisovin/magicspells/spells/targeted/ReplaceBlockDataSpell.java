package com.nisovin.magicspells.spells.targeted;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;

import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.events.MagicSpellsBlockPlaceEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class ReplaceBlockDataSpell extends TargetedSpell implements TargetedLocationSpell {

	private boolean pointBlank;
	private ConfigData<Boolean> powerAffectsRadius;
	private boolean circleShape;
	private final boolean checkPlugins;

	private ConfigData<Integer> yOffset;
	private ConfigData<Integer> radiusUp;
	private ConfigData<Integer> radiusDown;
	private ConfigData<Integer> radiusHoriz;

	private List<String> replace;
	private List<String> replaceWith;

	public ReplaceBlockDataSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		replace = new ArrayList<>();
		replaceWith = new ArrayList<>();

		yOffset = getConfigDataInt("y-offset", 0);
		radiusUp = getConfigDataInt("radius-up", 0);
		radiusDown = getConfigDataInt("radius-down", 0);
		radiusHoriz = getConfigDataInt("radius-horiz", 0);

		pointBlank = getConfigBoolean("point-blank", false);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", false);
		circleShape = getConfigBoolean("circle-shape", false);
		checkPlugins = getConfigBoolean("check-plugins", true);

		replace = getConfigStringList("replace", null);
		replaceWith = getConfigStringList("replace-with", null);

		if (replace == null)
			MagicSpells.error("ReplaceBlockDataSpell " + internalName + " has an empty replace list");
		if (replace != null && (replaceWith == null || replace.size() != replaceWith.size()))
			MagicSpells.error("ReplaceBlockDataSpell " + internalName
					+ " replace-with list is not the same size as the replace list");
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			Block target = pointBlank ? caster.getLocation().getBlock() : getTargetedBlock(caster, power, args);
			if (target == null)
				return noTarget(caster, args);
			if (!replaceBlockData(caster, target.getLocation(), power, args))
				return PostCastAction.ALREADY_HANDLED;
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return replaceBlockData(caster, target, power, args);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return replaceBlockData(null, target, power, args);
	}

	private boolean replaceBlockData(LivingEntity caster, Location target, float power, String[] args) {
		if (replace == null || replaceWith == null)
			return false;

		boolean replaced = false;
		Block block;

		int d = radiusDown.get(caster, null, power, args);
		int u = radiusUp.get(caster, null, power, args);
		int h = radiusHoriz.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args))) {
			d = Math.round(d * power);
			u = Math.round(u * power);
			h = Math.round(h * power);
		}

		SpellData spellData = new SpellData(caster, target, power, args);
		int yOffset = this.yOffset.get(caster, null, power, args);

		for (int y = target.getBlockY() - d + yOffset; y <= target.getBlockY() + u + yOffset; y++) {
			for (int x = target.getBlockX() - h; x <= target.getBlockX() + h; x++) {
				for (int z = target.getBlockZ() - h; z <= target.getBlockZ() + h; z++) {
					if (circleShape) {
						double hDistanceSq = NumberConversions.square(x - target.getBlockX())
								+ NumberConversions.square(z - target.getBlockZ());
						if (hDistanceSq > (h * h))
							continue;
						double vDistance = NumberConversions.square(y - (target.getBlockY() + yOffset));
						if (y > target.getBlockY() + yOffset) {
							if (vDistance > (u * u))
								continue;
						} else {
							if (vDistance > (d * d))
								continue;
						}
					}

					block = target.getWorld().getBlockAt(x, y, z);

					String blockDataString = block.getBlockData().getAsString();
					boolean contains = false;

					// The "╚" sign is used as a temporary placeholder so that a spell that's
					// intended to cycle will only go one step forward in the cycle.
					for (int i = 0; i < replace.size(); i++) {
						if (blockDataString.contains(replace.get(i)) && replaceWith.size() > i) {
							blockDataString = blockDataString.replace(replace.get(i),
									replaceWith.get(i).replace("=", "╚"));
							contains = true;
						}
					}

					Lidded lidded = null;

					if (block.getState() instanceof Lidded)
						lidded = (Lidded) block.getState();

					boolean shouldOpen = false;
					boolean shouldClose = false;

					if (lidded != null) {
						if (replace.contains("open=false") && !lidded.isOpen()
								&& replaceWith.get(replace.indexOf("open=false")).equals("open=true")) {
							shouldOpen = true;
						} else if (replace.contains("open=true") && lidded.isOpen()
								&& replaceWith.get(replace.indexOf("open=true")).equals("open=false")) {
							shouldClose = true;
						}
					}

					if (contains || shouldOpen || shouldClose) {
						if (checkPlugins && caster instanceof Player player) {
							Block against = target.clone().add(target.getDirection()).getBlock();

							MagicSpellsBlockPlaceEvent event = new MagicSpellsBlockPlaceEvent(block, block.getState(),
									against, player.getInventory().getItemInMainHand(), player, true, bypassDippGen);

							EventUtil.call(event);
							if (event.isCancelled()) {
								continue;
							}
						}

						if (contains) {
							BlockData blockData = Bukkit.createBlockData(blockDataString.replace("╚", "="));

							block.setBlockData(blockData, false);
						}

						if (lidded != null) {
							if (shouldOpen) {
								lidded.open();
							} else if (shouldClose) {
								lidded.close();
							} else if (!contains)
								continue;
						}

						playSpellEffects(EffectPosition.SPECIAL, block.getLocation(), spellData);

						replaced = true;
					}
				}
			}
		}

		if (caster != null)
			playSpellEffects(caster, target, spellData);
		else
			playSpellEffects(EffectPosition.TARGET, target, spellData);

		return replaced;
	}

}
