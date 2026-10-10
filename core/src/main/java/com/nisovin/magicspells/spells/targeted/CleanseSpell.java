package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.ValidTargetChecker;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

// TODO setup a system for registering "CleanseProvider"s
public class CleanseSpell extends TargetedSpell implements TargetedEntitySpell {

	private ValidTargetChecker checker;

	private List<String> toCleanse;
	private List<DotSpell> dotSpells;
	private List<StunSpell> stunSpells;
	private List<BuffSpell> buffSpells;
	private List<LoopSpell> loopSpells;
	private List<OrbitSpell> orbitSpells;
	private List<SilenceSpell> silenceSpells;
	private List<LevitateSpell> levitateSpells;
	private List<PotionEffectType> potionEffectTypes;

	private boolean fire;

	public CleanseSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		toCleanse = getConfigStringList("remove", Arrays.asList("fire", "hunger", "poison", "wither"));
		dotSpells = new ArrayList<>();
		stunSpells = new ArrayList<>();
		buffSpells = new ArrayList<>();
		loopSpells = new ArrayList<>();
		orbitSpells = new ArrayList<>();
		silenceSpells = new ArrayList<>();
		levitateSpells = new ArrayList<>();
		potionEffectTypes = new ArrayList<>();
		fire = false;
	}

	@Override
	public void initialize() {
		super.initialize();

		for (String s : toCleanse) {
			if (s.equalsIgnoreCase("fire")) {
				fire = true;
				continue;
			}

			if (s.startsWith("buff:")) {
				if (s.replace("buff:", "").equalsIgnoreCase("*")) {
					for (Spell spell : MagicSpells.getSpellsOrdered()) {
						if (spell instanceof BuffSpell) buffSpells.add((BuffSpell) spell);
					}
					continue;
				}
				Spell spell = MagicSpells.getSpellByInternalName(s.replace("buff:", ""));
				if (spell instanceof BuffSpell) buffSpells.add((BuffSpell) spell);
				continue;
			}

			if (s.startsWith("dot:")) {
				if (s.replace("dot:", "").equalsIgnoreCase("*")) {
					for (Spell spell : MagicSpells.getSpellsOrdered()) {
						if (spell instanceof DotSpell) dotSpells.add((DotSpell) spell);
					}
					continue;
				}
				Spell spell = MagicSpells.getSpellByInternalName(s.replace("dot:", ""));
				if (spell instanceof DotSpell) dotSpells.add((DotSpell) spell);
				continue;
			}

			if (s.startsWith("stun:")) {
				if (s.replace("stun:", "").equalsIgnoreCase("*")) {
					for (Spell spell : MagicSpells.getSpellsOrdered()) {
						if (spell instanceof StunSpell) stunSpells.add((StunSpell) spell);
					}
					continue;
				}
				Spell spell = MagicSpells.getSpellByInternalName(s.replace("stun:", ""));
				if (spell instanceof StunSpell) stunSpells.add((StunSpell) spell);
				continue;
			}

			if (s.startsWith("orbit:")) {
				if (s.replace("orbit:", "").equalsIgnoreCase("*")) {
					for (Spell spell : MagicSpells.getSpellsOrdered()) {
						if (spell instanceof OrbitSpell) orbitSpells.add((OrbitSpell) spell);
					}
					continue;
				}
				Spell spell = MagicSpells.getSpellByInternalName(s.replace("orbit:", ""));
				if (spell instanceof OrbitSpell) orbitSpells.add((OrbitSpell) spell);
				continue;
			}

			if (s.startsWith("silence:")) {
				if (s.replace("silence:", "").equalsIgnoreCase("*")) {
					for (Spell spell : MagicSpells.getSpellsOrdered()) {
						if (spell instanceof SilenceSpell) silenceSpells.add((SilenceSpell) spell);
					}
					continue;
				}
				Spell spell = MagicSpells.getSpellByInternalName(s.replace("silence:", ""));
				if (spell instanceof SilenceSpell) silenceSpells.add((SilenceSpell) spell);
				continue;
			}

			if (s.startsWith("levitate:")) {
				if (s.replace("levitate:", "").equalsIgnoreCase("*")) {
					for (Spell spell : MagicSpells.getSpellsOrdered()) {
						if (spell instanceof LevitateSpell) levitateSpells.add((LevitateSpell) spell);
					}
					continue;
				}
				Spell spell = MagicSpells.getSpellByInternalName(s.replace("levitate:", ""));
				if (spell instanceof LevitateSpell) levitateSpells.add((LevitateSpell) spell);
				continue;
			}

			if (s.startsWith("loop:")) {
				if (s.equals("loop:*")) {
					for (Spell spell : MagicSpells.getSpellsOrdered()) {
						if (spell instanceof LoopSpell loopSpell) loopSpells.add(loopSpell);
					}
					continue;
				}
				Spell spell = MagicSpells.getSpellByInternalName(s.replace("loop:", ""));
				if (spell instanceof LoopSpell loopSpell) loopSpells.add(loopSpell);
				continue;
			}

			PotionEffectType type = Util.getPotionEffectType(s);
			if (type != null) potionEffectTypes.add(type);
		}

		checker = entity -> {
			if (fire && entity.getFireTicks() > 0) return true;

			for (PotionEffectType type : potionEffectTypes) {
				if (entity.hasPotionEffect(type)) return true;
			}

			for (BuffSpell spell : buffSpells) {
				if (spell.isActive(entity)) return true;
			}

			for (DotSpell spell : dotSpells) {
				if (spell.isActive(entity)) return true;
			}

			for (StunSpell spell : stunSpells) {
				if (spell.isStunned(entity)) return true;
			}

			for (OrbitSpell spell : orbitSpells) {
				if (spell.hasOrbit(entity)) return true;
			}

			for (SilenceSpell spell : silenceSpells) {
				if (spell.isSilenced(entity)) return true;
			}

			for (LevitateSpell spell : levitateSpells) {
				if (spell.isBeingLevitated(entity)) return true;
			}

			for (LoopSpell spell : loopSpells) {
				if (spell.isActive(entity)) return true;
			}

			return false;
		};
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, checker, args);
			if (target.noTarget()) return noTarget(caster, args, target);

			cleanse(caster, target.target(), target.power(), args);
			sendMessages(caster, target.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		cleanse(caster, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		if (!validTargetList.canTarget(caster, target)) return false;
		cleanse(caster, target, power, null);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		cleanse(null, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		if (!validTargetList.canTarget(target)) return false;
		cleanse(null, target, power, null);
		return true;
	}

	@Override
	public ValidTargetChecker getValidTargetChecker() {
		return checker;
	}

	private void cleanse(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (fire) target.setFireTicks(0);

		for (PotionEffectType type : potionEffectTypes) {
			target.removePotionEffect(type);
		}

		buffSpells.forEach(spell -> spell.turnOff(target));
		dotSpells.forEach(spell -> spell.cancelDot(target));
		stunSpells.forEach(spell -> spell.removeStun(target));
		loopSpells.forEach(spell -> spell.cancelLoops(target));
		orbitSpells.forEach(spell -> spell.removeOrbits(target));
		silenceSpells.forEach(spell -> spell.removeSilence(target));
		levitateSpells.forEach(spell -> spell.removeLevitate(target));

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);
	}

}
