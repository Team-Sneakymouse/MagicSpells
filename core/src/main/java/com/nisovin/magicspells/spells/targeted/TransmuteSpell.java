package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

public class TransmuteSpell extends TargetedSpell implements TargetedLocationSpell {

	private List<Material> blockTypes;

	private Material transmuteType;
	
	public TransmuteSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		List<String> list = getConfigStringList("transmutable-types", null);
		blockTypes = new ArrayList<>();
		if (list != null && !list.isEmpty()) {
			for (String s : list) {
				Material material = Util.getMaterial(s);
				if (material == null || !material.isBlock()) continue;
				blockTypes.add(material);
			}
		} else blockTypes.add(Material.IRON_BLOCK);

		String materialName = getConfigString("transmute-type", "gold_block");
		transmuteType = Util.getMaterial(materialName);
		if (transmuteType == null || !transmuteType.isBlock()) {
			MagicSpells.error("TransmuteSpell '" + internalName + "' has an transmute-type defined!");
			transmuteType = null;
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		Block block = getTargetedBlock(data.caster(), data.power(), data.args());
		if (block == null)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(block.getLocation()));
		EventUtil.call(event);
		if (event.isCancelled())
			return new CastResult(noTarget(data.caster(), data.args()), data);

		data = event.getSpellData();
		block = data.location().getBlock();

		if (!canTransmute(block))
			return new CastResult(noTarget(data.caster(), data.args()), data);

		return castAtLocation(data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		Block block = data.location().getBlock();
		if (canTransmute(block)) {
			block.setType(transmuteType);
			Location effectLoc = block.getLocation().add(0.5, 0.5, 0.5);
			if (data.hasCaster())
				playSpellEffects(data.caster(), effectLoc, data);
			else
				playSpellEffects(EffectPosition.TARGET, effectLoc, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		Vector v = data.location().getDirection();
		block = data.location().clone().add(v).getBlock();
		if (canTransmute(block)) {
			block.setType(transmuteType);
			Location effectLoc = block.getLocation().add(0.5, 0.5, 0.5);
			if (data.hasCaster())
				playSpellEffects(data.caster(), effectLoc, data);
			else
				playSpellEffects(EffectPosition.TARGET, effectLoc, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
		return new CastResult(PostCastAction.ALREADY_HANDLED, data);
	}



	private boolean canTransmute(Block block) {
		for (Material m : blockTypes) {
			if (m.equals(block.getType())) return true;
		}
		return false;
	}

}
