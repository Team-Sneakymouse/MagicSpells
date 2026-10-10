package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import com.nisovin.magicspells.util.managers.AlteredBlockManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.block.data.BlockData;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.MagicSpellsBlockPlaceEvent;

public class ReplaceSpell extends TargetedSpell implements TargetedLocationSpell {

	private boolean replaceAll;
	private List<List<BlockData>> replace;
	private List<List<BlockData>> replaceWith;
	private List<BlockData> replaceBlacklist;

	private ConfigData<Integer> yOffset;
	private ConfigData<Integer> radiusUp;
	private ConfigData<Integer> radiusDown;
	private ConfigData<Integer> radiusHoriz;
	private ConfigData<Integer> replaceDuration;

	private boolean pointBlank;
	private boolean replaceRandom;
	private ConfigData<Boolean> powerAffectsRadius;
	private final boolean checkPlugins;
	private final boolean applyPhysics;
	private final boolean checkIsSupported;
	private boolean resolveDurationPerBlock;
	private boolean circleShape;
	private boolean mergeBlockData;
	private boolean affectsContainers;

	public ReplaceSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		replace = new ArrayList<>();
		replaceWith = new ArrayList<>();
		replaceBlacklist = new ArrayList<>();

		yOffset = getConfigDataInt("y-offset", 0);
		radiusUp = getConfigDataInt("radius-up", 1);
		radiusDown = getConfigDataInt("radius-down", 1);
		radiusHoriz = getConfigDataInt("radius-horiz", 1);
		replaceDuration = getConfigDataInt("duration", 0);

		pointBlank = getConfigBoolean("point-blank", false);
		checkPlugins = getConfigBoolean("check-plugins", true);
		applyPhysics = getConfigBoolean("apply-physics", false);
		checkIsSupported = getConfigBoolean("check-is-supported", applyPhysics);
		replaceRandom = getConfigBoolean("replace-random", true);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", false);
		resolveDurationPerBlock = getConfigBoolean("resolve-duration-per-block", false);
		circleShape = getConfigBoolean("circle-shape", false);
		mergeBlockData = getConfigBoolean("merge-block-data", true);
		affectsContainers = getConfigBoolean("affects-containers", false);

		List<String> list = getConfigStringList("replace-blocks", null);
		if (list != null) {
			replaceAll = false;
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).equals("all")) {
					replaceAll = true;
					// Just a filler.
					replace.add(null);
					break;
				}

				List<BlockData> blockList = new ArrayList<BlockData>();
				String[] split = list.get(i).split("\\|");
				for (String block : split) {
					try {
						BlockData data = Bukkit.createBlockData(block.trim().toLowerCase());
						blockList.add(data);
					} catch (IllegalArgumentException e) {
						MagicSpells.error(
								"ReplaceSpell " + internalName + " has an invalid replace-blocks item: " + block);
					}
				}
				replace.add(blockList);
			}
		}

		list = getConfigStringList("replace-with", null);
		if (list != null) {
			for (int i = 0; i < list.size(); i++) {
				List<BlockData> blockList = new ArrayList<BlockData>();
				String[] split = list.get(i).split("\\|");

				for (String block : split) {
					try {
						int n = 1;

						String[] blockSplit = block.split("%");
						String blockName = null;

						if (blockSplit.length == 2) {
							n = Integer.valueOf(blockSplit[0]);
							blockName = blockSplit[1];
						} else {
							blockName = blockSplit[0];
						}

						BlockData data = null;

						if (!blockName.equals("same"))
							data = Bukkit.createBlockData(blockName.trim().toLowerCase());

						for (int j = 0; j < n; j++) {
							blockList.add(data);
						}
					} catch (IllegalArgumentException e) {
						MagicSpells
								.error("ReplaceSpell " + internalName + " has an invalid replace-with item: " + block);
					}
				}
				replaceWith.add(blockList);
			}
		}

		list = getConfigStringList("replace-blacklist", null);
		if (list != null) {
			for (String s : list) {
				try {
					BlockData data = Bukkit.createBlockData(s.trim().toLowerCase());
					replaceBlacklist.add(data);
				} catch (IllegalArgumentException e) {
					MagicSpells.error("ReplaceSpell " + internalName + " has an invalid replace-blacklist item: " + s);
				}
			}
		}

		if (!replaceRandom && replace.size() != replaceWith.size()) {
			replaceRandom = true;
			MagicSpells.error("ReplaceSpell " + internalName
					+ " replace-random false, but replace-blocks and replace-with have different sizes!");
		}

		if (replace.isEmpty())
			MagicSpells.error("ReplaceSpell " + internalName + " has empty replace-blocks list!");
		if (replaceWith.isEmpty())
			MagicSpells.error("ReplaceSpell " + internalName + " has empty replace-with list!");
	}

	@Override
	public void turnOff() {
		MagicSpells.getAlteredBlockManager().undoAll(internalName, applyPhysics);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			Block target = pointBlank ? caster.getLocation().getBlock() : getTargetedBlock(caster, power, args);
			if (target == null)
				return noTarget(caster, args);
			replace(caster, target.getLocation(), power, args);
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return replace(caster, target, power, args);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return replace(null, target, power, args);
	}

	private boolean replace(LivingEntity caster, Location target, float power, String[] args) {
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
		int yOffset = this.yOffset.get(spellData);
		int replaceDuration = resolveDurationPerBlock ? 0 : this.replaceDuration.get(spellData);

		List<BlockData> allReplaceWithBlocks = new ArrayList<BlockData>();

		if (replaceRandom) {
			for (List<BlockData> blockList : replaceWith) {
				for (BlockData blockData : blockList) {
					allReplaceWithBlocks.add(blockData);
				}
			}
		}

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
					for (int i = 0; i < replace.size(); i++) {
						BlockData data = block.getBlockData();

						// If specific blocks are being replaced, skip if the block isn't replaceable.
						if (!replaceAll) {
							Boolean cont = true;

							for (BlockData replaceData : replace.get(i)) {
								if (data.matches(replaceData)) {
									cont = false;
								}
							}
							if (cont)
								continue;
						}

						// If all blocks are being replaced, skip if the block is already replaced.
						if (replaceAll) {
							Boolean cont = false;

							for (BlockData replaceData : replaceWith.get(i)) {
								if (data.matches(replaceData)) {
									cont = true;
								}
							}
							if (cont || (!affectsContainers && BlockUtils.isContainer(block)))
								continue;
						}

						if (replaceBlacklisted(data))
							continue;

						Block finalBlock = block;
						BlockState previousState = block.getState();

						// Place block.
						BlockData newBlockData = null;
						if (replaceRandom)
							newBlockData = allReplaceWithBlocks.get(Util.getRandomInt(allReplaceWithBlocks.size()));
						else
							newBlockData = replaceWith.get(i).get(Util.getRandomInt(replaceWith.get(i).size()));

						if (newBlockData == null)
							continue;

						if (checkIsSupported && !newBlockData.isSupported(block.getLocation()))
							continue;

						if (locationModifiers != null) {
							ModifierResult modifierResult = locationModifiers.apply(caster, target, spellData);
							if (!modifierResult.check()) {
								continue;
							}
						}

						BlockData merged = BlockUtils.mergeBlockData(data, newBlockData, mergeBlockData);

						if (resolveDurationPerBlock)
							replaceDuration = this.replaceDuration.get(spellData);

						AlteredBlockManager.Change change = null;
						if (replaceDuration > 0) {
							change = MagicSpells.getAlteredBlockManager().apply(internalName, block, merged, applyPhysics);
						} else {
							block.setBlockData(merged, applyPhysics);
						}

						if (checkPlugins && caster instanceof Player player) {
							Block against = target.clone().add(target.getDirection()).getBlock();
							if (block.equals(against))
								against = block.getRelative(BlockFace.DOWN);
							MagicSpellsBlockPlaceEvent event = new MagicSpellsBlockPlaceEvent(block, previousState,
									against, player.getInventory().getItemInMainHand(), player, true, bypassDippGen);
							EventUtil.call(event);
							if (event.isCancelled()) {
								if (change != null) change.undo(applyPhysics);
								else previousState.update(true);
								return false;
							}
						}
						spellData.setLocation(finalBlock.getLocation());
						playSpellEffects(EffectPosition.SPECIAL, finalBlock.getLocation(), spellData);

						if (change != null) {
							AlteredBlockManager.Change finalChange = change;
							MagicSpells.scheduleDelayedTask(() -> {
								finalChange.undo(applyPhysics);
								playSpellEffects(EffectPosition.BLOCK_DESTRUCTION, finalBlock.getLocation(), spellData);
							}, replaceDuration);
						}

						replaced = true;
						break;
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

	private boolean replaceBlacklisted(BlockData data) {
		for (BlockData blockData : replaceBlacklist)
			if (data.matches(blockData))
				return true;

		return false;
	}
}
