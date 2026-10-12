package com.nisovin.magicspells.castmodifiers;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.events.*;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ModifierResult;

public class ModifierSet {

	public static ModifierListener modifierListener;

	public static void initializeModifierListeners() {
		modifierListener = new ModifierListener();
		MagicSpells.registerEvents(modifierListener);
	}

	public static void unload() {
		if (modifierListener != null) {
			modifierListener.unload();
			modifierListener = null;
		}
	}

	private final List<Modifier> modifiers;

	public ModifierSet(List<String> data) {
		this(data, null, false);
	}

	public ModifierSet(List<String> data, Spell spell) {
		this(data, spell, false);
	}

	public ModifierSet(List<String> data, boolean isFromManaSystem) {
		this(data, null, isFromManaSystem);
	}

	private ModifierSet(List<String> data, Spell spell, boolean isFromManaSystem) {
		modifiers = new ArrayList<>();
		for (String s : data) {
			Modifier m = new Modifier();
			m.process(s);

			if (!m.isInitialized()) {
				String extra = "";
				if (m.getCustomActionData() != null) extra = ": " + m.getCustomActionData().getInvalidText();

				if (isFromManaSystem) MagicSpells.error("Mana system has a problem with modifier '" + s + "'" + extra);
				else if (spell != null) MagicSpells.error("Spell '" + spell.getInternalName() + "' has a problem with modifier '" + s + "'" + extra);
				else MagicSpells.error("Problem with modifier: " + s + "'" + extra);
				continue;
			}

			if (m.getStrModifierFailed() == null) m.setStrModifierFailed(spell.getStrModifierFailed());

			modifiers.add(m);

			if (isFromManaSystem) MagicSpells.debug(3, "    Modifier added for mana system: " + s);
			else if (spell != null) MagicSpells.debug(3, "    Modifier added for spell '" + spell.getInternalName() + "': " + s);
			else MagicSpells.debug(3, "    Modifier added: " + s);
		}
	}

	public void apply(SpellCastEvent event) {
		for (Modifier modifier : modifiers) {
			boolean cont;
			try {
				cont = modifier.apply(event);
			} catch (Exception e) {
				MagicSpells.handleException(e);
				event.setCancelled(true);
				break;
			}
			if (cont) continue;

			if (modifier.getStrModifierFailed() != null)
				MagicSpells.sendMessage(modifier.getStrModifierFailed(), event.getCaster(), event.getSpellData().args());
			break;
		}
	}

	public void apply(ManaChangeEvent event) {
		for (Modifier modifier : modifiers) {
			boolean cont = modifier.apply(event);
			if (cont) continue;

			if (modifier.getStrModifierFailed() != null) MagicSpells.sendMessage(modifier.getStrModifierFailed(), event.getPlayer(), MagicSpells.NULL_ARGS);
			break;
		}
	}

	public void apply(SpellCastedEvent event) {
		for (Modifier modifier : modifiers) {
			boolean cont = modifier.apply(event);

			if (cont) continue;
			break;
		}
	}

	public void apply(SpellTargetEvent event) {
		for (Modifier modifier : modifiers) {
			boolean cont;
			try {
				cont = modifier.apply(event);
			} catch (Exception e) {
				MagicSpells.handleException(e);
				event.setCancelled(true);
				break;
			}
			if (cont) continue;

			if (modifier.getStrModifierFailed() != null)
				MagicSpells.sendMessage(modifier.getStrModifierFailed(), event.getCaster(), event.getSpellData().args());
			break;
		}
	}

	public void apply(MagicSpellsGenericPlayerEvent event) {
		for (Modifier modifier : modifiers) {
			boolean cont = modifier.apply(event);
			if (cont) continue;

			if (modifier.getStrModifierFailed() != null) MagicSpells.sendMessage(modifier.getStrModifierFailed(), event.getPlayer(), MagicSpells.NULL_ARGS);
			break;
		}
	}

	public void apply(SpellTargetLocationEvent event) {
		for (Modifier modifier : modifiers) {
			boolean cont = modifier.apply(event);
			if (cont) continue;

			if (modifier.getStrModifierFailed() != null)
				MagicSpells.sendMessage(modifier.getStrModifierFailed(), event.getCaster(), event.getSpellData().args());
			break;
		}
	}

	/**
	 * Caster-only modifier apply; condition checks use {@code data.caster()}.
	 */
	public ModifierResult apply(SpellData data) {
		for (Modifier modifier : modifiers) {
			ModifierResult result = modifier.apply(data);
			if (result.check()) {
				data = result.data();
				continue;
			}

			if (modifier.getStrModifierFailed() != null)
				MagicSpells.sendMessage(modifier.getStrModifierFailed(), data.caster(), result.data().args());
			return result;
		}

		return new ModifierResult(data, true);
	}

	/**
	 * Entity-target modifier apply; condition checks use {@code data.caster()} vs {@code data.target()}.
	 */
	public ModifierResult applyTarget(SpellData data) {
		for (Modifier modifier : modifiers) {
			ModifierResult result = modifier.applyTarget(data);
			if (result.check()) {
				data = result.data();
				continue;
			}

			if (modifier.getStrModifierFailed() != null)
				MagicSpells.sendMessage(modifier.getStrModifierFailed(), data.caster(), result.data().args());
			return result;
		}

		return new ModifierResult(data, true);
	}

	/**
	 * Location-target modifier apply; condition checks use {@code data.caster()} vs {@code data.location()}.
	 */
	public ModifierResult applyLocation(SpellData data) {
		for (Modifier modifier : modifiers) {
			ModifierResult result = modifier.applyLocation(data);
			if (result.check()) {
				data = result.data();
				continue;
			}

			if (modifier.getStrModifierFailed() != null)
				MagicSpells.sendMessage(modifier.getStrModifierFailed(), data.caster(), result.data().args());
			return result;
		}

		return new ModifierResult(data, true);
	}

	/**
	 * @deprecated use {@link #apply(SpellData)}
	 */
	@Deprecated
	public ModifierResult apply(Entity caster, SpellData data) {
		return apply(data.caster(caster));
	}

	/**
	 * @deprecated use {@link #applyTarget(SpellData)}
	 */
	@Deprecated
	public ModifierResult apply(Entity caster, Entity target, SpellData data) {
		return applyTarget(data.caster(caster).target(target));
	}

	/**
	 * @deprecated use {@link #applyLocation(SpellData)}
	 */
	@Deprecated
	public ModifierResult apply(Entity caster, Location target, SpellData data) {
		return applyLocation(data.caster(caster).location(target));
	}

	public boolean check(SpellData data) {
		for (Modifier modifier : modifiers) {
			if (!modifier.check(data)) return false;
		}
		return true;
	}

	public boolean checkTarget(SpellData data) {
		for (Modifier modifier : modifiers) {
			if (!modifier.checkTarget(data)) return false;
		}
		return true;
	}

	public boolean checkLocation(SpellData data) {
		for (Modifier modifier : modifiers) {
			if (!modifier.checkLocation(data)) return false;
		}
		return true;
	}

	/**
	 * @deprecated use {@link #check(SpellData)}
	 */
	@Deprecated
	public boolean check(Entity livingEntity) {
		return check(new SpellData(livingEntity));
	}

	/**
	 * @deprecated use {@link #checkTarget(SpellData)}
	 */
	@Deprecated
	public boolean check(Entity livingEntity, Entity entity) {
		return checkTarget(new SpellData(livingEntity, entity));
	}

	/**
	 * @deprecated use {@link #checkLocation(SpellData)}
	 */
	@Deprecated
	public boolean check(Entity livingEntity, Location location) {
		return checkLocation(new SpellData(livingEntity, location));
	}

}
