package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.ArrayList;

import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.SpellFilter;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class LevitateSpell extends TargetedSpell implements TargetedEntitySpell {

	static final double DEFAULT_MAX_DISTANCE = 20;

	private Map<UUID, Levitator> levitating;

	private ConfigData<Integer> tickRate;
	private ConfigData<Integer> duration;

	private ConfigData<Double> yOffset;
	private ConfigData<Double> minDistance;
	private ConfigData<Double> maxDistance;
	private ConfigData<Double> distanceChange;

	private boolean cancelOnSpellCast;
	private boolean cancelOnItemSwitch;
	private boolean cancelOnTakeDamage;
	private boolean toggle;

	private SpellFilter filter;

	public LevitateSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		tickRate = getConfigDataInt("tick-rate", 5);
		duration = getConfigDataInt("duration", 10);

		yOffset = getConfigDataDouble("y-offset", 0F);
		minDistance = getConfigDataDouble("min-distance", 1F);
		maxDistance = getConfigDataDouble("max-distance", DEFAULT_MAX_DISTANCE);
		distanceChange = getConfigDataDouble("distance-change", 0F);

		cancelOnSpellCast = getConfigBoolean("cancel-on-spell-cast", false);
		cancelOnItemSwitch = getConfigBoolean("cancel-on-item-switch", true);
		cancelOnTakeDamage = getConfigBoolean("cancel-on-take-damage", true);
		toggle = getConfigBoolean("toggle", true);

		filter = getConfigSpellFilter();

		levitating = new HashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		if (cancelOnItemSwitch) registerEvents(new ItemSwitchListener());
		if (cancelOnSpellCast) registerEvents(new SpellCastListener());
		if (cancelOnTakeDamage) registerEvents(new DamageListener());
	}

	@Override
	public void turnOff() {
		new ArrayList<>(levitating.values()).forEach(Levitator::stop);
		levitating.clear();
	}

	@Override
	public CastResult cast(SpellData data) {
		if (stopOnRecast(data.caster())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!(data.caster() instanceof LivingEntity caster))
			return new CastResult(noTarget(data.caster(), data.args()), data);
		TargetInfo<LivingEntity> target = getTargetedEntity(caster, data.power(), data.args());
		if (target.noTarget()) return new CastResult(noTarget(caster, data.args(), target), data);

		levitate(caster, target.target(), target.power(), data.args());
		sendMessages(caster, target.target(), data.args());

		return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		return cast(state, new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (stopOnRecast(data.caster())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!(data.caster() instanceof LivingEntity caster)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		levitate(caster, data.target(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	public boolean isBeingLevitated(LivingEntity entity) {
		for (Levitator levitator : levitating.values()) {
			if (levitator.target.equals(entity)) return true;
		}
		return false;
	}

	public void removeLevitate(LivingEntity entity) {
		for (Levitator levitator : new ArrayList<>(levitating.values())) {
			if (levitator.target.equals(entity)) levitator.stop();
		}
	}

	@Override
	public CastResult cast(SpellCastState state, SpellData data) {
		if (stopOnRecast(data.caster())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return super.cast(state, data);
	}

	private boolean stopOnRecast(Entity caster) {
		if (!toggle || caster == null) return false;
		Levitator active = levitating.get(caster.getUniqueId());
		if (active == null) return false;
		active.stop();
		return true;
	}

	private boolean isLevitating(Entity entity) {
		return levitating.containsKey(entity.getUniqueId());
	}

	private void levitate(LivingEntity caster, Entity target, float power, String[] args) {
		int duration = this.duration.get(caster, target, power, args);
		int tickRate = this.tickRate.get(caster, target, power, args);
		if (duration < tickRate) duration = tickRate;

		if (!caster.getWorld().equals(target.getWorld())) return;
		double distance = caster.getLocation().distance(target.getLocation());
		Levitator active = levitating.get(caster.getUniqueId());
		if (active != null) active.stop();
		Levitator lev = new Levitator(caster, target, duration / tickRate, tickRate, distance, power, args);
		levitating.put(caster.getUniqueId(), lev);

		SpellData data = new SpellData(caster, target, power, args);

		playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, caster.getLocation(), target.getLocation(), caster, target, data);
		playSpellEffects(caster, target, data);
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player pl = event.getEntity();
		UUID _caster = null;
		for(Levitator lev : levitating.values()){
			if(lev.target instanceof Player && pl.getUniqueId().equals(lev.target.getUniqueId())){
				_caster = lev.caster.getUniqueId();
				break;
			}
		}
		if(_caster != null) levitating.remove(_caster).stop();
		if (!isLevitating(pl)) return;
		levitating.remove(pl.getUniqueId()).stop();
	}

	public class ItemSwitchListener implements Listener {

		@EventHandler
		public void onItemSwitch(PlayerItemHeldEvent event) {
			Player pl = event.getPlayer();
			if (!isLevitating(pl)) return;
			levitating.remove(pl.getUniqueId()).stop();
		}

	}

	public class SpellCastListener implements Listener {

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void onSpellCast(SpellCastEvent event) {
			Entity caster = event.getCaster();
			if (!isLevitating(caster)) return;
			if (!filter.check(event.getSpell())) return;
			levitating.remove(caster.getUniqueId()).stop();
		}

	}

	public class DamageListener implements Listener {

		@EventHandler
		public void onEntityDamage(EntityDamageByEntityEvent event) {
			Entity entity = event.getEntity();
			if (!(entity instanceof Player)) return;
			if (!isLevitating((LivingEntity) entity)) return;
			levitating.remove(entity.getUniqueId()).stop();
		}

	}

	private class Levitator implements Runnable {

		private Entity caster;
		private Entity target;
		private double distance;
		private int duration;
		private int counter;
		private int taskId;
		private boolean stopped;

		private final double distanceChange;
		private final double maxDistanceSq;
		private final double minDistance;
		private final double yOffset;
		private final int tickRate;

		private Levitator(Entity caster, Entity target, int duration, int tickRate, double distance, float power, String[] args) {
			this.caster = caster;
			this.target = target;
			this.duration = duration;
			this.distance = distance;
			this.tickRate = tickRate;

			counter = 0;
			stopped = false;

			double maxDistance = LevitateSpell.this.maxDistance.get(caster, target, power, args);
			maxDistanceSq = maxDistance * maxDistance;

			distanceChange = LevitateSpell.this.distanceChange.get(caster, target, power, args);
			minDistance = LevitateSpell.this.minDistance.get(caster, target, power, args);
			yOffset = LevitateSpell.this.yOffset.get(caster, target, power, args);

			taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickRate);
		}

		@Override
		public void run() {
			if (stopped) return;

			if (duration > 0 && counter >= duration) {
				stop();
				return;
			}
			counter++;

			if (caster.isDead() || !caster.isValid() || target.isDead() || !target.isValid()
					|| !caster.getWorld().equals(target.getWorld())) {
				stop();
				return;
			}
			if (caster.getLocation().distanceSquared(target.getLocation()) > maxDistanceSq) {
				stop();
				return;
			}

			if (distanceChange != 0 && distance > minDistance) {
				distance -= distanceChange;
				if (distance < minDistance) distance = minDistance;
			}

			if (!(caster instanceof LivingEntity livingCaster)) {
				stop();
				return;
			}
			target.setFallDistance(0);
			Vector wantedLocation = livingCaster.getEyeLocation().toVector().add(livingCaster.getLocation().getDirection().multiply(distance)).add(new Vector(0, yOffset, 0));
			Vector v = wantedLocation.subtract(target.getLocation().toVector()).multiply(tickRate / 25F + 0.1);
			target.setVelocity(v);
		}

		private void stop() {
			if (stopped) return;
			stopped = true;
			levitating.remove(caster.getUniqueId(), this);
			MagicSpells.cancelTask(taskId);
		}

	}

}
