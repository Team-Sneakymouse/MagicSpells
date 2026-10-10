package com.nisovin.magicspells.castmodifiers;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.VariableMod;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.ManaChangeEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.util.VariableMod.VariableOwner;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.castmodifiers.customdata.CustomData;
import com.nisovin.magicspells.events.MagicSpellsGenericPlayerEvent;
import com.nisovin.magicspells.castmodifiers.customdata.CustomDataFloat;

public enum ModifierType {

	REQUIRED(false, "required", "require") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (!check)
				event.setCancelled(true);
			return check;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			if (!check)
				event.setNewAmount(event.getOldAmount());
			return check;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			if (!check)
				event.setCancelled(true);
			return check;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return check;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			if (!check)
				event.setCancelled(true);
			return check;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			if (!check)
				event.setCancelled(true);
			return check;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			return result;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			return result;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			return result;
		}

	},

	DENIED(false, "denied", "deny") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (check)
				event.setCancelled(true);
			return !check;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			if (check)
				event.setNewAmount(event.getOldAmount());
			return !check;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			if (check)
				event.setCancelled(true);
			return !check;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return !check;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			if (check)
				event.setCancelled(true);
			return !check;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			if (check)
				event.setCancelled(true);
			return !check;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			return new ModifierResult(result.data(), !result.check());
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			return new ModifierResult(result.data(), !result.check());
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			return new ModifierResult(result.data(), !result.check());
		}

	},

	POWER(true, "power", "empower", "multiply") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (check)
				event.increasePower((CustomDataFloat.from(customData, event)));
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			if (check) {
				int gain = event.getNewAmount() - event.getOldAmount();
				gain = Math.round(gain * CustomDataFloat.from(customData, event));
				int newAmt = event.getOldAmount() + gain;
				if (newAmt > event.getMaxMana())
					newAmt = event.getMaxMana();
				event.setNewAmount(newAmt);
			}
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			if (check)
				event.increasePower(CustomDataFloat.from(customData, event));
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			if (result.check()) {
				SpellData data = result.data();
				return new ModifierResult(data.power(data.power() * CustomDataFloat.from(customData, data)), true);
			}

			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			if (result.check()) {
				SpellData data = result.data();
				return new ModifierResult(data.power(data.power() * CustomDataFloat.from(customData, data)), true);
			}

			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			if (result.check()) {
				SpellData data = result.data();
				return new ModifierResult(data.power(data.power() * CustomDataFloat.from(customData, data)), true);
			}

			return new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			return new CustomDataFloat(text);
		}

	},

	ADD_POWER(true, "addpower", "add") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (check)
				event.setPower(event.getPower() + CustomDataFloat.from(customData, event));
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			if (check) {
				int newAmt = event.getNewAmount() + (int) CustomDataFloat.from(customData, event);
				if (newAmt > event.getMaxMana())
					newAmt = event.getMaxMana();
				if (newAmt < 0)
					newAmt = 0;
				event.setNewAmount(newAmt);
			}
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			if (check)
				event.setPower(event.getPower() + CustomDataFloat.from(customData, event));
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			if (result.check()) {
				SpellData data = result.data();
				return new ModifierResult(data.power(data.power() + CustomDataFloat.from(customData, data)), true);
			}

			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			if (result.check()) {
				SpellData data = result.data();
				return new ModifierResult(data.power(data.power() + CustomDataFloat.from(customData, data)), true);
			}

			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			if (result.check()) {
				SpellData data = result.data();
				return new ModifierResult(data.power(data.power() + CustomDataFloat.from(customData, data)), true);
			}

			return new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			return new CustomDataFloat(text);
		}

	},

	COOLDOWN(true, "cooldown") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (check)
				event.setCooldown(CustomDataFloat.from(customData, event));
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			return new CustomDataFloat(text);
		}

	},

	REAGENTS(true, "reagents") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (check)
				event.setReagents(event.getReagents().multiply(CustomDataFloat.from(customData, event)));
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			return new CustomDataFloat(text);
		}

	},

	CAST_TIME(true, "casttime") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (check)
				event.setCastTime((int) CustomDataFloat.from(customData, event));
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			return new CustomDataFloat(text);
		}

	},

	STOP(false, "stop") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			return !check;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			return !check;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			return !check;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return !check;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			return !check;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			return !check;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			return new ModifierResult(result.data(), !result.check());
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			return new ModifierResult(result.data(), !result.check());
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			return new ModifierResult(result.data(), !result.check());
		}

	},

	CONTINUE(false, "continue") {

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			return check;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			return check;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			return check;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			return check;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			return check;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			return check;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			return result;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			return result;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			return result;
		}

	},

	CAST(true, "cast") {

		class CastData extends CustomData {

			public String invalidText;

			public Subspell spell;

			@Override
			public boolean isValid() {
				return spell != null;
			}

			@Override
			public String getInvalidText() {
				return invalidText;
			}

		}

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			CastData data = (CastData) customData;
			if (check && data.isValid())
				data.spell.subcast(event.getSpellData());
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			CastData data = (CastData) customData;
			if (check && data.isValid())
				data.spell.subcast(new SpellData(event.getPlayer(), 1f, null));
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			CastData data = (CastData) customData;
			if (check && data.isValid())
				data.spell.subcast(event.getSpellData().target(event.getCaster()));
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			CastData data = (CastData) customData;
			if (check && data.isValid())
				data.spell.subcast(new SpellData(event.getCaster(), event.getPower(), event.getSpellArgs()));
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			CastData data = (CastData) customData;
			if (check && data.isValid())
				data.spell.subcast(event.getSpellData());
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			CastData data = (CastData) customData;
			if (check && data.isValid())
				data.spell.subcast(new SpellData(event.getPlayer(), 1f, null));
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			CastData data = (CastData) customData;
			if (result.check() && data.isValid())
				data.spell.subcast(result.data());
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			CastData data = (CastData) customData;
			if (result.check() && data.isValid())
				data.spell.subcast(result.data().target(target));
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			CastData data = (CastData) customData;
			if (result.check() && data.isValid())
				data.spell.subcast(result.data().location(target));
			return result.check() ? result : new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			CastData data = new CastData();
			if (text == null) {
				data.invalidText = "No spell defined.";
				return data;
			}

			Subspell spell = new Subspell(text);
			if (spell.process())
				data.spell = spell;
			else
				data.invalidText = "Spell '" + text + "' does not exist.";

			return data;
		}

	},

	CAST_INSTEAD(true, "castinstead") {

		class CustomInsteadData extends CustomData {

			public String invalidText;

			public Subspell spell;

			@Override
			public boolean isValid() {
				return spell != null;
			}

			@Override
			public String getInvalidText() {
				return invalidText;
			}

		}

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (check && data.isValid()) {
				data.spell.subcast(event.getSpellData());
				event.setCancelled(true);
			}
			return !check;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (check && data.isValid())
				data.spell.subcast(new SpellData(event.getPlayer(), 1f, null));
			return !check;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (check && data.isValid()) {
				data.spell.subcast(event.getSpellData());
				event.setCancelled(true);
				event.setCastCancelled(true);
			}
			return !check;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (check && data.isValid()) {
				data.spell.subcast(new SpellData(event.getCaster(), event.getPower(), event.getSpellArgs()));
			}
			return !check;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (check && data.isValid()) {
				data.spell.subcast(event.getSpellData());
				event.setCancelled(true);
			}
			return !check;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (check && data.isValid())
				data.spell.subcast(new SpellData(event.getPlayer(), 1f, null));
			return !check;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (result.check() && data.isValid())
				data.spell.subcast(result.data());
			return new ModifierResult(result.data(), !result.check());
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (result.check() && data.isValid())
				data.spell.subcast(result.data().target(target));
			return new ModifierResult(result.data(), !result.check());
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			CustomInsteadData data = (CustomInsteadData) customData;
			if (result.check() && data.isValid())
				data.spell.subcast(result.data().location(target));
			return new ModifierResult(result.data(), !result.check());
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			CustomInsteadData data = new CustomInsteadData();
			if (text == null) {
				data.invalidText = "No spell defined.";
				return data;
			}

			Subspell spell = new Subspell(text);
			if (spell.process())
				data.spell = spell;
			else
				data.invalidText = "Spell '" + text + "' does not exist.";

			return data;
		}

	},

	VARIABLE_MODIFY(true, "variable") {

		class VariableModData extends CustomData {

			private String invalidText = "Variable action is invalid.";

			public VariableOwner variableOwner;
			public Variable variable;
			public VariableMod mod;

			@Override
			public boolean isValid() {
				return variable != null;
			}

			@Override
			public String getInvalidText() {
				return invalidText;
			}

		}

		private void modifyVariable(CustomData customData, Player caster, Player target, float power, String[] args) {
			if (!customData.isValid())
				return;
			VariableModData data = (VariableModData) customData;

			boolean needsTarget = data.variableOwner == VariableOwner.TARGET
					|| (data.mod.getVariableOwner() == VariableOwner.TARGET && !data.mod.isConstantValue());
			if (needsTarget && target == null)
				return;

			Player owner = data.variableOwner == VariableOwner.CASTER ? caster : target;
			MagicSpells.getVariableManager().processVariableMods(data.variable, data.mod, owner, caster, target, power,
					args);
		}

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check)
				modifyVariable(customData, caster, null, event.getPower(), event.getSpellArgs());
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			if (check)
				modifyVariable(customData, event.getPlayer(), null, 1f, null);
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check) {
				Player target = event.getTarget() instanceof Player p ? p : null;
				modifyVariable(customData, caster, target, event.getPower(), event.getSpellArgs());
			}
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check)
				modifyVariable(customData, caster, null, event.getPower(), event.getSpellArgs());
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check)
				modifyVariable(customData, caster, null, event.getPower(), event.getSpellArgs());
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			if (check)
				modifyVariable(customData, event.getPlayer(), null, 1f, null);
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			if (!(caster instanceof Player player))
				return result.check() ? new ModifierResult(result.data(), false) : result;
			if (result.check()) {
				modifyVariable(customData, player, null, result.data().power(), result.data().args());
			}
			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			if (!(caster instanceof Player player))
				return result.check() ? new ModifierResult(result.data(), false) : result;
			if (result.check()) {
				modifyVariable(customData, player, target instanceof Player playerTarget ? playerTarget : null,
						result.data().power(), result.data().args());
			}
			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			if (!(caster instanceof Player player))
				return result.check() ? new ModifierResult(result.data(), false) : result;
			if (result.check()) {
				modifyVariable(customData, player, null, result.data().power(), result.data().args());
			}
			return new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			// input format
			// [<caster|target>:]<variableToModify>;[=|+|*|/][-]<amount|[<caster|target>:]<modifyingVariableName>>
			VariableModData data = new VariableModData();
			if (text == null) {
				data.invalidText = "No data action data defined.";
				return data;
			}

			if (!text.contains(";")) {
				data.invalidText = "Data is invalid.";
				return data;
			}

			String[] splits = text.split(";", 2);
			if (splits.length < 2) {
				data.invalidText = "VarMod is not defined.";
				return data;
			}

			String varData = splits[0];
			VariableOwner variableOwner = VariableOwner.CASTER;
			String variableName;
			if (varData.contains(":")) {
				String[] varDataSplits = varData.split(":");
				if (varDataSplits[0].startsWith("target"))
					variableOwner = VariableOwner.TARGET;
				variableName = varDataSplits[1];
			} else
				variableName = varData;

			data.variableOwner = variableOwner;
			data.mod = new VariableMod(splits[1]);
			data.variable = MagicSpells.getVariableManager().getVariable(variableName);
			if (data.variable == null)
				data.invalidText = "Variable does not exist.";
			return data;
		}

	},

	STRING(true, "string") {

		class StringData extends CustomData {

			public String invalidText;

			public Variable variable;
			public String value;

			@Override
			public boolean isValid() {
				return variable != null && value != null;
			}

			@Override
			public String getInvalidText() {
				return invalidText;
			}

		}

		private void setVariable(Player player, StringData data) {
			data.variable.parseAndSet(player, data.value);
		}

		@Override
		public boolean apply(SpellCastEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check)
				setVariable(caster, (StringData) customData);
			return true;
		}

		@Override
		public boolean apply(ManaChangeEvent event, boolean check, CustomData customData) {
			if (check)
				setVariable(event.getPlayer(), (StringData) customData);
			return true;
		}

		@Override
		public boolean apply(SpellTargetEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check)
				setVariable(caster, (StringData) customData);
			return true;
		}

		@Override
		public boolean apply(SpellCastedEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check)
				setVariable(caster, (StringData) customData);
			return true;
		}

		@Override
		public boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData) {
			if (!(event.getCaster() instanceof Player caster))
				return false;
			if (check)
				setVariable(caster, (StringData) customData);
			return true;
		}

		@Override
		public boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData) {
			if (check)
				setVariable(event.getPlayer(), (StringData) customData);
			return true;
		}

		@Override
		public ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData) {
			if (!(caster instanceof Player player))
				return result.check() ? new ModifierResult(result.data(), false) : result;
			if (result.check()) {
				setVariable(player, (StringData) customData);
				return result;
			}
			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
				CustomData customData) {
			if (!(caster instanceof Player player))
				return result.check() ? new ModifierResult(result.data(), false) : result;
			if (result.check()) {
				setVariable(player, (StringData) customData);
				return result;
			}
			return new ModifierResult(result.data(), true);
		}

		@Override
		public ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
				CustomData customData) {
			if (!(caster instanceof Player player))
				return result.check() ? new ModifierResult(result.data(), false) : result;
			if (result.check()) {
				setVariable(player, (StringData) customData);
				return result;
			}
			return new ModifierResult(result.data(), true);
		}

		@Override
		public CustomData buildCustomActionData(String text) {
			StringData data = new StringData();
			if (text == null || text.trim().isEmpty() || !text.contains(" ")) {
				data.invalidText = "Data is invalid.";
				return data;
			}

			String[] splits = text.split(" ", 2);
			data.variable = MagicSpells.getVariableManager().getVariable(splits[0]);
			if (data.variable == null)
				data.invalidText = "Variable does not exist.";
			data.value = splits[1];
			return data;
		}

	}

	;

	private final String[] keys;
	private static boolean initialized = false;

	private final boolean usesCustomData;

	ModifierType(boolean usesCustomData, String... keys) {
		this.keys = keys;
		this.usesCustomData = usesCustomData;
	}

	public boolean usesCustomData() {
		return usesCustomData;
	}

	public abstract boolean apply(SpellCastEvent event, boolean check, CustomData customData);

	public abstract boolean apply(ManaChangeEvent event, boolean check, CustomData customData);

	public abstract boolean apply(SpellTargetEvent event, boolean check, CustomData customData);

	public abstract boolean apply(SpellTargetLocationEvent event, boolean check, CustomData customData);

	public abstract boolean apply(MagicSpellsGenericPlayerEvent event, boolean check, CustomData customData);

	public abstract boolean apply(SpellCastedEvent event, boolean check, CustomData customData);

	public abstract ModifierResult apply(LivingEntity caster, ModifierResult result, CustomData customData);

	public abstract ModifierResult apply(LivingEntity caster, LivingEntity target, ModifierResult result,
			CustomData customData);

	public abstract ModifierResult apply(LivingEntity caster, Location target, ModifierResult result,
			CustomData customData);

	public CustomData buildCustomActionData(String text) {
		return null;
	}

	static Map<String, ModifierType> nameMap;

	static void initialize() {
		nameMap = new HashMap<>();
		for (ModifierType type : ModifierType.values()) {
			for (String key : type.keys) {
				nameMap.put(key.toLowerCase(), type);
			}
		}
		initialized = true;
	}

	public static ModifierType getModifierTypeByName(String name) {
		if (!initialized)
			initialize();
		return nameMap.get(name.toLowerCase());
	}

}
