package com.nisovin.magicspells.spells.targeted;

import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

public class MountSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Integer> duration;

	private boolean reverse;

	public MountSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		duration = getConfigDataInt("duration", 0);

		reverse = getConfigBoolean("reverse", false);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), data.args());
					if (targetInfo.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);
					LivingEntity target = targetInfo.target();
		
					mount(data.caster(), target, data.power(), data.args());
					sendMessages(data.caster(), target, data.args());
		
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
		if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				mount(data.caster(), data.target(), data.power(), data.args());
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	private void mount(Entity caster, Entity target, float power, String[] args) {
		if (caster == null || target == null) return;

		int duration = this.duration.get(caster, target, power, args);

		if (reverse) {
			if (!caster.getPassengers().isEmpty()) caster.eject();
			if (caster.getVehicle() != null) caster.getVehicle().eject();
			if (target.getVehicle() != null) target.getVehicle().eject();

			caster.addPassenger(target);
			if (duration > 0) {
				Entity finalTarget = target;
				MagicSpells.scheduleDelayedTask(() -> caster.removePassenger(finalTarget), duration);
			}
			sendMessages(caster, target, args);
			return;
		}

		if (caster.getVehicle() != null) {
			Entity veh = caster.getVehicle();
			veh.eject();
			List<Entity> passengers = caster.getPassengers();
			if (passengers.isEmpty()) return;

			caster.eject();
			for (Entity e : passengers) {
				veh.addPassenger(e);
				if (duration > 0) {
					MagicSpells.scheduleDelayedTask(() -> veh.removePassenger(e), duration);
				}
			}
			return;
		}

		for (Entity e : target.getPassengers()) {
			if (!(e instanceof LivingEntity)) continue;
			target = (LivingEntity) e;
			break;
		}

		caster.eject();
		target.addPassenger(caster);
		if (duration > 0) {
			Entity finalTarget1 = target;
			MagicSpells.scheduleDelayedTask(() -> finalTarget1.removePassenger(caster), duration);
		}

		playSpellEffects(caster, target, power, args);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		Entity vehicle = player.getVehicle();
		List<Entity> passengers = player.getPassengers();
		if (!passengers.isEmpty()) player.eject();
		if (vehicle instanceof Player) vehicle.eject();
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		Entity vehicle = player.getVehicle();
		List<Entity> passengers = player.getPassengers();
		if (!passengers.isEmpty()) player.eject();
		if (vehicle instanceof Player) vehicle.eject();
	}

}
