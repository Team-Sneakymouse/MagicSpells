package com.nisovin.magicspells.spells.targeted.ext;

import java.util.Set;
import java.util.UUID;
import java.util.Collection;

import com.google.common.collect.Multimap;
import com.google.common.collect.LinkedListMultimap;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import ru.xezard.glow.data.glow.Glow;
import ru.xezard.glow.data.glow.Glow.GlowBuilder;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.buff.InvisibilitySpell;

/*
 * NOTE: ProtocolLib and XGlow are required for this spell class.
 * XGlow: https://github.com/Xezard/XGlow (appears abandoned).
 * GlowBuilder still takes Bukkit ChatColor — no viable migration without replacing XGlow.
 */
@SuppressWarnings("deprecation") // XGlow GlowBuilder#color(ChatColor)
public class GlowSpell extends TargetedSpell implements TargetedEntitySpell {

	private final Multimap<UUID, GlowData> glowing;

	private GlowBuilder glowBuilder;

	private ChatColor color;

	private final ConfigData<Integer> duration;

	private final ConfigData<Boolean> powerAffectsDuration;

	public GlowSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		glowing = LinkedListMultimap.create();

		duration = getConfigDataInt("duration", 0);

		powerAffectsDuration = getConfigDataBoolean("power-affects-duration", true);

		String colorName = getConfigString("color", "");
		try {
			color = ChatColor.valueOf(colorName.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			color = ChatColor.WHITE;
			MagicSpells.log("GlowSpell '" + internalName + "' has an invalid color defined: '" + colorName + "'.");
		}
	}

	@Override
	public void initialize() {
		super.initialize();

		glowBuilder = Glow.builder().color(color).name(MagicSpells.getInstance().getName());
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player caster))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		TargetInfo<LivingEntity> info = getTargetedEntity(caster, data.power(), data.args());
		if (info.noTarget())
			return new CastResult(noTarget(caster, data.args(), info), data);

		data = data.target(info.target()).power(info.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return result;

		sendMessages(data.caster(), data.target(), data.args());
		return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(Entity livingEntity, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(livingEntity, power, args)).action();
	}

	@Override
	public CastResult castAtEntity(SpellData data) {
		if (!data.hasCaster() || !validTargetList.canTarget(data.caster(), data.target())
				|| !(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		glow(player, data.target(), data.power(), data.args());
		playSpellEffects(data.caster(), data.target(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	@Override
	public boolean castAtEntity(Entity target, float power, String[] args) {
		return false;
	}

	@Override
	public void turnOff() {
		glowing.values().forEach(glowData -> glowData.getGlow().destroy());
	}

	private void glow(Player caster, Entity target, float power, String[] args) {
		int duration = this.duration.get(caster, target, power, args);
		if (Boolean.TRUE.equals(powerAffectsDuration.get(caster, target, power, args))) duration = Math.round(duration * power);

		Collection<GlowData> glows = glowing.get(caster.getUniqueId());
		for (GlowData glowData : glows) {
			// That entity is glowing for the caster
			if (!glowData.getGlow().hasHolder(target)) continue;

			// If casted by the same spell, extend duration, otherwise fail
			if (!glowData.getInternalName().equals(internalName)) return;

			MagicSpells.cancelTask(glowData.getTaskId());
			glowData.setTaskId(MagicSpells.scheduleDelayedTask(() -> {
				// Make the target hidden if it has an invisibility spell active
				if (target instanceof Player targetPlayer) {
					Set<BuffSpell> buffSpells = MagicSpells.getBuffManager().getActiveBuffs(targetPlayer);
					if (buffSpells != null) {
						for (BuffSpell buffSpell : buffSpells) {
							if (!(buffSpell instanceof InvisibilitySpell)) continue;
							if (!caster.canSee(targetPlayer)) continue;

							caster.hidePlayer(MagicSpells.getInstance(), targetPlayer);
						}
					}
				}

				glowData.getGlow().destroy();
				glowing.remove(caster.getUniqueId(), glowData);
			}, duration));

			return;
		}

		GlowData glowData;

		Glow glow = glowBuilder.name(target.getUniqueId().toString() + caster.getUniqueId().toString() + internalName).build();
		glow.addHolders(target);
		glow.display(caster);

		glowData = new GlowData(glow, internalName);
		glowData.setTaskId(MagicSpells.scheduleDelayedTask(() -> {
			// Make the target hidden if it has an invisibility spell active
			if (target instanceof Player targetPlayer) {
				Set<BuffSpell> buffSpells = MagicSpells.getBuffManager().getActiveBuffs(targetPlayer);
				if (buffSpells != null) {
					for (BuffSpell buffSpell : buffSpells) {
						if (!(buffSpell instanceof InvisibilitySpell)) continue;
						if (!caster.canSee(targetPlayer)) continue;

						caster.hidePlayer(MagicSpells.getInstance(), targetPlayer);
					}
				}
			}

			glow.destroy();
			glowing.remove(caster.getUniqueId(), glowData);
		}, duration));

		// If target is a vanished player, make the caster see the target with vanish
		if (target instanceof Player targetPlayer) {
			Set<BuffSpell> buffSpells = MagicSpells.getBuffManager().getActiveBuffs(targetPlayer);
			if (buffSpells != null) {
				for (BuffSpell buffSpell : buffSpells) {
					if (!(buffSpell instanceof InvisibilitySpell)) continue;
					if (caster.canSee(targetPlayer)) continue;

					caster.showPlayer(MagicSpells.getInstance(), targetPlayer);
				}
			}
		}

		glowing.put(caster.getUniqueId(), glowData);
	}

	private static class GlowData {

		private Glow glow;
		private int taskId;
		private String internalName;

		private GlowData(Glow glow, String internalName) {
			this.glow = glow;
			this.internalName = internalName;
		}

		public Glow getGlow() {
			return glow;
		}

		public void setGlow(Glow glow) {
			this.glow = glow;
		}

		public int getTaskId() {
			return taskId;
		}

		public void setTaskId(int taskId) {
			this.taskId = taskId;
		}

		public String getInternalName() {
			return internalName;
		}

		public void setInternalName(String internalName) {
			this.internalName = internalName;
		}

	}

}
