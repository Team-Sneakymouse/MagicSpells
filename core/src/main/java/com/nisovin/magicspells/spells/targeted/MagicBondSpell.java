package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerQuitEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.SpellFilter;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

public class MagicBondSpell extends TargetedSpell implements TargetedEntitySpell {

	private Map<LivingEntity, LivingEntity> bondTarget;

	private ConfigData<Integer> duration;

	private String strDurationEnd;

	private SpellFilter filter;

	public MagicBondSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		duration = getConfigDataInt("duration", 200);
		strDurationEnd = getConfigString("str-duration", "");
		filter = getConfigSpellFilter();

		bondTarget = new HashMap<>();
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), data.args());
					if (target.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), target), data);
		
					bond(data.caster(), target.target(), target.power(), data.args());
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
				bond(data.caster(), data.target(), data.power(), data.args());
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	private void bond(LivingEntity caster, LivingEntity target, float power, String[] args) {
		bondTarget.put(caster, target);
		playSpellEffects(caster, target, power, args);
		SpellMonitor monitorBond = new SpellMonitor(caster, target, power);
		MagicSpells.registerEvents(monitorBond);

		MagicSpells.scheduleDelayedTask(() -> {
			if (!strDurationEnd.isEmpty()) {
				if (caster instanceof Player) MagicSpells.sendMessage((Player) caster, strDurationEnd);
				if (target instanceof Player) MagicSpells.sendMessage((Player) target, strDurationEnd);
			}
			bondTarget.remove(caster);

			HandlerList.unregisterAll(monitorBond);
		}, duration.get(caster, target, power, args));
	}

	private class SpellMonitor implements Listener {

		private LivingEntity caster;
		private LivingEntity target;
		private float power;

		private SpellMonitor(LivingEntity caster, LivingEntity target, float power) {
			this.caster = caster;
			this.target = target;
			this.power = power;
		}

		@EventHandler
		public void onPlayerLeave(PlayerQuitEvent e) {
			if (bondTarget.containsKey(e.getPlayer()) || bondTarget.containsValue(e.getPlayer())) {
				bondTarget.remove(caster);
			}
		}

		@EventHandler
		public void onPlayerSpellCast(SpellCastEvent e) {
			Spell spell = e.getSpell();
			if (e.getCaster() != caster || spell instanceof MagicBondSpell) return;
			if (spell.onCooldown(caster)) return;
			if (!bondTarget.containsKey(caster) && !bondTarget.containsValue(target)) return;
			if (target.isDead()) return;
			if (!filter.check(spell)) return;

			spell.cast(target);

		}

		@Override
		public boolean equals(Object other) {
			if (other == null) return false;
			if (!getClass().getName().equals(other.getClass().getName())) return false;
			SpellMonitor otherMonitor = (SpellMonitor)other;
			if (otherMonitor.caster != caster) return false;
			if (otherMonitor.target != target) return false;
			if (otherMonitor.power != power) return false;
			return true;
		}

	}

}
