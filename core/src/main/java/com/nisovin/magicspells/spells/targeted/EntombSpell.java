package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.BlockBreakEvent;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;

public class EntombSpell extends TargetedSpell implements TargetedEntitySpell {

	private Map<Block, AlteredBlockManager.Change> blocks;

	private Material material;
	private String materialName;

	private ConfigData<Integer> duration;

	private boolean allowBreaking;
	private boolean closeTopAndBottom;

	private String blockDestroyMessage;

	public EntombSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		materialName = getConfigString("block-type", "glass");
		material = Util.getMaterial(materialName);
		if (material == null || !material.isBlock()) {
			MagicSpells.error("EntombSpell '" + internalName + "' has an invalid block defined!");
			material = null;
		}

		duration = getConfigDataInt("duration", 20);

		allowBreaking = getConfigBoolean("allow-breaking", true);
		closeTopAndBottom = getConfigBoolean("close-top-and-bottom", true);

		blockDestroyMessage = getConfigString("block-destroy-message", "");

		blocks = new HashMap<>();
	}

	@Override
	public void turnOff() {
		super.turnOff();

		for (AlteredBlockManager.Change change : blocks.values()) {
			change.undo(false);
			playSpellEffects(EffectPosition.BLOCK_DESTRUCTION, change.block().getLocation(), null);
		}
		blocks.clear();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> targetInfo = getTargetedEntity(caster, power, args);
			if (targetInfo.noTarget())
				return noTarget(caster, args, targetInfo);

			LivingEntity target = targetInfo.target();
			power = targetInfo.power();

			createTomb(caster, target, power, args);
			sendMessages(caster, target, args);
			playSpellEffects(caster, target, power, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		createTomb(caster, target, power, args);
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		createTomb(null, target, power, args);
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	private void createTomb(LivingEntity caster, LivingEntity target, float power, String[] args) {
		List<Block> tempBlocks = new ArrayList<>();
		List<AlteredBlockManager.Change> tombChanges = new ArrayList<>();

		Block feet = target.getLocation().getBlock();
		float pitch = target.getLocation().getPitch();
		float yaw = target.getLocation().getYaw();

		Location tpLoc = feet.getLocation().add(0.5, 0, 0.5);
		tpLoc.setYaw(yaw);
		tpLoc.setPitch(pitch);
		target.teleportAsync(tpLoc);

		tempBlocks.add(feet.getRelative(1, 0, 0));
		tempBlocks.add(feet.getRelative(1, 1, 0));
		tempBlocks.add(feet.getRelative(-1, 0, 0));
		tempBlocks.add(feet.getRelative(-1, 1, 0));
		tempBlocks.add(feet.getRelative(0, 0, 1));
		tempBlocks.add(feet.getRelative(0, 1, 1));
		tempBlocks.add(feet.getRelative(0, 0, -1));
		tempBlocks.add(feet.getRelative(0, 1, -1));

		if (closeTopAndBottom) {
			tempBlocks.add(feet.getRelative(0, -1, 0));
			tempBlocks.add(feet.getRelative(0, 2, 0));
		}

		SpellData data = new SpellData(caster, target, tpLoc, power, args);
		for (Block b : tempBlocks) {
			if (!BlockUtils.isAir(b.getType()))
				continue;
			AlteredBlockManager.Change change = MagicSpells.getAlteredBlockManager().apply(internalName, b, material,
					false);
			tombChanges.add(change);
			blocks.put(b, change);
			playSpellEffects(EffectPosition.SPECIAL, b.getLocation().add(0.5, 0.5, 0.5), data);
		}

		int duration = this.duration.get(caster, target, power, args);
		if (duration > 0 && !tombChanges.isEmpty()) {
			MagicSpells.scheduleDelayedTask(() -> removeTomb(tombChanges, data), Math.round(duration * power));
		}
	}

	private void removeTomb(List<AlteredBlockManager.Change> entomb, SpellData data) {
		for (AlteredBlockManager.Change change : entomb) {
			change.undo(false);
			playSpellEffects(EffectPosition.BLOCK_DESTRUCTION, change.block().getLocation().add(0.5, 0.5, 0.5), data);
			blocks.remove(change.block());
		}
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		AlteredBlockManager.Change change = blocks.get(event.getBlock());
		if (change == null)
			return;
		event.setCancelled(true);
		if (allowBreaking) {
			change.undo(false);
			blocks.remove(event.getBlock());
		}
		if (!blockDestroyMessage.isEmpty())
			MagicSpells.sendMessage(event.getPlayer(), blockDestroyMessage);
	}

}
