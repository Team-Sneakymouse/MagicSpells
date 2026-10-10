package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class SlotSelectSpell extends TargetedSpell implements TargetedEntitySpell {

	private boolean isVariable = false;

	private String variable;
	private int slot;
	private final boolean ignoreSlotBounds;

	public SlotSelectSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		if (isConfigString("slot")) {
			isVariable = true;
			variable = getConfigString("slot", null);
		} else slot = getConfigInt("slot", 0);
		ignoreSlotBounds = getConfigBoolean("ignore-slot-bounds", false);
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		if (isVariable && (variable == null || variable.isEmpty() || MagicSpells.getVariableManager().getVariable(variable) == null)) {
			MagicSpells.error("SlotSelectSpell '" + internalName + "' has an invalid variable specified in 'slot'!");
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		TargetInfo<Player> info = getTargetedPlayer(data.caster(), data.power(), data.args());
		if (info.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), info), data);

		data = data.target(info.target()).power(info.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		sendMessages(data.caster(), data.target(), data.args());
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
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			boolean ok = slotChange(data.caster(), data.target(), data.power(), data.args());
			return new CastResult(ok ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
		}
		if (!validTargetList.canTarget(data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		boolean ok = slotChange(null, data.target(), data.power(), data.args());
		return new CastResult(ok ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
	}



	private boolean slotChange(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player)) return false;

		int newSlot = -1;
		if (isVariable) {
			if (variable == null || variable.isEmpty() || MagicSpells.getVariableManager().getVariable(variable) == null) {
				MagicSpells.error("SlotSelectSpell '" + internalName + "' has an invalid variable specified in 'slot'!");
			} else newSlot = (int) Math.round(MagicSpells.getVariableManager().getValue(variable, player));
		} else newSlot = slot;

		try {
			player.getInventory().setHeldItemSlot(newSlot);
		} catch(IllegalArgumentException e) {
			if (!ignoreSlotBounds) {
				MagicSpells.error("SlotSelectSpell '" + internalName + "' attempted to set to a slot outside bounds (0-8)! If this is intended, set 'ignore-slot-bounds' to true.");
			}
		}

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);

		return true;
	}

}
