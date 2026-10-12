package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class StunSpell extends TargetedSpell implements TargetedEntitySpell {

	private final Map<UUID, StunnedInfo> stunnedLivingEntities;

	private final int interval;
	private final ConfigData<Integer> duration;

	private final boolean stunBody;
	private final boolean stunMonitor;
	private final boolean useTargetLocation;
	private final ConfigData<Boolean> powerAffectsDuration;

	private final StunListener listener;

	public StunSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		interval = getConfigInt("interval", 5);
		duration = getConfigDataInt("duration", 200);

		stunBody = getConfigBoolean("stun-body", true);
		stunMonitor = getConfigBoolean("stun-monitor", true);
		useTargetLocation = getConfigBoolean("use-target-location", true);
		powerAffectsDuration = getConfigDataBoolean("power-affects-duration", true);

		listener = new StunListener();
		stunnedLivingEntities = new HashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		if (!stunBody && !stunMonitor) {
			MagicSpells.error("StunSpell '" + internalName + "' is not attempting to stun the body or the monitor.");
			return;
		}

		registerEvents(listener);
		MagicSpells.scheduleRepeatingTask(new StunMonitor(), interval, interval);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), data.args());
		if (targetInfo.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);

		data = data.target(targetInfo.target()).power(targetInfo.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return result;

		sendMessages(data.caster(), data.target(), data.args());
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
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			stunLivingEntity(data);
		} else {
			if (!validTargetList.canTarget(data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			stunLivingEntity(data);
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	private void stunLivingEntity(SpellData data) {
		Entity caster = data.caster();
		Entity target = data.target();
		float power = data.power();
		String[] args = data.args();
		long duration = this.duration.get(caster, target, power, args) * TimeUtil.MILLISECONDS_PER_SECOND / 20;
		if (Boolean.TRUE.equals(powerAffectsDuration.get(caster, target, power, args))) duration = Math.round(duration * power);

		StunnedInfo info = new StunnedInfo(caster, target, System.currentTimeMillis() + duration, target.getLocation());
		stunnedLivingEntities.put(target.getUniqueId(), info);

		if (caster != null)
			playSpellEffects(caster, target, data);
		else
			playSpellEffects(EffectPosition.TARGET, target, data);

		playSpellEffectsBuff(target, entity -> {
			if (!(entity instanceof LivingEntity)) return false;
			return isStunned((LivingEntity) entity);
		}, data);
	}

	public boolean isStunned(LivingEntity entity) {
		return stunnedLivingEntities.containsKey(entity.getUniqueId());
	}

	public void removeStun(Entity entity) {
		stunnedLivingEntities.remove(entity.getUniqueId());
	}

	private record StunnedInfo(Entity caster, Entity target, Long until, Location targetLocation) {
	}

	private class StunListener implements Listener {

		@EventHandler
		private void onMove(PlayerMoveEvent event) {
			Player player = event.getPlayer();
			if (!isStunned(player)) return;
			StunnedInfo info = stunnedLivingEntities.get(player.getUniqueId());
			if (info == null) return;

			// Perform checks whether to stun the player.
			boolean shouldStun = false;
			Location from = event.getFrom();
			Location to = event.getTo();
			boolean movedBody = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
			boolean movedMonitor = from.getPitch() != to.getPitch() || from.getYaw() != to.getYaw();
			if (stunBody && movedBody) shouldStun = true;
			if (stunMonitor && movedMonitor) shouldStun = true;
			if (!shouldStun) return;

			if (info.until > System.currentTimeMillis()) {
				event.setTo(useTargetLocation ? info.targetLocation : from);
				return;
			}

			removeStun(player);
		}

		@EventHandler
		private void onInteract(PlayerInteractEvent event) {
			if (!isStunned(event.getPlayer())) return;
			event.setCancelled(true);
		}

		@EventHandler
		private void onQuit(PlayerQuitEvent event) {
			Player player = event.getPlayer();
			if (!isStunned(player)) return;
			removeStun(player);
		}

		@EventHandler
		private void onDeath(PlayerDeathEvent event) {
			Player player = event.getEntity();
			if (!isStunned(player)) return;
			removeStun(player);
		}

	}

	private class StunMonitor implements Runnable {

		private final Set<Entity> toRemove = new HashSet<>();

		@Override
		public void run() {
			for (UUID id : stunnedLivingEntities.keySet()) {
				StunnedInfo info = stunnedLivingEntities.get(id);
				Entity entity = info.target;
				Long until = info.until;
				if (entity instanceof Player) continue;

				if (entity.isValid() && until > System.currentTimeMillis()) {
					entity.teleportAsync(info.targetLocation);
					continue;
				}

				toRemove.add(entity);
			}

			for (Entity entity : toRemove) {
				removeStun(entity);
			}

			toRemove.clear();
		}

	}

}
