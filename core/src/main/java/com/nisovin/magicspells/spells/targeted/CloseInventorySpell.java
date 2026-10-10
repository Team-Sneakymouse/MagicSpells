package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
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
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<Player> targetInfo = getTargetedPlayer(caster, power, args);
			if (targetInfo.noTarget()) return noTarget(caster, args, targetInfo);
			Player target = targetInfo.target();

			close(caster, target, targetInfo.power(), args);
			sendMessages(caster, target, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(caster, target)) return false;
		close(caster, player, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(target)) return false;
		close(null, player, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
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
