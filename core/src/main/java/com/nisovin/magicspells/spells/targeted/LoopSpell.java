package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collection;

import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.VariableMod;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.util.managers.VariableManager;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

public class LoopSpell extends TargetedSpell implements TargetedEntitySpell, TargetedLocationSpell {

	private static boolean deathRegistered;

	private final Multimap<UUID, Loop> activeLoops = HashMultimap.create();

	private final ConfigData<Integer> iterations;

	private final ConfigData<Long> delay;
	private final ConfigData<Long> duration;
	private final ConfigData<Long> interval;

	private final double yOffset;

	private final ConfigData<Boolean> targeted;
	private final ConfigData<Boolean> pointBlank;
	private final ConfigData<Boolean> stopOnFail;
	private final ConfigData<Boolean> cancelOnDeath;
	private final ConfigData<Boolean> passTargeting;
	private final ConfigData<Boolean> stopOnSuccess;
	private final ConfigData<Boolean> requireEntityTarget;
	private final ConfigData<Boolean> castRandomSpellInstead;
	private final boolean skipFirstLoopModifiers;
	private final boolean skipFirstVariableModsLoop;
	private final boolean skipFirstLoopTargetModifiers;
	private final boolean skipFirstLoopLocationModifiers;
	private final boolean skipFirstVariableModsTargetLoop;

	private final String strFadeSelf;
	private final String strFadeTarget;

	private Subspell spellOnEnd;
	private String spellOnEndName;

	private List<Subspell> spells;
	private List<String> spellNames;

	private List<String> varModsLoop;
	private List<String> varModsTargetLoop;

	private Multimap<String, VariableMod> variableModsLoop;
	private Multimap<String, VariableMod> variableModsTargetLoop;

	private List<String> loopModifierStrings;
	private List<String> loopTargetModifierStrings;
	private List<String> loopLocationModifierStrings;

	private ModifierSet loopModifiers;
	private ModifierSet loopTargetModifiers;
	private ModifierSet loopLocationModifiers;

	public LoopSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		iterations = getConfigDataInt("iterations", 0);

		delay = getConfigDataLong("delay", 0);
		duration = getConfigDataLong("duration", 0);
		interval = getConfigDataLong("interval", 20);

		yOffset = getConfigDouble("y-offset", 0);

		targeted = getConfigDataBoolean("targeted", true);
		pointBlank = getConfigDataBoolean("point-blank", false);
		stopOnFail = getConfigDataBoolean("stop-on-fail", false);
		passTargeting = getConfigDataBoolean("pass-targeting", true);
		cancelOnDeath = getConfigDataBoolean("cancel-on-death", false);
		stopOnSuccess = getConfigDataBoolean("stop-on-success", false);
		requireEntityTarget = getConfigDataBoolean("require-entity-target", false);
		castRandomSpellInstead = getConfigDataBoolean("cast-random-spell-instead", false);

		boolean skipFirst = getConfigBoolean("skip-first", false);
		skipFirstLoopModifiers = getConfigBoolean("skip-first-loop-modifiers", skipFirst);
		skipFirstVariableModsLoop = getConfigBoolean("skip-first-variable-mods-loop", skipFirst);
		skipFirstLoopTargetModifiers = getConfigBoolean("skip-first-loop-target-modifiers", skipFirst);
		skipFirstLoopLocationModifiers = getConfigBoolean("skip-first-loop-location-modifiers", skipFirst);
		skipFirstVariableModsTargetLoop = getConfigBoolean("skip-first-variable-mods-target-loop", skipFirst);

		strFadeSelf = getConfigString("str-fade-self", "");
		strFadeTarget = getConfigString("str-fade-target", "");
		spellOnEndName = getConfigString("spell-on-end", null);

		spellNames = getConfigStringList("spells", null);
		varModsLoop = getConfigStringList("variable-mods-loop", null);
		varModsTargetLoop = getConfigStringList("variable-mods-target-loop", null);
		loopModifierStrings = getConfigStringList("loop-modifiers", null);
		loopTargetModifierStrings = getConfigStringList("loop-target-modifiers", null);
		loopLocationModifierStrings = getConfigStringList("loop-location-modifiers", null);
	}

	@Override
	public void initialize() {
		super.initialize();

		if ((!cancelOnDeath.isConstant() || Boolean.TRUE.equals(cancelOnDeath.get(null, 1f, null))) && !deathRegistered) {
			deathRegistered = true;
			registerEvents(new DeathListener());
		}

		if (spellOnEndName != null) {
			spellOnEnd = new Subspell(spellOnEndName);
			if (!spellOnEnd.process()) {
				MagicSpells.error("LoopSpell '" + internalName + "' has an invalid spell-on-end '" + spellOnEndName
						+ "' defined!");
				spellOnEnd = null;
			}
		}
		spellOnEndName = null;

		if (spellNames != null && !spellNames.isEmpty()) {
			spells = new ArrayList<>();

			for (String spellName : spellNames) {
				Subspell spell = new Subspell(spellName);
				if (!spell.process()) {
					MagicSpells.error(
							"LoopSpell '" + internalName + "' has an invalid spell '" + spellName + "' defined!");
					continue;
				}

				spells.add(spell);
			}

			if (spells.isEmpty())
				spells = null;
		}
		spellNames = null;
	}

	@Override
	protected void initializeVariables() {
		super.initializeVariables();

		if (varModsLoop != null && !varModsLoop.isEmpty()) {
			variableModsLoop = LinkedListMultimap.create();

			for (String s : varModsLoop) {
				try {
					String[] data = s.split(" ", 2);
					variableModsLoop.put(data[0], new VariableMod(data[1]));
				} catch (Exception e) {
					MagicSpells.error("Invalid variable-mods-loop option for spell '" + internalName + "': " + s);
				}
			}

			if (variableModsLoop.isEmpty())
				variableModsLoop = null;
		}

		if (varModsTargetLoop != null && !varModsTargetLoop.isEmpty()) {
			variableModsTargetLoop = LinkedListMultimap.create();

			for (String s : varModsTargetLoop) {
				try {
					String[] data = s.split(" ", 2);
					variableModsTargetLoop.put(data[0], new VariableMod(data[1]));
				} catch (Exception e) {
					MagicSpells
							.error("Invalid variable-mods-target-loop option for spell '" + internalName + "': " + s);
				}
			}

			if (variableModsTargetLoop.isEmpty())
				variableModsTargetLoop = null;
		}

		varModsLoop = null;
		varModsTargetLoop = null;
	}

	@Override
	protected void initializeModifiers() {
		super.initializeModifiers();

		if (loopModifierStrings != null && !loopModifierStrings.isEmpty())
			loopModifiers = new ModifierSet(loopModifierStrings, this);

		if (loopTargetModifierStrings != null && !loopTargetModifierStrings.isEmpty())
			loopTargetModifiers = new ModifierSet(loopTargetModifierStrings, this);

		if (loopLocationModifierStrings != null && !loopLocationModifierStrings.isEmpty())
			loopLocationModifiers = new ModifierSet(loopLocationModifierStrings, this);

		loopModifierStrings = null;
		loopTargetModifierStrings = null;
		loopLocationModifierStrings = null;
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			LivingEntity entityTarget = null;
			Location locationTarget = null;

			boolean targeted = Boolean.TRUE.equals(this.targeted.get(caster, power, args));
			boolean requireEntityTarget = Boolean.TRUE.equals(this.requireEntityTarget.get(caster, power, args));
			boolean pointBlank = Boolean.TRUE.equals(this.pointBlank.get(caster, power, args));

			if (targeted) {
				if (requireEntityTarget) {
					TargetInfo<LivingEntity> info = getTargetedEntity(caster, power);
					if (info.noTarget())
						return noTarget(caster, args, info);

					entityTarget = info.target();
					power = info.power();
				} else if (pointBlank) {
					locationTarget = caster.getLocation();
				} else {
					Block block = getTargetedBlock(caster, power, args);

					if (block != null) {
						locationTarget = block.getLocation();
						locationTarget.add(0.5, yOffset + 0.5, 0.5);

						SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, caster, locationTarget,
								power, args);
						if (!event.callEvent())
							return noTarget(caster, args);

						locationTarget = event.getTargetLocation();
						power = event.getPower();
					}
				}

				if (entityTarget == null && locationTarget == null)
					return noTarget(caster, args);
			}

			initLoop(caster, entityTarget, locationTarget, power, args);

			if (entityTarget != null) {
				sendMessages(caster, entityTarget, args);
				return PostCastAction.NO_MESSAGES;
			}
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		initLoop(caster, target, null, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		initLoop(caster, target, null, power, null);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		initLoop(null, target, null, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		if (!validTargetList.canTarget(target))
			return false;
		initLoop(null, target, null, power, null);
		return true;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		initLoop(caster, null, target.clone().add(0, yOffset, 0), power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		initLoop(caster, null, target.clone().add(0, yOffset, 0), power, null);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		initLoop(null, null, target.clone().add(0, yOffset, 0), power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(Location target, float power) {
		initLoop(null, null, target.clone().add(0, yOffset, 0), power, null);
		return true;
	}

	public Multimap<UUID, Loop> getActiveLoops() {
		return activeLoops;
	}

	public boolean isActive(LivingEntity entity) {
		return activeLoops.containsKey(entity.getUniqueId());
	}

	public void cancelLoops(LivingEntity entity) {
		Collection<Loop> loops = activeLoops.removeAll(entity.getUniqueId());
		loops.forEach(l -> l.cancel(false));
	}

	public void cancelLoops(UUID uuid) {
		Collection<Loop> loops = activeLoops.removeAll(uuid);
		loops.forEach(l -> l.cancel(false));
	}

	@Override
	protected void turnOff() {
		activeLoops.forEach((target, loop) -> loop.cancel(false));
		activeLoops.clear();
	}

	private void initLoop(LivingEntity caster, LivingEntity targetEntity, Location targetLocation, float power,
			String[] args) {
		Loop loop = new Loop(caster, targetEntity, targetLocation, power, args);

		if (targetEntity != null)
			activeLoops.put(targetEntity.getUniqueId(), loop);
		else if (caster != null)
			activeLoops.put(caster.getUniqueId(), loop);
		else
			activeLoops.put(null, loop);
	}

	public class Loop implements Runnable {

		private final LivingEntity caster;

		private final LivingEntity targetEntity;
		private final Location targetLocation;

		private final long iterations;
		private final int taskId;

		private SpellData data;
		private long count;
		private boolean cancelled;

		private Loop(LivingEntity caster, LivingEntity targetEntity, Location targetLocation, float power,
				String[] args) {
			this.caster = caster;

			this.targetLocation = targetLocation;
			this.targetEntity = targetEntity;

			data = new SpellData(caster, targetEntity, targetLocation, power, args);
			iterations = LoopSpell.this.iterations.get(caster, targetEntity, power, args);

			long interval = LoopSpell.this.interval.get(data);
			long delay = LoopSpell.this.delay.get(data);

			if (interval <= 0) {
				taskId = -1;

				if (delay < 0) {
					for (int i = 0; i < iterations && !cancelled; i++)
						run();
					return;
				}

				MagicSpells.scheduleDelayedTask(() -> {
					for (int i = 0; i < iterations && !cancelled; i++)
						run();
				}, delay);

				return;
			}

			taskId = MagicSpells.scheduleRepeatingTask(this, delay, interval);

			long duration = LoopSpell.this.duration.get(data);
			if (duration > 0)
				MagicSpells.scheduleDelayedTask(this::cancel, duration);
		}

		@Override
		public void run() {
			boolean cancelOnDeath = Boolean.TRUE.equals(LoopSpell.this.cancelOnDeath.get(data));

			if (targetEntity != null && (cancelOnDeath || !(targetEntity instanceof Player))
					&& !targetEntity.isValid()) {
				cancel();
				return;
			}

			if (variableModsLoop != null && (!skipFirstVariableModsLoop || count > 0)
					&& caster instanceof Player playerCaster) {
				VariableManager variableManager = MagicSpells.getVariableManager();
				Player playerTarget = targetEntity instanceof Player t ? t : null;

				for (Map.Entry<String, VariableMod> entry : variableModsLoop.entries()) {
					VariableMod mod = entry.getValue();
					if (mod == null)
						continue;

					variableManager.processVariableMods(entry.getKey(), mod, playerCaster, playerCaster, playerTarget,
							data.power(), data.args());
				}
			}

			if (variableModsTargetLoop != null && (!skipFirstVariableModsTargetLoop || count > 0)
					&& targetEntity instanceof Player playerTarget) {
				VariableManager variableManager = MagicSpells.getVariableManager();
				Player playerCaster = caster instanceof Player p ? p : null;

				for (Map.Entry<String, VariableMod> entry : variableModsTargetLoop.entries()) {
					VariableMod mod = entry.getValue();
					if (mod == null)
						continue;

					variableManager.processVariableMods(entry.getKey(), mod, playerTarget, playerCaster, playerTarget,
							data.power(), data.args());
				}
			}

			if (loopModifiers != null && (!skipFirstLoopModifiers || count > 0)) {
				ModifierResult result = loopModifiers.apply(caster, data);
				data = result.data();

				if (!result.check()) {
					cancel();
					return;
				}
			}

			if (targetEntity != null && loopTargetModifiers != null && (!skipFirstLoopTargetModifiers || count > 0)) {
				ModifierResult result = loopTargetModifiers.apply(caster, targetEntity, data);
				data = result.data();

				if (!result.check()) {
					cancel();
					return;
				}
			}

			if (targetLocation != null && loopLocationModifiers != null
					&& (!skipFirstLoopLocationModifiers || count > 0)) {
				ModifierResult result = loopLocationModifiers.apply(caster, targetLocation, data);
				data = result.data();

				if (!result.check()) {
					cancel();
					return;
				}
			}

			if (spells != null) {
				if (Boolean.TRUE.equals(castRandomSpellInstead.get(data))) {
					Subspell spell = spells.get(random.nextInt(spells.size()));
					if (!cast(spell))
						return;
				} else {
					for (Subspell spell : spells)
						if (!cast(spell))
							return;
				}
			}

			if (caster != null) {
				if (targetEntity != null)
					playSpellEffects(caster, targetEntity, data);
				else if (targetLocation != null)
					playSpellEffects(caster, targetLocation, data);
				else
					playSpellEffects(EffectPosition.CASTER, caster, data);
			} else {
				if (targetEntity != null)
					playSpellEffects(EffectPosition.TARGET, targetEntity, data);
				else if (targetLocation != null)
					playSpellEffects(EffectPosition.TARGET, targetLocation, data);
			}

			count++;
			if (iterations > 0 && count >= iterations)
				cancel();
		}

		private boolean cast(Subspell spell) {
			boolean success;
			boolean passTargeting = Boolean.TRUE.equals(LoopSpell.this.passTargeting.get(data));

			if (targetEntity != null)
				success = spell.subcast(caster, targetEntity, data.power(), data.args(), passTargeting);
			else if (targetLocation != null)
				success = spell.subcast(caster, targetLocation, data.power(), data.args());
			else
				success = spell.subcast(caster, data.power(), data.args());

			boolean stopOnSuccess = Boolean.TRUE.equals(LoopSpell.this.stopOnSuccess.get(data));
			boolean stopOnFail = Boolean.TRUE.equals(LoopSpell.this.stopOnFail.get(data));
			if (stopOnSuccess && success || stopOnFail && !success) {
				cancel();
				return false;
			}

			return true;
		}

		public LoopSpell getSpell() {
			return LoopSpell.this;
		}

		public LivingEntity getCaster() {
			return caster;
		}

		private void cancel() {
			cancel(true);
		}

		private void cancel(boolean remove) {
			if (cancelled)
				return;

			cancelled = true;

			MagicSpells.cancelTask(taskId);

			if (remove) {
				UUID key = null;
				if (targetEntity != null)
					key = targetEntity.getUniqueId();
				else if (caster != null)
					key = caster.getUniqueId();

				activeLoops.remove(key, this);
			}

			if (targetEntity != null)
				playSpellEffects(EffectPosition.DELAYED, targetEntity, data);
			else if (targetLocation != null)
				playSpellEffects(EffectPosition.DELAYED, targetLocation, data);
			else if (caster != null)
				playSpellEffects(EffectPosition.DELAYED, caster, data);

			if (caster != null || targetEntity != null) {
				String casterName = caster != null ? getTargetName(caster) : "";
				String targetName = targetEntity != null ? getTargetName(targetEntity) : "";

				sendMessage(strFadeSelf, caster, caster, targetEntity, data.args(), "%a", casterName, "%t", targetName);
				sendMessage(strFadeTarget, targetEntity, caster, targetEntity, data.args(), "%a", casterName, "%t",
						targetName);
			}

			if (spellOnEnd != null) {
				boolean passTargeting = Boolean.TRUE.equals(LoopSpell.this.passTargeting.get(data));
				if (targetEntity != null)
					spellOnEnd.subcast(caster, targetEntity, data.power(), data.args(), passTargeting);
				else if (targetLocation != null)
					spellOnEnd.subcast(caster, targetLocation, data.power(), data.args());
				else if (caster != null)
					spellOnEnd.subcast(caster, data.power(), data.args());
			}
		}

	}

	private static class DeathListener implements Listener {

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void onDeath(PlayerDeathEvent event) {
			Player player = event.getEntity();
			UUID uuid = player.getUniqueId();

			List<Spell> spells = MagicSpells.getSpellsOrdered();
			for (Spell spell : spells) {
				if (!(spell instanceof LoopSpell loopSpell)
						|| !Boolean.TRUE.equals(loopSpell.cancelOnDeath.get(player, player, 1f, null)))
					continue;
				loopSpell.cancelLoops(uuid);
			}
		}

	}

}
