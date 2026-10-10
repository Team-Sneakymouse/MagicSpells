package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
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
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), data.args());
					if (target.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), target), data);
					
					switchPlaces(data.caster(), target.target(), target.power(), data.args());
					sendMessages(data.caster(), target.target(), data.args());
		
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
		if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				switchPlaces(data.caster(), data.target(), data.power(), data.args());
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
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
