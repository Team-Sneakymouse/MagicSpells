package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Slime;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.VariableMod;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.ValidTargetChecker;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.util.config.ConfigData;

public class SlimeSizeSpell extends TargetedSpell implements TargetedEntitySpell {

	private static final ValidTargetChecker SLIME = entity -> entity instanceof Slime;

	private VariableMod variableMod;

	private String size;

	private ConfigData<Integer> minSize;
	private ConfigData<Integer> maxSize;

	private static ValidTargetChecker isSlimeChecker = (LivingEntity entity) -> entity instanceof Slime;

	public SlimeSizeSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		size = getConfigString("size", "=5");

		minSize = getConfigDataInt("min-size", 0);
		maxSize = getConfigDataInt("max-size", 20);
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		variableMod = new VariableMod(size);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> info = getTargetedEntity(caster, power, SLIME, args);
			if (info.noTarget()) return noTarget(caster, args, info);

			if (!setSize(caster, info.target(), info.power(), args)) return noTarget(caster, args);

			sendMessages(caster, info.target(), args);
			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		return setSize(caster, target, power, args);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return false;
	}

	@Override
	public ValidTargetChecker getValidTargetChecker() {
		return isSlimeChecker;
	}

	private boolean setSize(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Slime slime) || !(caster instanceof Player player)) return false;

		int minSize = this.minSize.get(caster, target, power, args);
		int maxSize = this.maxSize.get(caster, target, power, args);

		if (minSize < 0) minSize = 0;
		if (maxSize < minSize) maxSize = minSize;

		double rawOutputValue = variableMod.getValue(player, null, slime.getSize(), power, args);
		int finalSize = Util.clampValue(minSize, maxSize, (int) rawOutputValue);
		slime.setSize(finalSize);

		playSpellEffects(caster, target, power, args);

		return true;
	}

}
