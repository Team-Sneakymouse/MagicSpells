package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class CloseInventorySpell extends TargetedSpell implements TargetedEntitySpell {

	private final ConfigData<Integer> delay;

	public CloseInventorySpell(MagicConfig config, String spellName) {
		super(config, spellName);
		delay = getConfigDataInt("delay", 0);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<Player> targetInfo = getTargetedPlayer(data.caster(), data.power(), data.args());
					if (targetInfo.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);
					Player target = targetInfo.target();
		
					close(data.caster(), target, targetInfo.power(), data.args());
					sendMessages(data.caster(), target, data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							close(data.caster(), player, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							close(null, player, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private void close(LivingEntity caster, Player target, float power, String[] args) {
		int delay = this.delay.get(caster, target, power, args);

		if (delay > 0) {
			MagicSpells.scheduleDelayedTask(() -> {
				target.closeInventory();

				if (caster != null) playSpellEffects(caster, target, power, args);
				else playSpellEffects(EffectPosition.TARGET, target, power, args);
			}, delay);
		}
		else {
			target.closeInventory();

			if (caster != null) playSpellEffects(caster, target, power, args);
			else playSpellEffects(EffectPosition.TARGET, target, power, args);
		}
	}

}
