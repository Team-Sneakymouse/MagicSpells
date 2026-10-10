package com.nisovin.magicspells.spells.targeted;

import org.bukkit.DyeColor;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
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
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), SHEEP, data.args());
					if (target.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), target), data);
		
					boolean done = grow(data.caster(), (Sheep) target.target(), data.power(), data.args());
					if (!done) return new CastResult(noTarget(data.caster(), data.args()), data);
		
					sendMessages(data.caster(), target.target(), data.args());
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!(data.target() instanceof Sheep sheep) || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			if (!(grow(data.caster(), sheep, data.power(), data.args()))) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!(data.target() instanceof Sheep sheep) || !validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			if (!(grow(null, sheep, data.power(), data.args()))) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
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
