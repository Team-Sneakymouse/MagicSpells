package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;

import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

public class DummySpell extends TargetedSpell
		implements TargetedEntitySpell, TargetedLocationSpell, TargetedEntityFromLocationSpell {

	private ConfigData<Boolean> requireEntityTarget;

	public DummySpell(MagicConfig config, String spellName) {
		super(config, spellName);
		requireEntityTarget = getConfigDataBoolean("require-entity-target", true);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (Boolean.TRUE.equals(requireEntityTarget.get(data))) {
			TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), data.args());
			if (target.noTarget())
				return new CastResult(noTarget(data.caster(), data.args(), target), data);

			data = data.target(target.target()).power(target.power());
			playSpellEffects(data.caster(), data.target(), data);
			sendMessages(data.caster(), data.target(), data.args());
			return new CastResult(PostCastAction.NO_MESSAGES, data);
		}

		Location loc = data.location();
		if (loc == null && data.caster() != null) {
			try {
				Block block = getTargetedBlock(data.caster(), data.power(), data.args());
				if (block != null && !BlockUtils.isAir(block.getType()))
					loc = block.getLocation().add(0.5, 0, 0.5);
			} catch (IllegalStateException ignored) {
			}
		}

		if (loc == null)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(loc));
		EventUtil.call(event);
		if (event.isCancelled())
			return new CastResult(noTarget(data.caster(), data.args()), data);

		data = event.getSpellData();
		playSpellEffects(data.caster(), data.location(), data);
		sendMessages(data.caster(), data.location(), data.args());
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
			playSpellEffects(data.caster(), data.target(), data);
		} else {
			if (!validTargetList.canTarget(data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			playSpellEffects(EffectPosition.TARGET, data.target(), data);
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	@Override
	public CastResult castAtLocation(SpellData data) {
		if (data.hasCaster())
			playSpellEffects(data.caster(), data.location(), data);
		else
			playSpellEffects(EffectPosition.TARGET, data.location(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			playSpellEffects(data.caster(), data.location(), data.target(), data);
		} else {
			if (!validTargetList.canTarget(data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			playSpellEffects(data.location(), data.target(), data);
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		return true;
	}

	/** Only meaningful when {@code require-entity-target} is a constant expression. */
	public boolean isRequireEntityTarget() {
		return Boolean.TRUE.equals(requireEntityTarget.get(null, null, null, 1f, null));
	}

	public void setRequireEntityTarget(boolean requireEntityTarget) {
		this.requireEntityTarget = (c, t, l, p, a) -> requireEntityTarget;
	}

}
