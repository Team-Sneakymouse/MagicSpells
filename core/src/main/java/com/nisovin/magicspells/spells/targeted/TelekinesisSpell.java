package com.nisovin.magicspells.spells.targeted;

import java.util.HashSet;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsPlayerInteractEvent;

public class TelekinesisSpell extends TargetedSpell implements TargetedLocationSpell {
	
	private boolean checkPlugins;
	
	public TelekinesisSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		checkPlugins = getConfigBoolean("check-plugins", true);
		
		losTransparentBlocks = new HashSet<>(losTransparentBlocks);
		losTransparentBlocks.remove(Material.LEVER);

		for (Material material : Material.values()) {
			if (!material.name().toUpperCase().contains("PRESSURE_PLATE")
					&& !material.name().toUpperCase().contains("BUTTON")) continue;
			losTransparentBlocks.remove(material);
		}
	}
	
	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		Block target = getTargetedBlock(data.caster(), data.power(), data.args());
		if (target == null)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(target.getLocation()));
		if (!event.callEvent())
			return new CastResult(noTarget(data.caster(), data.args()), data);

		data = event.getSpellData();
		target = data.location().getBlock();

		CastResult result = castAtLocation(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!activate(player, data.location().getBlock()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		playSpellEffects(data.caster(), data.location(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	private boolean checkPlugins(Player caster, Block target) {
		if (!checkPlugins) return true;
		MagicSpellsPlayerInteractEvent event = new MagicSpellsPlayerInteractEvent(caster, Action.RIGHT_CLICK_BLOCK, caster.getEquipment().getItemInMainHand(), target, BlockFace.SELF);
		EventUtil.call(event);
		return event.useInteractedBlock() != Result.DENY;
	}

	private boolean activate(Player caster, Block target) {
		Material targetType = target.getType();
		if (targetType == Material.LEVER || BlockUtils.isButton(targetType)) {
			if (!checkPlugins(caster, target)) return false;
			BlockUtils.activatePowerable(target);
			return true;
		} else if (BlockUtils.isPressurePlate(targetType)) {
			if (!checkPlugins(caster, target)) return false;
			BlockUtils.activatePowerable(target);
			return true;
		}
		return false;
	}

}
