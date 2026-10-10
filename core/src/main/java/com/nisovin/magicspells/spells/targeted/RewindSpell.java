package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerQuitEvent;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.mana.ManaHandler;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.mana.ManaChangeReason;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class RewindSpell extends TargetedSpell implements TargetedEntitySpell {

	private Map<LivingEntity, Rewinder> entities;

	private ConfigData<Integer> tickInterval;
	private ConfigData<Integer> startDuration;
	private ConfigData<Integer> rewindInterval;
	private ConfigData<Integer> specialEffectInterval;
	private ConfigData<Integer> delayedEffectInterval;

	private boolean rewindMana;
	private boolean rewindHealth;
	private boolean allowForceRewind;

	private Subspell rewindSpell;
	private String rewindSpellName;

	public RewindSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		tickInterval = getConfigDataInt("tick-interval", 4);
		startDuration = getConfigDataInt("start-duration", 200);
		rewindInterval = getConfigDataInt("rewind-interval", 2);
		specialEffectInterval = getConfigDataInt("special-effect-interval", 5);
		delayedEffectInterval = getConfigDataInt("delayed-effect-interval", 5);

		rewindMana = getConfigBoolean("rewind-mana", false);
		rewindHealth = getConfigBoolean("rewind-health", true);
		allowForceRewind = getConfigBoolean("allow-force-rewind", true);

		rewindSpellName = getConfigString("spell-on-rewind", "");

		entities = new ConcurrentHashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		rewindSpell = new Subspell(rewindSpellName);
		if (!rewindSpell.process()) {
			if (!rewindSpellName.isEmpty())
				MagicSpells.error("RewindSpell '" + internalName + "' has an invalid spell-on-rewind defined!");
			rewindSpell = null;
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
		if (info.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), info), data);

		data = data.target(info.target()).power(info.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return result;

		sendMessages(data.caster(), data.target(), data.args());
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
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			new Rewinder(data.caster(), data.target(), data.power(), data.args());
			playSpellEffects(data.caster(), data.target(), data);
		} else {
			if (!validTargetList.canTarget(data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			new Rewinder(null, data.target(), data.power(), data.args());
			playSpellEffects(EffectPosition.TARGET, data.target(), data);
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@EventHandler(ignoreCancelled = true)
	public void onSpellCast(SpellCastEvent e) {
		if (!allowForceRewind)
			return;
		LivingEntity caster = e.getCaster();
		if (!entities.containsKey(caster))
			return;
		if (!e.getSpell().getInternalName().equals(internalName))
			return;
		entities.get(caster).rewind();
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		Player pl = e.getPlayer();
		if (!entities.containsKey(pl))
			return;
		entities.get(pl).stop();
	}

	private class Rewinder implements Runnable {

		private int taskId;
		private int counter = 0;

		private int startMana;
		private double startHealth;

		private LivingEntity caster;
		private float power;
		private String[] args;
		private SpellData data;
		private LivingEntity entity;
		private List<Location> locations;

		private final int startDuration;
		private final int specialEffectInterval;

		private Rewinder(LivingEntity caster, LivingEntity entity, float power, String[] args) {
			this.locations = new ArrayList<>();
			this.entity = entity;
			this.caster = caster;
			this.power = power;
			this.args = args;

			data = new SpellData(caster, entity, power, args);

			entities.put(entity, this);

			this.startHealth = entity.getHealth();
			if (MagicSpells.isManaSystemEnabled() && entity instanceof Player player) {
				ManaHandler handler = MagicSpells.getManaHandler();
				if (handler != null)
					this.startMana = handler.getMana(player);
			}

			int tickInterval = RewindSpell.this.tickInterval.get(caster, entity, power, args);
			startDuration = RewindSpell.this.startDuration.get(caster, entity, power, args) / tickInterval;
			specialEffectInterval = RewindSpell.this.specialEffectInterval.get(caster, entity, power, args);

			this.taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickInterval);
		}

		@Override
		public void run() {
			// Save locations
			locations.add(entity.getLocation());
			// Loop through already saved locations and play effects with special position
			if (specialEffectInterval > 0 && counter % specialEffectInterval == 0)
				locations.forEach(loc -> playSpellEffects(EffectPosition.SPECIAL, loc, data));
			counter++;
			if (counter >= startDuration)
				rewind();
		}

		private void rewind() {
			MagicSpells.cancelTask(taskId);
			entities.remove(entity);
			if (rewindSpell != null)
				rewindSpell.subcast(new SpellData(caster, power, args));
			new ForceRewinder(caster, entity, locations, startHealth, startMana, power, args);
		}

		private void stop() {
			MagicSpells.cancelTask(taskId);
			entities.remove(entity);
		}

	}

	private class ForceRewinder implements Runnable {

		private int taskId;
		private int counter;

		private int startMana;
		private SpellData data;
		private double startHealth;
		private LivingEntity entity;

		private Location tempLocation;
		private List<Location> locations;

		private final int delayedEffectInterval;

		private ForceRewinder(LivingEntity caster, LivingEntity entity, List<Location> locations, double startHealth,
				int startMana, float power, String[] args) {
			this.locations = locations;
			this.entity = entity;
			this.startMana = startMana;
			this.startHealth = startHealth;
			this.counter = locations.size();

			data = new SpellData(caster, entity, power, args);

			delayedEffectInterval = RewindSpell.this.delayedEffectInterval.get(data);

			int rewindInterval = RewindSpell.this.rewindInterval.get(data);
			this.taskId = MagicSpells.scheduleRepeatingTask(this, 0, rewindInterval);
		}

		@Override
		public void run() {
			// Check if the entity is valid and alive
			if (entity == null || !entity.isValid() || entity.isDead()) {
				cancel();
				return;
			}

			if (locations != null && locations.size() > 0)
				tempLocation = locations.get(counter - 1);
			if (tempLocation != null) {
				entity.teleportAsync(tempLocation);
				locations.remove(tempLocation);
				if (delayedEffectInterval > 0 && counter % delayedEffectInterval == 0)
					locations.forEach(loc -> playSpellEffects(EffectPosition.DELAYED, loc, data));
			}

			counter--;
			if (counter <= 0)
				stop();
		}

		private void stop() {
			MagicSpells.cancelTask(taskId);
			if (rewindHealth)
				entity.setHealth(startHealth);
			if (rewindMana && MagicSpells.isManaSystemEnabled() && entity instanceof Player player) {
				ManaHandler handler = MagicSpells.getManaHandler();
				if (handler != null)
					handler.setMana(player, startMana, ManaChangeReason.OTHER);
			}
		}

		private void cancel() {
			MagicSpells.cancelTask(taskId);
			locations.clear();
			locations = null;
		}

	}

}
