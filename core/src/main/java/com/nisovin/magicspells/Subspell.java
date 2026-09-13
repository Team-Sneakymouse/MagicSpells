package com.nisovin.magicspells;

import com.nisovin.magicspells.util.performance.PerformanceDiagnostics;
import com.nisovin.magicspells.util.performance.PerformanceExecution.Operation;

import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.Objects;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.concurrent.ThreadLocalRandom;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.ValidTargetList;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.Spell.SpellCastState;
import com.nisovin.magicspells.Spell.PostCastAction;
import com.nisovin.magicspells.Spell.SpellCastResult;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.util.config.FunctionData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class Subspell {

	private static final Random random = ThreadLocalRandom.current();

	private Spell spell;
	private final String spellName;
	private CastMode mode = CastMode.PARTIAL;
	private CastTargeting targeting = CastTargeting.NORMAL;

	private boolean invert = false;
	private boolean passPower = true;
	private boolean passTargeting = false;
	private ConfigData<Integer> delay = (caster, target, location, power, args) -> -1;
	private ConfigData<Double> chance = (caster, target, location, power, args) -> -1D;
	private ConfigData<Float> subPower = (caster, target, location, power, args) -> 1F;
	private ConfigData<String[]> args = (caster, target, location, power, args) -> null;

	private boolean isTargetedEntity = false;
	private boolean isTargetedLocation = false;
	private boolean isTargetedEntityFromLocation = false;

	// spellName(mode=hard|h|full|f|partial|p|direct|d;power=[subpower];delay=[delay];chance=[chance])
	public Subspell(String data) {
		String[] split = data.split("\\(", 2);

		spellName = split[0].trim();

		if (split.length > 1) {
			split[1] = split[1].trim();
			if (split[1].endsWith(")"))
				split[1] = split[1].substring(0, split[1].length() - 1);

			String[] castArguments = split[1].split(";");
			for (String castArgument : castArguments) {
				String[] keyValue = castArgument.split("=", 2);
				if (keyValue.length != 2) {
					MagicSpells.error("Invalid cast argument '" + castArgument + "' on subspell '" + data + "'.");
					continue;
				}

				String key = keyValue[0].toLowerCase().trim(), value = keyValue[1].trim();
				switch (key) {
					case "mode" -> mode = CastMode.getFromString(value);
					case "targeting" -> {
						try {
							targeting = CastTargeting.valueOf(value.toUpperCase());
						} catch (IllegalArgumentException e) {
							MagicSpells.error("Invalid target type '" + value + "' on subspell '" + data + "'.");
							DebugHandler.debugIllegalArgumentException(e);
						}
					}
					case "invert" -> invert = Boolean.parseBoolean(value);
					case "pass-power" -> passPower = Boolean.parseBoolean(value);
					case "pass-targeting" -> passTargeting = Boolean.parseBoolean(value);
					case "args" -> {
						try {
							JsonElement element = JsonParser.parseString(value);
							JsonArray array = element.getAsJsonArray();

							List<ConfigData<String>> argumentData = new ArrayList<>();
							List<String> arguments = new ArrayList<>();

							boolean constant = true;
							for (JsonElement je : array) {
								String val = je.getAsString();
								ConfigData<String> supplier = ConfigDataUtil.getString(val);

								argumentData.add(supplier);
								arguments.add(val);

								constant &= supplier.isConstant();
							}

							if (constant) {
								String[] arg = arguments.toArray(new String[0]);
								args = (caster, target, location, power, args) -> arg;

								continue;
							}

							args = (caster, target, location, power, args) -> {
								String[] ret = new String[argumentData.size()];
								for (int i = 0; i < argumentData.size(); i++)
									ret[i] = argumentData.get(i).get(caster, target, power, args);

								return ret;
							};
						} catch (IllegalArgumentException e) {
							MagicSpells.error("Invalid spell arguments '" + value + "' on subspell '" + data + "'.");
							DebugHandler.debugIllegalArgumentException(e);
						} catch (ClassCastException | JsonSyntaxException e) {
							MagicSpells.error("Invalid spell arguments '" + value + "' on subspell '" + data + "'.");
							DebugHandler.debug(e);
						}
					}
					case "power" -> {
						try {
							float subPower = Float.parseFloat(value);
							this.subPower = (caster, target, location, power, args) -> subPower;
						} catch (NumberFormatException e) {
							FunctionData<Float> subPowerData = FunctionData.build(value, Double::floatValue, true);
							if (subPowerData == null) {
								MagicSpells.error("Invalid power '" + value + "' on subspell '" + data + "'.");
								continue;
							}

							subPower = subPowerData;
						}
					}
					case "delay" -> {
						try {
							int delay = Integer.parseInt(value);
							this.delay = (caster, target, location, power, args) -> delay;
						} catch (NumberFormatException e) {
							FunctionData<Integer> delayData = FunctionData.build(value, Double::intValue, true);
							if (delayData == null) {
								MagicSpells.error("Invalid delay '" + value + "' on subspell '" + data + "'.");
								continue;
							}

							delay = delayData;
						}
					}
					case "chance" -> {
						try {
							double chance = Double.parseDouble(value) / 100;
							this.chance = (caster, target, location, power, args) -> chance;
						} catch (NumberFormatException e) {
							FunctionData<Double> chanceData = FunctionData.build(value, Function.identity(), true);
							if (chanceData == null) {
								MagicSpells.error("Invalid chance '" + value + "' on subspell '" + data + "'.");
								continue;
							}

							chance = chanceData;
						}
					}
					default ->
						MagicSpells.error("Invalid cast argument '" + castArgument + "' on subspell '" + data + "'.");
				}
			}
		}
	}

	public boolean process() {
		spell = MagicSpells.getSpellByInternalName(spellName);
		if (spell != null) {
			isTargetedEntity = spell instanceof TargetedEntitySpell;
			isTargetedLocation = spell instanceof TargetedLocationSpell;
			isTargetedEntityFromLocation = spell instanceof TargetedEntityFromLocationSpell;

			switch (targeting) {
				case NONE -> {
					isTargetedEntity = false;
					isTargetedLocation = false;
					isTargetedEntityFromLocation = false;
				}
				case ENTITY -> {
					if (!isTargetedEntity)
						return false;

					isTargetedLocation = false;
					isTargetedEntityFromLocation = false;
				}
				case LOCATION -> {
					if (!isTargetedLocation)
						return false;

					isTargetedEntity = false;
					isTargetedEntityFromLocation = false;
				}
				case ENTITY_FROM_LOCATION -> {
					if (!isTargetedEntityFromLocation)
						return false;

					isTargetedEntity = false;
					isTargetedLocation = false;
				}
			}
		}

		return spell != null;
	}

	public Spell getSpell() {
		return spell;
	}

	public boolean isTargetedEntitySpell() {
		return isTargetedEntity;
	}

	public boolean isTargetedLocationSpell() {
		return isTargetedLocation;
	}

	public boolean isTargetedEntityFromLocationSpell() {
		return isTargetedEntityFromLocation;
	}

	private static boolean canCast() {
		return MagicSpells.isLoaded();
	}

	public boolean subcast(@Nullable LivingEntity caster, @NotNull Location location, @NotNull LivingEntity target,
			float power, @Nullable String[] args) {
		return subcast(caster, location, target, power, args, passTargeting, true);
	}

	public boolean subcast(@Nullable LivingEntity caster, @NotNull Location location, @NotNull LivingEntity target,
			float power, @Nullable String[] args, boolean passTargeting) {
		return subcast(caster, location, target, power, args, passTargeting, true);
	}

	public boolean subcast(@Nullable LivingEntity caster, @NotNull Location location, @NotNull LivingEntity target,
			float power, @Nullable String[] args, boolean passTargeting, boolean useTargetForLocation) {
		if (invert) {
			if (caster != null) {
				LivingEntity temp = caster;
				caster = target;
				target = temp;
			} else
				caster = target;
		}

		CastTargeting targeting = this.targeting;
		if (targeting == CastTargeting.NORMAL) {
			if (spell instanceof TargetedEntityFromLocationSpell)
				targeting = CastTargeting.ENTITY_FROM_LOCATION;
			else if (spell instanceof TargetedEntitySpell)
				targeting = CastTargeting.ENTITY;
			else if (spell instanceof TargetedLocationSpell)
				targeting = CastTargeting.LOCATION;
			else
				targeting = CastTargeting.NONE;
		}

		return switch (targeting) {
			case ENTITY_FROM_LOCATION ->
				spell instanceof TargetedEntityFromLocationSpell
						&& castAtEntityFromLocation(caster, location, target, power, passTargeting);
			case ENTITY -> spell instanceof TargetedEntitySpell && castAtEntity(caster, target, power, passTargeting);
			case LOCATION ->
				spell instanceof TargetedLocationSpell
						&& castAtLocation(caster, useTargetForLocation ? target.getLocation() : location, args, power);
			case NONE -> {
				if (caster == null)
					yield false;

				PostCastAction action = cast(caster, power);
				yield action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.NO_MESSAGES;
			}
			default -> false;
		};
	}

	public boolean subcast(@Nullable LivingEntity caster, @NotNull LivingEntity target, float power,
			@Nullable String[] args) {
		return subcast(caster, target, power, args, passTargeting);
	}

	public boolean subcast(@Nullable LivingEntity caster, @NotNull LivingEntity target, float power,
			@Nullable String[] args, boolean passTargeting) {
		if (invert) {
			if (caster != null) {
				LivingEntity temp = caster;
				caster = target;
				target = temp;
			} else
				caster = target;
		}

		CastTargeting targeting = this.targeting;
		if (targeting == CastTargeting.NORMAL) {
			if (spell instanceof TargetedEntitySpell)
				targeting = CastTargeting.ENTITY;
			else if (spell instanceof TargetedLocationSpell)
				targeting = CastTargeting.LOCATION;
			else
				targeting = CastTargeting.NONE;
		}

		return switch (targeting) {
			case ENTITY -> spell instanceof TargetedEntitySpell && castAtEntity(caster, target, power, passTargeting);
			case LOCATION ->
				spell instanceof TargetedLocationSpell && castAtLocation(caster, target.getLocation(), args, power);
			case NONE -> {
				if (caster == null)
					yield false;

				PostCastAction action = cast(caster, power);
				yield action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.NO_MESSAGES;
			}
			default -> false;
		};
	}

	public boolean subcast(@Nullable LivingEntity caster, @NotNull Location target, float power,
			@Nullable String[] args) {
		if (invert && caster != null)
			return subcast(caster, target, caster, power, args);

		CastTargeting targeting = this.targeting;
		if (targeting == CastTargeting.NORMAL) {
			if (spell instanceof TargetedLocationSpell)
				targeting = CastTargeting.LOCATION;
			else
				targeting = CastTargeting.NONE;
		}

		return switch (targeting) {
			case LOCATION -> spell instanceof TargetedLocationSpell && castAtLocation(caster, target, args, power);
			case NONE -> {
				if (caster == null)
					yield false;

				PostCastAction action = cast(caster, power);
				yield action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.NO_MESSAGES;
			}
			default -> false;
		};
	}

	public boolean subcast(@NotNull LivingEntity caster, float power, @Nullable String[] args) {
		if (invert)
			return subcast(caster, caster, power, args);
		if (targeting != CastTargeting.NORMAL && targeting != CastTargeting.NONE)
			return false;

		PostCastAction action = cast(caster, power);
		return action == PostCastAction.HANDLE_NORMALLY || action == PostCastAction.NO_MESSAGES;
	}

	public PostCastAction cast(LivingEntity caster, float power) {
		return cast(caster, power, null);
	}

	public PostCastAction cast(LivingEntity caster, float power, String[] args) {
		if (!canCast())
			return PostCastAction.ALREADY_HANDLED;

		if (!passPower)
			power = 1f;

		double chance = this.chance.get(caster, power, args);
		if ((chance > 0 && chance < 1) && random.nextDouble() > chance)
			return PostCastAction.ALREADY_HANDLED;

		int delay = this.delay.get(caster, power, args);
		if (delay < 0)
			return castReal(caster, power, args);

		float finalPower = power;
		MagicSpells.scheduleDelayedTask(() -> castReal(caster, finalPower, args), delay);

		return PostCastAction.HANDLE_NORMALLY;
	}

	private PostCastAction castReal(LivingEntity caster, float basePower, String[] args) {
		if (!canCast())
			return PostCastAction.ALREADY_HANDLED;
		try (var audit = PerformanceDiagnostics.EXECUTION.enter(Operation.SUBSPELL, spell.getInternalName(), "")) {

			float power = basePower * subPower.get(caster, basePower, null);
			args = this.args.get(caster, power, args);

			return switch (mode) {
				case HARD, FULL -> spell.cast(caster, power, args).action;
				case DIRECT -> spell.castSpell(caster, SpellCastState.NORMAL, power, args);
				case PARTIAL -> {
					SpellCastEvent castEvent = new SpellCastEvent(spell, caster, SpellCastState.NORMAL, power, args, 0,
							null, 0);
					if (!castEvent.callEvent() || castEvent.getSpellCastState() != SpellCastState.NORMAL)
						yield PostCastAction.ALREADY_HANDLED;

					power = castEvent.getPower();

					PostCastAction action = spell.castSpell(caster, SpellCastState.NORMAL, power, args);
					new SpellCastedEvent(spell, caster, SpellCastState.NORMAL, power, args, 0, null, action);

					yield action;
				}
			};
		}
	}

	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null, passTargeting);
	}

	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, boolean passTargeting) {
		return castAtEntity(caster, target, power, null, passTargeting | this.passTargeting);
	}

	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args,
			boolean passTargeting) {
		if (!canCast())
			return false;

		if (!passPower)
			power = 1f;

		double chance = this.chance.get(caster, power, args);
		if ((chance > 0 && chance < 1) && random.nextDouble() > chance)
			return false;

		int delay = this.delay.get(caster, power, args);
		if (delay < 0)
			return castAtEntityReal(caster, target, power, args, passTargeting);

		float finalPower = power;
		MagicSpells.scheduleDelayedTask(() -> castAtEntityReal(caster, target, finalPower, args, passTargeting), delay);

		return true;
	}

	private boolean castAtEntityReal(LivingEntity caster, LivingEntity target, float basePower, String[] args,
			boolean passTargeting) {
		if (!canCast())
			return false;
		try (var audit = PerformanceDiagnostics.EXECUTION.enter(Operation.SUBSPELL, spell.getInternalName(), "")) {

			if (!isTargetedEntity)
				return isTargetedLocation && castAtLocationReal(caster, target.getLocation(), basePower, args);

			float power = basePower * subPower.get(caster, basePower, args);
			args = this.args.get(caster, power, args);

			return switch (mode) {
				case HARD -> {
					if (caster == null)
						yield false;

					SpellCastResult result = spell.cast(caster, target.getLocation(), power, args);
					yield result.state == SpellCastState.NORMAL && (result.action == PostCastAction.HANDLE_NORMALLY
							|| result.action == PostCastAction.NO_MESSAGES);
				}
				case DIRECT -> {
					if (passTargeting)
						yield passTargetingEntity(caster, target, power, args);
					else {
						TargetedEntitySpell targetedSpell = (TargetedEntitySpell) spell;
						yield caster != null ? targetedSpell.castAtEntity(caster, target, power, args)
								: targetedSpell.castAtEntity(target, power, args);
					}
				}
				case PARTIAL -> {
					SpellCastEvent castEvent = new SpellCastEvent(spell, caster, SpellCastState.NORMAL, power, args, 0,
							null, 0, target.getLocation());
					if (!castEvent.callEvent() || castEvent.getSpellCastState() != SpellCastState.NORMAL)
						yield false;

					SpellTargetEvent targetEvent = new SpellTargetEvent(spell, caster, target, castEvent.getPower(), args);
					if (!targetEvent.callEvent())
						yield false;

					target = targetEvent.getTarget();
					power = targetEvent.getPower();

					boolean success;
					if (passTargeting)
						success = passTargetingEntity(caster, target, power, args);
					else {
						TargetedEntitySpell targetedEntitySpell = (TargetedEntitySpell) spell;
						success = caster != null ? targetedEntitySpell.castAtEntity(caster, target, power, args)
								: targetedEntitySpell.castAtEntity(target, power, args);
					}

					if (success)
						new SpellCastedEvent(spell, caster, SpellCastState.NORMAL, power, args, 0, null,
								PostCastAction.HANDLE_NORMALLY).callEvent();

					yield success;
				}
				case FULL -> {
					if (caster == null)
						yield false;

					SpellCastEvent castEvent = spell.preCast(caster, target.getLocation(), power, args);
					if (castEvent == null)
						yield false;

					PostCastAction action = PostCastAction.HANDLE_NORMALLY;
					boolean success = false;
					if (castEvent.getSpellCastState() == SpellCastState.NORMAL) {
						SpellTargetEvent targetEvent = new SpellTargetEvent(spell, caster, target, castEvent.getPower(),
								args);
						if (targetEvent.callEvent()) {
							target = targetEvent.getTarget();
							power = targetEvent.getPower();

							if (passTargeting)
								success = passTargetingEntity(caster, target, power, args);
							else
								((TargetedEntitySpell) spell).castAtEntity(caster, target, power, args);
						}
					}

					if (success) {
						if (spell instanceof TargetedSpell targetedSpell) {
							action = PostCastAction.NO_MESSAGES;
							targetedSpell.sendMessages(caster, target, args);
						}
					} else
						action = PostCastAction.ALREADY_HANDLED;

					spell.postCast(castEvent, action);

					yield success;
				}
			};
		}
	}

	public boolean passTargetingEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		ValidTargetList list = spell.getValidTargetList();
		ValidTargetList originalList = list.clone();
		if (Objects.equals(caster, target) && !list.canTargetSelf())
			list.setTargetCaster(true);
		if (!list.canTargetEntity(target)) {
			list.addEntityTarget(target);
			spell.setValidTargetList(list);
		}

		boolean success = caster != null ? ((TargetedEntitySpell) spell).castAtEntity(caster, target, power, args)
				: ((TargetedEntitySpell) spell).castAtEntity(target, power, args);
		spell.setValidTargetList(originalList);
		return success;
	}

	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return castAtLocation(caster, target, null, power);
	}

	public boolean castAtLocation(LivingEntity caster, Location target, String[] args, float power) {
		if (!canCast())
			return false;

		if (!passPower)
			power = 1f;

		double chance = this.chance.get(caster, power, args);
		if ((chance > 0 && chance < 1) && random.nextDouble() > chance)
			return false;

		int delay = this.delay.get(caster, power, args);
		if (delay < 0)
			return castAtLocationReal(caster, target, power, args);

		float finalPower = power;
		MagicSpells.scheduleDelayedTask(() -> castAtLocationReal(caster, target, finalPower, args), delay);

		return true;
	}

	private boolean castAtLocationReal(LivingEntity caster, Location target, float basePower, String[] args) {
		if (!canCast())
			return false;
		try (var audit = PerformanceDiagnostics.EXECUTION.enter(Operation.SUBSPELL, spell.getInternalName(), "")) {

			if (!isTargetedLocation)
				return false;

			float power = basePower * subPower.get(caster, basePower, args);
			args = this.args.get(caster, power, args);

			return switch (mode) {
				case HARD -> {
					if (caster == null)
						yield false;

					SpellCastResult result = spell.cast(caster, target, power, args);
					yield result.state == SpellCastState.NORMAL && (result.action == PostCastAction.HANDLE_NORMALLY ||
							result.action == PostCastAction.NO_MESSAGES);
				}
				case DIRECT -> {
					TargetedLocationSpell targetedSpell = (TargetedLocationSpell) spell;
					yield caster != null ? targetedSpell.castAtLocation(caster, target, power, args)
							: targetedSpell.castAtLocation(target, power, args);
				}
				case PARTIAL -> {
					SpellCastEvent castEvent = new SpellCastEvent(spell, caster, SpellCastState.NORMAL, power, args, 0,
							null, 0, target);
					if (!castEvent.callEvent() || castEvent.getSpellCastState() != SpellCastState.NORMAL)
						yield false;

					SpellTargetLocationEvent targetEvent = new SpellTargetLocationEvent(spell, caster, target,
							castEvent.getPower(), args);
					if (!targetEvent.callEvent())
						yield false;

					target = targetEvent.getTargetLocation();
					power = targetEvent.getPower();

					TargetedLocationSpell targetedSpell = (TargetedLocationSpell) spell;
					boolean success = caster != null ? targetedSpell.castAtLocation(caster, target, power, args)
							: targetedSpell.castAtLocation(target, power, args);

					if (success)
						new SpellCastedEvent(spell, caster, SpellCastState.NORMAL, power, args, 0, null,
								PostCastAction.HANDLE_NORMALLY).callEvent();

					yield success;
				}
				case FULL -> {
					if (caster == null)
						yield false;

					SpellCastEvent castEvent = spell.preCast(caster, target, power, args);
					if (castEvent == null)
						yield false;

					PostCastAction action = PostCastAction.HANDLE_NORMALLY;
					boolean success = false;
					if (castEvent.getSpellCastState() == SpellCastState.NORMAL) {
						SpellTargetLocationEvent targetEvent = new SpellTargetLocationEvent(spell, caster, target,
								castEvent.getPower(), args);
						if (targetEvent.callEvent()) {
							target = targetEvent.getTargetLocation();
							power = targetEvent.getPower();

							success = ((TargetedLocationSpell) spell).castAtLocation(caster, target, power, args);
						}
					}

					if (!success)
						action = PostCastAction.ALREADY_HANDLED;

					spell.postCast(castEvent, action);

					yield success;
				}
			};
		}
	}

	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power) {
		return castAtEntityFromLocation(caster, from, target, power, null, passTargeting);
	}

	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			boolean passTargeting) {
		return castAtEntityFromLocation(caster, from, target, power, null, passTargeting | this.passTargeting);
	}

	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args, boolean passTargeting) {
		if (!canCast())
			return false;

		if (!passPower)
			power = 1f;

		double chance = this.chance.get(caster, power, args);
		if ((chance > 0 && chance < 1) && random.nextDouble() > chance)
			return false;

		int delay = this.delay.get(caster, power, args);
		if (delay < 0)
			return castAtEntityFromLocationReal(caster, from, target, power, args, passTargeting);

		float finalPower = power;
		MagicSpells.scheduleDelayedTask(
				() -> castAtEntityFromLocationReal(caster, from, target, finalPower, args, passTargeting), delay);

		return true;
	}

	private boolean castAtEntityFromLocationReal(LivingEntity caster, Location from, LivingEntity target,
			float basePower, String[] args, boolean passTargeting) {
		if (!canCast())
			return false;
		try (var audit = PerformanceDiagnostics.EXECUTION.enter(Operation.SUBSPELL, spell.getInternalName(), "")) {

			if (!isTargetedEntityFromLocation)
				return false;

			float power = basePower * subPower.get(caster, basePower, args);
			args = this.args.get(caster, power, args);

			return switch (mode) {
				case HARD -> {
					if (caster == null)
						yield false;

					SpellCastResult result = spell.cast(caster, from, power, args);
					yield result.state == SpellCastState.NORMAL && (result.action == PostCastAction.HANDLE_NORMALLY ||
							result.action == PostCastAction.NO_MESSAGES);
				}
				case DIRECT -> {
					if (passTargeting)
						yield passTargetingEntityFromLocation(caster, from, target, power, args);
					else {
						TargetedEntityFromLocationSpell targetedSpell = (TargetedEntityFromLocationSpell) spell;
						yield caster != null ? targetedSpell.castAtEntityFromLocation(caster, from, target, power, args)
								: targetedSpell.castAtEntityFromLocation(from, target, power, args);
					}
				}
				case PARTIAL -> {
					SpellCastEvent castEvent = new SpellCastEvent(spell, caster, SpellCastState.NORMAL, power, args, 0,
							null, 0, from);
					if (!castEvent.callEvent() || castEvent.getSpellCastState() != SpellCastState.NORMAL)
						yield false;

					SpellTargetEvent targetEntityEvent = new SpellTargetEvent(spell, caster, target, castEvent.getPower(),
							args);
					if (!targetEntityEvent.callEvent())
						yield false;

					SpellTargetLocationEvent targetLocationEvent = new SpellTargetLocationEvent(spell, caster, from,
							targetEntityEvent.getPower(), args);
					if (!targetLocationEvent.callEvent())
						yield false;

					target = targetEntityEvent.getTarget();
					power = targetLocationEvent.getPower();
					from = targetLocationEvent.getTargetLocation();

					boolean success;
					if (passTargeting)
						success = passTargetingEntityFromLocation(caster, from, target, power, args);
					else {
						TargetedEntityFromLocationSpell targetedSpell = (TargetedEntityFromLocationSpell) spell;
						success = caster != null ? targetedSpell.castAtEntityFromLocation(caster, from, target, power, args)
								: targetedSpell.castAtEntityFromLocation(from, target, power, args);
					}

					if (success)
						new SpellCastedEvent(spell, caster, SpellCastState.NORMAL, power, args, 0, null,
								PostCastAction.HANDLE_NORMALLY).callEvent();

					yield success;
				}
				case FULL -> {
					if (caster == null)
						yield false;

					SpellCastEvent castEvent = spell.preCast(caster, from, power, args);
					if (castEvent == null)
						yield false;

					PostCastAction action = PostCastAction.HANDLE_NORMALLY;
					boolean success = false;
					if (castEvent.getSpellCastState() == SpellCastState.NORMAL) {
						SpellTargetEvent targetEntityEvent = new SpellTargetEvent(spell, caster, target,
								castEvent.getPower(), args);
						if (targetEntityEvent.callEvent()) {
							target = targetEntityEvent.getTarget();
							power = targetEntityEvent.getPower();

							SpellTargetLocationEvent targetLocationEvent = new SpellTargetLocationEvent(spell, caster, from,
									power, args);
							if (targetLocationEvent.callEvent()) {
								power = targetLocationEvent.getPower();
								from = targetLocationEvent.getTargetLocation();

								if (passTargeting)
									success = passTargetingEntityFromLocation(caster, from, target, power, args);
								else
									success = ((TargetedEntityFromLocationSpell) spell).castAtEntityFromLocation(caster,
											from, target, power, args);
							}
						}
					}

					if (success) {
						if (spell instanceof TargetedSpell targetedSpell) {
							action = PostCastAction.NO_MESSAGES;
							targetedSpell.sendMessages(caster, target, args);
						}
					} else
						action = PostCastAction.ALREADY_HANDLED;

					spell.postCast(castEvent, action);

					yield success;
				}
			};
		}
	}

	public boolean passTargetingEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		ValidTargetList list = spell.getValidTargetList();
		ValidTargetList originalList = list.clone();
		if (Objects.equals(caster, target) && !list.canTargetSelf())
			list.setTargetCaster(true);
		if (!list.canTargetEntity(target)) {
			list.addEntityTarget(target);
			spell.setValidTargetList(list);
		}

		boolean success = caster != null
				? ((TargetedEntityFromLocationSpell) spell).castAtEntityFromLocation(caster, from, target, power, args)
				: ((TargetedEntityFromLocationSpell) spell).castAtEntityFromLocation(from, target, power, args);
		spell.setValidTargetList(originalList);
		return success;
	}

	public void setCastMode(CastMode mode) {
		this.mode = mode;
	}

	public enum CastMode {

		HARD("hard", "h"),
		FULL("full", "f"),
		PARTIAL("partial", "p"),
		DIRECT("direct", "d");

		private static final Map<String, CastMode> nameMap = new HashMap<>();

		private final String[] names;

		CastMode(String... names) {
			this.names = names;
		}

		public static CastMode getFromString(String label) {
			return nameMap.get(label.toLowerCase());
		}

		static {
			for (CastMode mode : CastMode.values()) {
				nameMap.put(mode.name().toLowerCase(), mode);
				for (String s : mode.names) {
					nameMap.put(s.toLowerCase(), mode);
				}
			}
		}

	}

	public enum CastTargeting {

		NORMAL,
		ENTITY_FROM_LOCATION,
		ENTITY,
		LOCATION,
		NONE

	}

}
