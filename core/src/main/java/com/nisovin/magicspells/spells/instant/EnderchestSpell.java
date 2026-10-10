package com.nisovin.magicspells.spells.instant;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.PlayerNameUtils;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

public class EnderchestSpell extends InstantSpell implements TargetedEntitySpell {

	public EnderchestSpell(MagicConfig config, String spellName) {
		super(config, spellName);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (data.args() != null && data.args().length == 1 && player.hasPermission("magicspells.advanced." + internalName)) {
						Player target = PlayerNameUtils.getPlayer(data.args()[0]);
						if (target == null) {
							player.sendMessage("Invalid player target");
							return new CastResult(PostCastAction.ALREADY_HANDLED, data);
						}
						player.openInventory(target.getEnderChest());
					} else player.openInventory(player.getEnderChest());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		
				if (!(data.caster() instanceof Player playerCaster) || !(data.target() instanceof Player playerTarget)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				playerCaster.openInventory(playerTarget.getEnderChest());
		
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

}
