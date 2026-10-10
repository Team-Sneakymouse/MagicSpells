package com.nisovin.magicspells.spells.targeted;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.ValidTargetChecker;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

//This spell currently support the shearing of sheep at the moment.
//Future tweaks for the shearing of other mobs will be added.

public class ShearSpell extends TargetedSpell implements TargetedEntitySpell {

	private static final ValidTargetChecker SHEEP = entity -> entity instanceof Sheep;
	private static final DyeColor[] colors = DyeColor.values();

	private DyeColor dye;

	private String requestedColor;

	private ConfigData<Integer> minWool;
	private ConfigData<Integer> maxWool;

	private ConfigData<Double> dropOffset;

	private boolean forceWoolColor;
	private boolean randomWoolColor;
	private boolean configuredCorrectly;

	public ShearSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		requestedColor = getConfigString("wool-color", "");

		minWool = getConfigDataInt("min-wool-drop", 1);
		maxWool = getConfigDataInt("max-wool-drop", 3);

		dropOffset = getConfigDataDouble("drop-offset", 1);

		forceWoolColor = getConfigBoolean("force-wool-color", false);
		randomWoolColor = getConfigBoolean("random-wool-color", false);
	}

	@Override
	public void initialize() {
		super.initialize();

		configuredCorrectly = parseSpell();
		if (!configuredCorrectly) MagicSpells.error("ShearSpell " + internalName + " was configured incorrectly!");
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), SHEEP, data.args());
					if (target.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), target), data);
		
					boolean done = shear(data.caster(), (Sheep) target.target(), target.power(), data.args());
					if (!done) return new CastResult(noTarget(data.caster(), data.args()), data);
		
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
		if (data.hasCaster()) {
			if (!(data.target() instanceof Sheep sheep && validTargetList.canTarget(data.caster(), data.target()) && shear(data.caster(), sheep, data.power(), data.args()))) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!(data.target() instanceof Sheep sheep && validTargetList.canTarget(data.target()) && shear(null, sheep, data.power(), data.args()))) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private boolean parseSpell() {
		if (forceWoolColor && requestedColor != null) {
			try {
				dye = DyeColor.valueOf(requestedColor.toUpperCase());
			} catch (IllegalArgumentException e) {
				MagicSpells.error("Invalid wool color defined. Will use sheep's color instead.");
				requestedColor = null;
				return false;
			}
		}
		return true;
	}

	private boolean shear(LivingEntity caster, Sheep sheep, float power, String[] args) {
		if (!configuredCorrectly || sheep.isSheared() || !sheep.isAdult()) return false;

		DyeColor color = null;
		if (forceWoolColor) {
			if (randomWoolColor) color = colors[Util.getRandomInt(colors.length)];
			else if (dye != null) color = dye;
		} else color = sheep.getColor();
		if (color == null) color = DyeColor.WHITE;
		Material woolColor = Material.getMaterial(color.name() + "_WOOL");
		if (woolColor == null) woolColor = Material.WHITE_WOOL;

		int maxWool = this.maxWool.get(caster, sheep, power, args);
		int minWool = this.minWool.get(caster, sheep, power, args);
		int count;
		if (maxWool != 0) count = random.nextInt((maxWool - minWool) + 1) + minWool;
		else count = random.nextInt(minWool + 1);

		sheep.setSheared(true);
		sheep.getWorld().dropItemNaturally(sheep.getLocation().add(0, dropOffset.get(caster, sheep, power, args), 0), new ItemStack(woolColor, count));

		if (caster != null) playSpellEffects(caster, sheep, power, args);
		else playSpellEffects(EffectPosition.TARGET, sheep, power, args);

		return true;
	}

}
