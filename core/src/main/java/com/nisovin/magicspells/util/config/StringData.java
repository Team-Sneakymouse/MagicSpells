package com.nisovin.magicspells.util.config;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Location;

import me.clip.placeholderapi.PlaceholderAPI;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TxtUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.magicitems.MagicItemTags;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable;
import com.nisovin.magicspells.variables.variabletypes.PlayerStringVariable;

public class StringData implements ConfigData<String> {

	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("""
			%(?:\
			((var|castervar|targetvar|locationvar|casterlocationvar|targetlocationvar):(\\w+)(?::(\\d+))?)|\
			(defaultvar:(\\w+))|\
			(playervar:([a-zA-Z0-9_]{3,16}):(\\w+)(?::(\\d+))?)|\
			(arg:(\\d+):([\\w-]+))|\
			((papi|casterpapi|targetpapi):([^%]+))|\
			(playerpapi:([a-zA-Z0-9_]{3,16}):([^%]+))\
			)%|\
			%(cast_item|[at])""", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	private final List<ConfigData<String>> values;
	private final List<String> fragments;

	public StringData(String value) {
		List<ConfigData<String>> values = new ArrayList<>();
		List<String> fragments = new ArrayList<>();

		Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
		int end = 0;
		while (matcher.find()) {
			ConfigData<String> data = createData(matcher);
			if (data == null)
				continue;

			fragments.add(value.substring(end, matcher.start()));
			values.add(data);

			end = matcher.end();
		}

		fragments.add(value.substring(end));

		this.fragments = Collections.unmodifiableList(fragments);
		this.values = Collections.unmodifiableList(values);
	}

	private static ConfigData<String> createData(Matcher matcher) {
		if (matcher.group(1) != null) {
			String owner = matcher.group(2);
			String variable = matcher.group(3);
			String placesString = matcher.group(4);

			int places = -1;
			if (placesString != null) {
				try {
					places = Integer.parseInt(placesString);
				} catch (NumberFormatException e) {
					return null;
				}
			}

			if (owner.equalsIgnoreCase("targetvar"))
				return new TargetVariableData(matcher.group(), variable, places);
			if (owner.equalsIgnoreCase("locationvar"))
				return new LocationVariableData(matcher.group(), variable, places);
			if (owner.equalsIgnoreCase("casterlocationvar"))
				return new CasterLocationVariableData(matcher.group(), variable, places);
			if (owner.equalsIgnoreCase("targetlocationvar"))
				return new TargetLocationVariableData(matcher.group(), variable, places);
			return new CasterVariableData(matcher.group(), variable, places);
		}

		if (matcher.group(5) != null) {
			String variable = matcher.group(6);
			return new DefaultVariableData(matcher.group(), variable);
		}

		if (matcher.group(7) != null) {
			String player = matcher.group(8);
			String variable = matcher.group(9);
			String placesString = matcher.group(10);

			int places = -1;
			if (placesString != null) {
				try {
					places = Integer.parseInt(placesString);
				} catch (NumberFormatException e) {
					return null;
				}
			}

			return new PlayerVariableData(matcher.group(), variable, player, places);
		}

		if (matcher.group(11) != null) {
			String def = matcher.group(13);

			int index;
			try {
				index = Integer.parseInt(matcher.group(12));
			} catch (NumberFormatException e) {
				return null;
			}
			if (index == 0)
				return null;

			return new ArgumentData(index - 1, def);
		}

		if (matcher.group(14) != null) {
			String owner = matcher.group(15);
			String papiPlaceholder = '%' + matcher.group(16) + '%';

			return owner.equalsIgnoreCase("targetpapi") ? new TargetPAPIData(matcher.group(), papiPlaceholder)
					: new CasterPAPIData(matcher.group(), papiPlaceholder);
		}

		if (matcher.group(17) != null) {
			String player = matcher.group(18);
			String papiPlaceholder = '%' + matcher.group(19) + '%';

			return new PlayerPAPIData(matcher.group(), papiPlaceholder, player);
		}

		if (matcher.group(20) != null) {
			String token = matcher.group(20);
			if ("cast_item".equalsIgnoreCase(token)) {
				return new CastItemNameData(matcher.group());
			}
			if ("a".equalsIgnoreCase(token)) {
				return new CasterNameData(matcher.group());
			}
			if ("t".equalsIgnoreCase(token)) {
				return new TargetNameData(matcher.group());
			}
		}
		return null;
	}

	@Override
	public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
		if (values.isEmpty())
			return fragments.get(0);

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < fragments.size() - 1; i++) {
			builder.append(fragments.get(i));
			builder.append(values.get(i).get(caster, target, location, power, args));
		}
		builder.append(fragments.get(fragments.size() - 1));

		return builder.toString();
	}

	@Override
	public String get(SpellData data) {
		if (values.isEmpty())
			return fragments.get(0);

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < fragments.size() - 1; i++) {
			builder.append(fragments.get(i));
			builder.append(values.get(i).get(data));
		}
		builder.append(fragments.get(fragments.size() - 1));

		return builder.toString();
	}

	@Override
	public boolean isConstant() {
		return values.isEmpty();
	}

	public List<ConfigData<String>> getValues() {
		return values;
	}

	public List<String> getFragments() {
		return fragments;
	}

	public static abstract class PlaceholderData implements ConfigData<String> {

		protected final String placeholder;

		public PlaceholderData(String placeholder) {
			this.placeholder = placeholder;
		}

		@Override
		public boolean isConstant() {
			return false;
		}

	}

	public static class ArgumentData implements ConfigData<String> {

		private final String def;
		private final int index;

		public ArgumentData(int index, String def) {
			this.index = index;
			this.def = def;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (args != null && args.length > index)
				return args[index];
			else
				return def;
		}

		@Override
		public boolean isConstant() {
			return false;
		}

	}

	public static class CasterVariableData extends PlaceholderData {

		private final String variable;
		private final int places;

		public CasterVariableData(String placeholder, String variable, int places) {
			super(placeholder);

			this.variable = variable;
			this.places = places;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (!(caster instanceof Player player))
				return placeholder;

			Variable var = MagicSpells.getVariableManager().getVariable(variable);
			if (var == null)
				return placeholder;

			if (places >= 0) {
				if (var instanceof PlayerStringVariable || var instanceof GlobalStringVariable)
					return TxtUtil.getStringNumber(var.getStringValue(player), places);

				return TxtUtil.getStringNumber(var.getValue(player), places);
			}

			return var.getStringValue(player);
		}

	}

	public static class TargetVariableData extends PlaceholderData {

		private final String variable;
		private final int places;

		public TargetVariableData(String placeholder, String variable, int places) {
			super(placeholder);

			this.variable = variable;
			this.places = places;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (!(target instanceof Player player))
				return placeholder;

			Variable var = MagicSpells.getVariableManager().getVariable(variable);
			if (var == null)
				return placeholder;

			if (places >= 0) {
				if (var instanceof PlayerStringVariable || var instanceof GlobalStringVariable)
					return TxtUtil.getStringNumber(var.getStringValue(player), places);

				return TxtUtil.getStringNumber(var.getValue(player), places);
			}

			return var.getStringValue(player);
		}

	}

	public static class LocationVariableData extends PlaceholderData {

		private final String variable;
		private final int places;

		public LocationVariableData(String placeholder, String variable, int places) {
			super(placeholder);
			this.variable = variable;
			this.places = places;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (location == null && target == null && caster == null)
				return placeholder;
			Variable var = MagicSpells.getVariableManager().getVariable(variable);
			if (var == null)
				return placeholder;

			Location loc = location == null ? target != null ? target.getLocation() : caster.getLocation() : location;

			if (loc == null)
				return placeholder;

			if (places >= 0) {
				return TxtUtil.getStringNumber(MagicSpells.getVariableManager().getValueAtLocation(variable, loc),
						places);
			}

			return MagicSpells.getVariableManager().getStringValueAtLocation(variable, loc);
		}

	}

	public static class CasterLocationVariableData extends PlaceholderData {

		private final String variable;
		private final int places;

		public CasterLocationVariableData(String placeholder, String variable, int places) {
			super(placeholder);
			this.variable = variable;
			this.places = places;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (caster == null)
				return placeholder;
			Variable var = MagicSpells.getVariableManager().getVariable(variable);
			if (var == null)
				return placeholder;

			Location loc = caster.getLocation();
			if (places >= 0) {
				return TxtUtil.getStringNumber(MagicSpells.getVariableManager().getValueAtLocation(variable, loc),
						places);
			}

			return MagicSpells.getVariableManager().getStringValueAtLocation(variable, loc);
		}

	}

	public static class TargetLocationVariableData extends PlaceholderData {

		private final String variable;
		private final int places;

		public TargetLocationVariableData(String placeholder, String variable, int places) {
			super(placeholder);
			this.variable = variable;
			this.places = places;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (target == null && location == null)
				return placeholder;
			Variable var = MagicSpells.getVariableManager().getVariable(variable);
			if (var == null)
				return placeholder;

			Location loc = location == null ? target.getLocation() : location;
			if (places >= 0) {
				return TxtUtil.getStringNumber(MagicSpells.getVariableManager().getValueAtLocation(variable, loc),
						places);
			}

			return MagicSpells.getVariableManager().getStringValueAtLocation(variable, loc);
		}

	}

	public static class DefaultVariableData extends PlaceholderData {

		private final String variable;

		public DefaultVariableData(String placeholder, String variable) {
			super(placeholder);
			this.variable = variable;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			Variable var = MagicSpells.getVariableManager().getVariable(variable);
			if (var == null)
				return placeholder;

			return var.getDefaultStringValue();
		}
	}

	public static class PlayerVariableData extends PlaceholderData {

		private final String variable;
		private final String player;
		private final int places;

		public PlayerVariableData(String placeholder, String variable, String player, int places) {
			super(placeholder);

			this.variable = variable;
			this.player = player;
			this.places = places;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			Variable var = MagicSpells.getVariableManager().getVariable(variable);
			if (var == null)
				return placeholder;

			if (places >= 0) {
				if (var instanceof PlayerStringVariable || var instanceof GlobalStringVariable)
					return TxtUtil.getStringNumber(var.getStringValue(player), places);

				return TxtUtil.getStringNumber(var.getValue(player), places);
			}

			return var.getStringValue(player);
		}

	}

	public static class CasterPAPIData extends PlaceholderData {

		private final String papiPlaceholder;

		public CasterPAPIData(String placeholder, String papiPlaceholder) {
			super(placeholder);

			this.papiPlaceholder = papiPlaceholder;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") || !(caster instanceof Player player))
				return placeholder;

			return PlaceholderAPI.setPlaceholders(player, papiPlaceholder);
		}

	}

	public static class TargetPAPIData extends PlaceholderData {

		private final String papiPlaceholder;

		public TargetPAPIData(String placeholder, String papiPlaceholder) {
			super(placeholder);

			this.papiPlaceholder = papiPlaceholder;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") || !(target instanceof Player player))
				return placeholder;

			return PlaceholderAPI.setPlaceholders(player, papiPlaceholder);
		}

	}

	public static class PlayerPAPIData extends PlaceholderData {

		private final String papiPlaceholder;
		private final String player;

		public PlayerPAPIData(String placeholder, String papiPlaceholder, String player) {
			super(placeholder);

			this.papiPlaceholder = papiPlaceholder;
			this.player = player;
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
				return placeholder;
			return PlaceholderAPI.setPlaceholders(Bukkit.getOfflinePlayer(player), papiPlaceholder);
		}

	}

	public static class CasterNameData extends PlaceholderData {

		public CasterNameData(String placeholder) {
			super(placeholder);
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			return caster != null ? caster.getName() : placeholder;
		}
	}

	public static class TargetNameData extends PlaceholderData {

		public TargetNameData(String placeholder) {
			super(placeholder);
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			return target != null ? target.getName() : placeholder;
		}
	}

	public static class CastItemNameData extends PlaceholderData {

		public CastItemNameData(String placeholder) {
			super(placeholder);
		}

		@Override
		public String get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
			return placeholder;
		}

		@Override
		public String get(SpellData data) {
			if (data == null || !data.hasCastItem()) return placeholder;
			String name = MagicItemTags.magicItemName(data.castItem());
			return name != null && !name.isEmpty() ? name : placeholder;
		}

	}

}
