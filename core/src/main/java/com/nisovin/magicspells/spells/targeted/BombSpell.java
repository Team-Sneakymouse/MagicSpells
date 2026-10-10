package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellAnimation;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;

public class BombSpell extends TargetedSpell implements TargetedLocationSpell {

	private Map<Block, AlteredBlockManager.Change> blocks;

	private Material material;
	private String materialName;

	private ConfigData<Integer> fuse;
	private ConfigData<Integer> interval;

	private Subspell targetSpell;
	private String targetSpellName;

	public BombSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		materialName = getConfigString("block", "stone");
		material = Util.getMaterial(materialName);
		if (material == null || !material.isBlock()) {
			MagicSpells.error("BombSpell '" + internalName + "' has an invalid block defined!");
			material = null;
		}

		fuse = getConfigDataInt("fuse", 100);
		interval = getConfigDataInt("interval", 20);

		targetSpellName = getConfigString("spell", "");

		blocks = new HashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		targetSpell = new Subspell(targetSpellName);
		if (!targetSpell.process()) {
			if (!targetSpellName.isEmpty())
				MagicSpells.error("BombSpell '" + internalName + "' has an invalid spell defined!");
			targetSpell = null;
		}
	}

	@Override
	public void turnOff() {
		super.turnOff();

		for (AlteredBlockManager.Change change : blocks.values()) {
			change.undo(false);
		}

		blocks.clear();
	}

	@Override
	public CastResult cast(SpellData data) {
		List<Block> blocks = getLastTwoTargetedBlocks(data.caster(), data.power(), data.args());
		if (blocks.size() != 2)
			return new CastResult(noTarget(data.caster(), data.args()), data);
		if (!blocks.get(1).getType().isSolid())
			return new CastResult(noTarget(data.caster(), data.args()), data);

		Block target = blocks.get(0);
		if (!bomb(data.location(target.getLocation())))
			return new CastResult(noTarget(data.caster(), data.args()), data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		if (!bomb(data))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	private boolean bomb(SpellData data) {
		LivingEntity livingEntity = data.caster();
		Location loc = data.location();
		float power = data.power();
		String[] args = data.args();
		if (material == null)
			return false;
		Block block = loc.getBlock();
		if (!BlockUtils.isAir(block.getType()))
			return false;

		AlteredBlockManager.Change change = MagicSpells.getAlteredBlockManager().apply(internalName, block, material,
				false);
		blocks.put(block, change);

		final SpellData effectData = data.location(loc.clone().add(0.5, 0, 0.5));
		if (livingEntity != null)
			playSpellEffects(livingEntity, effectData.location(), effectData);
		else
			playSpellEffects(EffectPosition.TARGET, effectData.location(), effectData);

		final int interval = this.interval.get(livingEntity, null, power, args);
		final int fuse = this.fuse.get(livingEntity, null, power, args);
		new SpellAnimation(interval, interval, true, false) {

			private final Location l = block.getLocation().add(0.5, 0, 0.5);
			private int time = 0;

			@Override
			protected void onTick(int tick) {
				time += interval;
				if (time >= fuse) {
					stop(true);
					if (material.equals(block.getType())) {
						blocks.remove(block);
						change.undo(false);
						playSpellEffects(EffectPosition.DELAYED, l, effectData);
						if (targetSpell != null)
							targetSpell.subcast(effectData.location(l));
					}
				} else if (!material.equals(block.getType()))
					stop(true);
				else
					playSpellEffects(EffectPosition.SPECIAL, l, effectData);
			}

		};

		return true;
	}

}
