package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.FireworkEffect;
import org.bukkit.entity.LivingEntity;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.FireworkMeta;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.RegexUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

// REMOVE LATER - REPLACED BY CONJURESPELL
public class ConjureFireworkSpell extends InstantSpell implements TargetedLocationSpell {

	private static final Pattern COLORS_PATTERN = Pattern.compile("^[A-Fa-f0-9]{6}(,[A-Fa-f0-9]{6})*$");

	private ConfigData<Integer> count;
	private ConfigData<Integer> flight;
	private ConfigData<Integer> pickupDelay;

	private boolean gravity;
	private boolean addToInventory;

	private String fireworkName;

	private ItemStack firework;

	public ConjureFireworkSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		count = getConfigDataInt("count", 1);
		flight = getConfigDataInt("flight", 2);
		pickupDelay = getConfigDataInt("pickup-delay", 0);

		gravity = getConfigBoolean("gravity", true);
		addToInventory = getConfigBoolean("add-to-inventory", true);

		fireworkName = getConfigString("firework-name", "");

		firework = new ItemStack(Material.FIREWORK_ROCKET);
		FireworkMeta meta = (FireworkMeta) firework.getItemMeta();

		if (!fireworkName.isEmpty())
			meta.displayName(Util.getMiniMessage(fireworkName));

		List<String> fireworkEffects = getConfigStringList("firework-effects", null);
		if (fireworkEffects != null && !fireworkEffects.isEmpty()) {
			for (String e : fireworkEffects) {
				FireworkEffect.Type type = Type.BALL;
				boolean trail = false;
				boolean twinkle = false;
				int[] colors = null;
				int[] fadeColors = null;

				String[] data = e.split(" ");
				for (String s : data) {
					if (s.equalsIgnoreCase("ball") || s.equalsIgnoreCase("smallball")) {
						type = Type.BALL;
					} else if (s.equalsIgnoreCase("largeball")) {
						type = Type.BALL_LARGE;
					} else if (s.equalsIgnoreCase("star")) {
						type = Type.STAR;
					} else if (s.equalsIgnoreCase("burst")) {
						type = Type.BURST;
					} else if (s.equalsIgnoreCase("creeper")) {
						type = Type.CREEPER;
					} else if (s.equalsIgnoreCase("trail")) {
						trail = true;
					} else if (s.equalsIgnoreCase("twinkle") || s.equalsIgnoreCase("flicker")) {
						twinkle = true;
					} else if (RegexUtil.matches(COLORS_PATTERN, s)) {
						String[] scolors = s.split(",");
						int[] icolors = new int[scolors.length];
						for (int i = 0; i < scolors.length; i++) {
							icolors[i] = Integer.parseInt(scolors[i], 16);
						}

						if (colors == null)
							colors = icolors;
						else if (fadeColors == null)
							fadeColors = icolors;
					}
				}

				FireworkEffect.Builder builder = FireworkEffect.builder();
				builder.with(type);
				builder.trail(trail);
				builder.flicker(twinkle);
				if (colors != null) {
					for (int color : colors) {
						builder.withColor(Color.fromRGB(color));
					}
				}
				if (fadeColors != null) {
					for (int fadeColor : fadeColors) {
						builder.withColor(Color.fromRGB(fadeColor));
					}
				}
				meta.addEffect(builder.build());
			}
		}

		firework.setItemMeta(meta);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		boolean added = false;
		ItemStack item = firework.clone();
		item.setAmount(count.get(player, null, data.power(), data.args()));

		ItemMeta meta = item.getItemMeta();
		if (meta instanceof FireworkMeta fMeta)
			fMeta.setPower(flight.get(player, null, data.power(), data.args()));

		if (addToInventory)
			added = Util.addToInventory(player, player.getInventory(), item, true, false);
		SpellData effectData = new SpellData(player, data.power(), data.args());
		if (!added) {
			Item dropped = player.getWorld().dropItem(player.getLocation(), item);
			dropped.setItemStack(item);
			dropped.setGravity(gravity);

			int delay = Math.max(pickupDelay.get(player, null, effectData.power(), effectData.args()), 0);
			dropped.setPickupDelay(delay);

			playSpellEffects(EffectPosition.SPECIAL, dropped, effectData);
		}
		playSpellEffects(EffectPosition.CASTER, player, effectData);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

		@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	@Override
	public CastResult castAtLocation(SpellData data) {
		SpellData effectData = new SpellData(data.caster(), data.location(), data.power(), data.args());
		playSpellEffects(EffectPosition.CASTER, data.caster(), effectData);

		ItemStack item = firework.clone();
		item.setAmount(count.get(data.caster(), null, data.power(), data.args()));

		ItemMeta meta = item.getItemMeta();
		if (meta instanceof FireworkMeta fMeta)
			fMeta.setPower(flight.get(data.caster(), null, data.power(), data.args()));

		Item dropped = data.location().getWorld().dropItem(data.location(), item);
		dropped.setItemStack(item);
		dropped.setGravity(gravity);

		int delay = Math.max(pickupDelay.get(data.caster(), null, data.power(), data.args()), 0);
		dropped.setPickupDelay(delay);

		playSpellEffects(EffectPosition.SPECIAL, dropped, effectData);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



}
