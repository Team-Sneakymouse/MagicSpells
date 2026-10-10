package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.ValidTargetChecker;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class AgeSpell extends TargetedSpell implements TargetedEntitySpell {

	private static final ValidTargetChecker AGEABLE = entity -> entity instanceof Ageable;

	private ConfigData<Integer> rawAge;

	private boolean setMaturity;
	private boolean applyAgeLock;

	public AgeSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		rawAge = getConfigDataInt("age", 0);
		setMaturity = getConfigBoolean("set-maturity", true);
		applyAgeLock = getConfigBoolean("apply-age-lock", false);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> info = getTargetedEntity(caster, power, AGEABLE, args);
			if (info.noTarget()) return noTarget(caster, args, info);

			applyAgeChanges(caster, (Ageable) info.target(), info.power(), args);
			sendMessages(caster, info.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target) || !(target instanceof Ageable ageable)) return false;
		applyAgeChanges(caster, ageable, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target) || !(target instanceof Ageable ageable)) return false;
		applyAgeChanges(null, ageable, power, args);
		return true;
	}

	private void applyAgeChanges(LivingEntity caster, Ageable target, float power, String[] args) {
		if (setMaturity) target.setAge(rawAge.get(caster, target, power, args));
		if (target instanceof Breedable breedable) breedable.setAgeLock(applyAgeLock);

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);
	}

}
