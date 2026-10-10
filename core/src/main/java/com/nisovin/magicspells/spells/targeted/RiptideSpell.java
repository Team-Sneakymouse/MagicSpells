package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class RiptideSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Integer> duration;

	public RiptideSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		duration = getConfigDataInt("duration", 40);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<Player> info = getTargetedPlayer(caster, power, args);
			if (info.noTarget()) return noTarget(caster, args, info);

			Player target = info.target();
			power = info.power();

			target.startRiptideAttack(duration.get(caster, target, power, args), 0, null);
			playSpellEffects(caster, target, power, args);
			sendMessages(caster, target, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(caster, target)) return false;

		player.startRiptideAttack(duration.get(player, target, power, args), 0, null);
		playSpellEffects(caster, target, power, args);

		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(target)) return false;

		player.startRiptideAttack(duration.get(player, target, power, args), 0, null);
		playSpellEffects(EffectPosition.TARGET, target, power, args);

		return true;
	}

}
