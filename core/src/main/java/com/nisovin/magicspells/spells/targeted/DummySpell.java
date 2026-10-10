package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;

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
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			if (Boolean.TRUE.equals(requireEntityTarget.get(caster, power, args))) {
				TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
				if (target.noTarget())
					return noTarget(caster, args, target);

				playSpellEffects(caster, target.target(), target.power(), args);
				sendMessages(caster, target.target(), args);

				return PostCastAction.NO_MESSAGES;
			} else {
				Location loc = getCurrentCastLocation();
				if (loc == null && caster != null) {
					try {
						Block block = getTargetedBlock(caster, power, args);
						if (block != null && !BlockUtils.isAir(block.getType()))
							loc = block.getLocation().add(0.5, 0, 0.5);
					} catch (IllegalStateException ignored) {
					}
				}

				if (loc == null)
					return noTarget(caster, args);

				SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, caster, loc, power, args);
				EventUtil.call(event);
				if (event.isCancelled())
					loc = null;
				else {
					loc = event.getTargetLocation();
					power = event.getPower();
				}

				if (loc == null)
					return noTarget(caster, args);

				// Include trail / reverse-line (start→end), same as castAtLocation(caster, loc).
				playSpellEffects(caster, loc, power, args);
				sendMessages(caster, loc, args);

				return PostCastAction.NO_MESSAGES;
			}
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		playSpellEffects(caster, from, target, new SpellData(caster, target, from, power, args));
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		playSpellEffects(from, target, new SpellData(null, target, power, args));
		return true;
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
