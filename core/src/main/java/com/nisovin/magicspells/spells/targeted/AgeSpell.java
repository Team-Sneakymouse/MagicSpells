package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
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
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), AGEABLE, data.args());
					if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);
		
					applyAgeChanges(data.caster(), (Ageable) info.target(), info.power(), data.args());
					sendMessages(data.caster(), info.target(), data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()) || !(data.target() instanceof Ageable ageable)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							applyAgeChanges(data.caster(), ageable, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target()) || !(data.target() instanceof Ageable ageable)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							applyAgeChanges(null, ageable, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private void applyAgeChanges(Entity caster, Ageable target, float power, String[] args) {
		if (setMaturity) target.setAge(rawAge.get(caster, target, power, args));
		if (target instanceof Breedable breedable) breedable.setAgeLock(applyAgeLock);

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);
	}

}
