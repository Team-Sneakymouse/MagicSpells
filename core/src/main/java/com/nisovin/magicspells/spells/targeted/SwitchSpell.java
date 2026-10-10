package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

public class SwitchSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Integer> switchBack;
	
	public SwitchSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		switchBack = getConfigDataInt("switch-back", 0);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
			if (target.noTarget()) return noTarget(caster, args, target);
			
			switchPlaces(caster, target.target(), target.power(), args);
			sendMessages(caster, target.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		switchPlaces(caster, target, power, args);
		return true;
	}

	private void switchPlaces(LivingEntity caster, final LivingEntity target, float power, String[] args) {
		Location targetLoc = target.getLocation();
		Location casterLoc = caster.getLocation();
		caster.teleportAsync(targetLoc);
		target.teleportAsync(casterLoc);

		int switchBack = this.switchBack.get(caster, target, power, args);
		if (switchBack <= 0) return;

		playSpellEffects(caster, target, power, args);

		MagicSpells.scheduleDelayedTask(() -> {
			if (caster.isDead() || target.isDead()) return;
			Location targetLoc1 = target.getLocation();
			Location casterLoc1 = caster.getLocation();
			caster.teleportAsync(targetLoc1);
			target.teleportAsync(casterLoc1);
		}, switchBack);
	}

}
