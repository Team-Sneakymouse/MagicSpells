package com.nisovin.magicspells.spells.targeted;

import org.bukkit.DyeColor;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.ValidTargetChecker;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

//This spell currently support the shearing of sheep at the moment.
//Future tweaks for the shearing of other mobs will be added.

public class RegrowSpell extends TargetedSpell implements TargetedEntitySpell {

	private static final ValidTargetChecker SHEEP = entity -> entity instanceof Sheep;

	private DyeColor dye;

	private String requestedColor;

	private boolean forceWoolColor;
	private boolean randomWoolColor;
	private boolean configuredCorrectly;

	public RegrowSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		requestedColor = getConfigString("wool-color", "");

		forceWoolColor = getConfigBoolean("force-wool-color", false);
		randomWoolColor = getConfigBoolean("random-wool-color", false);

	}

	@Override
	public void initialize() {
		super.initialize();

		configuredCorrectly = parseSpell();
		if (!configuredCorrectly) MagicSpells.error("RegrowSpell " + internalName + " was configured incorrectly!");
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, SHEEP, args);
			if (target.noTarget()) return noTarget(caster, args, target);

			boolean done = grow(caster, (Sheep) target.target(), power, args);
			if (!done) return noTarget(caster, args);

			sendMessages(caster, target.target(), args);
			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Sheep sheep) || !validTargetList.canTarget(caster, target)) return false;
		return grow(caster, sheep, power, args);
	}


	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!(target instanceof Sheep sheep) || !validTargetList.canTarget(target)) return false;
		return grow(null, sheep, power, args);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
	}

	private boolean grow(LivingEntity caster, Sheep target, float power, String[] args) {
		if (!configuredCorrectly) return false;
		if (!target.isSheared()) return false;
		if (!target.isAdult()) return false;

		//If we are forcing a specific random wool color, lets set its color to this.
		if (forceWoolColor && randomWoolColor) target.setColor(randomizeDyeColor());
		else if (forceWoolColor && dye != null) target.setColor(dye);

		target.setSheared(false);

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);

		return true;
	}

	private DyeColor randomizeDyeColor() {
		DyeColor[] allDyes = DyeColor.values();
		int dyePosition = random.nextInt(allDyes.length);
		return allDyes[dyePosition];
	}

	private boolean parseSpell() {
		if (forceWoolColor && !requestedColor.isEmpty()) {
			try {
				dye = DyeColor.valueOf(requestedColor);
			} catch (IllegalArgumentException e) {
				MagicSpells.error("Invalid wool color defined. Will use sheep's color instead.");
				return false;
			}
		}
		return true;
	}

}
