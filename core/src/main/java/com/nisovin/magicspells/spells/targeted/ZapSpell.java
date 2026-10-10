package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.EnumSet;

import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsBlockBreakEvent;

public class ZapSpell extends TargetedSpell implements TargetedLocationSpell {

	private Set<Material> allowedBlockTypes;
	private Set<Material> disallowedBlockTypes;

	private String strCantZap;

	private boolean dropBlock;
	private boolean dropNormal;
	private boolean checkPlugins;
	private boolean playBreakEffect;

	public ZapSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		// Always non-null: empty allowed list means "any block not disallowed".
		allowedBlockTypes = EnumSet.noneOf(Material.class);
		List<String> allowed = getConfigStringList("allowed-block-types", null);
		if (allowed != null) {
			for (String s : allowed) {
				Material m = Util.getMaterial(s);
				if (m == null)
					continue;

				allowedBlockTypes.add(m);
			}
		}

		disallowedBlockTypes = EnumSet.noneOf(Material.class);
		List<String> disallowed = getConfigStringList("disallowed-block-types",
				Arrays.asList("bedrock", "lava", "water"));
		if (disallowed != null) {
			for (String s : disallowed) {
				Material m = Util.getMaterial(s);
				if (m == null)
					continue;

				disallowedBlockTypes.add(m);
			}
		}

		strCantZap = getConfigString("str-cant-zap", "");

		dropBlock = getConfigBoolean("drop-block", false);
		dropNormal = getConfigBoolean("drop-normal", true);
		checkPlugins = getConfigBoolean("check-plugins", true);
		playBreakEffect = getConfigBoolean("play-break-effect", true);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		Block target;
		try {
			target = getTargetedBlock(data.caster(), data.power(), data.args());
		} catch (IllegalStateException e) {
			target = null;
		}
		if (target != null) {
			SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(target.getLocation()));
			EventUtil.call(event);
			if (event.isCancelled())
				target = null;
			else {
				data = event.getSpellData();
				target = data.location().getBlock();
			}
		}
		if (target == null)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		if (!canZap(target))
			return new CastResult(noTarget(data.caster(), strCantZap, data.args()), data);

		CastResult result = castAtLocation(data.location(target.getLocation()));
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return new CastResult(noTarget(data.caster(), strCantZap, data.args()), data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		if (data.hasCaster() && !(data.caster() instanceof Player))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		Player player = data.caster() instanceof Player p ? p : null;
		Block block = data.location().getBlock();
		if (canZap(block)) {
			if (zap(block, player, data))
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		Vector v = data.location().getDirection();
		block = data.location().clone().add(v).getBlock();

		if (canZap(block) && zap(block, player, data))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		return new CastResult(PostCastAction.ALREADY_HANDLED, data);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return castAtLocation(new SpellData(null, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	private boolean zap(Block target, Player player, SpellData data) {
		boolean playerNull = player == null;

		if (checkPlugins && !playerNull) {
			MagicSpellsBlockBreakEvent event = new MagicSpellsBlockBreakEvent(target, player, bypassDippGen);
			MagicSpells.plugin.getServer().getPluginManager().callEvent(event);
			if (event.isCancelled())
				return false;
		}

		if (dropBlock) {
			if (dropNormal)
				target.breakNaturally();
			else
				target.getWorld().dropItemNaturally(target.getLocation(), new ItemStack(target.getType()));
		}

		if (playBreakEffect)
			target.getWorld().playEffect(target.getLocation(), Effect.DESTROY_BLOCK, target.getBlockData());

		SpellData effectData = data.location(target.getLocation());
		if (!playerNull)
			playSpellEffects(EffectPosition.CASTER, player, effectData);
		playSpellEffects(EffectPosition.TARGET, target.getLocation(), effectData);
		if (!playerNull)
			playSpellEffectsTrail(player.getLocation(), target.getLocation(), effectData);

		target.setType(Material.AIR);
		return true;
	}

	private boolean canZap(Block target) {
		Material type = target.getType();
		if (disallowedBlockTypes.contains(type))
			return false;
		if (allowedBlockTypes.isEmpty())
			return true;
		return allowedBlockTypes.contains(type);
	}

}
