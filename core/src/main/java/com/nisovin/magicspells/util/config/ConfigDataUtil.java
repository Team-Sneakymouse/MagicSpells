package com.nisovin.magicspells.util.config;

import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.handlers.EnchantmentHandler;
import com.nisovin.magicspells.util.AttributeSpecParser;
import com.nisovin.magicspells.util.AttributeUtil;
import com.nisovin.magicspells.util.managers.AttributeManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.util.Vector;
import org.bukkit.util.EulerAngle;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.bukkit.entity.LivingEntity;
import org.bukkit.block.data.BlockData;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Particle.DustTransition;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import net.kyori.adventure.text.Component;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.ColorUtil;
import com.nisovin.magicspells.util.ParticleUtil;

public class ConfigDataUtil {

	/**
	 * Parse an attribute modifier string into cast-time {@link ConfigData}.
	 * <p>
	 * Format: {@code <attribute> <value> [operation] [slot]}
	 * <ul>
	 * <li>{@code operation} defaults to {@code add_number}</li>
	 * <li>{@code slot} is optional ({@link org.bukkit.inventory.EquipmentSlot}); omit to apply in all slots</li>
	 * </ul>
	 * All tokens support string/numeric expressions via ConfigData.
	 */
	@NotNull
	public static ConfigData<AttributeManager.AttributeInfo> getAttributeInfo(@Nullable String value,
			@Nullable String sourceKey, int index) {
		if (value == null || value.isBlank())
			return (caster, target, location, power, args) -> null;

		String[] parts = value.trim().split("\\s+");
		if (parts.length < 2)
			return (caster, target, location, power, args) -> null;

		ConfigData<String> attributeName = getString(parts[0]);
		ConfigData<Double> amount = getDouble(parts[1]);
		ConfigData<String> operation = parts.length >= 3 ? getString(parts[2])
				: (caster, target, location, power, args) -> "add_number";
		ConfigData<String> slot = parts.length >= 4 ? getString(parts[3])
				: (caster, target, location, power, args) -> null;
		NamespacedKey modifierKey = stableModifierKey(sourceKey, index);

		if (attributeName.isConstant() && amount.isConstant() && operation.isConstant() && slot.isConstant()) {
			AttributeManager.AttributeInfo info = buildAttributeInfo(value, modifierKey, attributeName.get(null),
					amount.get(null), operation.get(null), slot.get(null));
			return (caster, target, location, power, args) -> info;
		}

		return new ConfigData<>() {
			@Override
			public AttributeManager.AttributeInfo get(LivingEntity caster, LivingEntity target, Location location,
					float power,
					String[] args) {
				String name = attributeName.get(caster, target, location, power, args);
				if (name == null)
					return null;

				Double amt = amount.get(caster, target, location, power, args);
				if (amt == null)
					return null;

				String op = operation.get(caster, target, location, power, args);
				if (op == null)
					return null;

				return buildAttributeInfo(value, modifierKey, name, amt, op,
						slot.get(caster, target, location, power, args));
			}

			@Override
			public boolean isConstant() {
				return false;
			}
		};
	}

	@NotNull
	public static ConfigData<AttributeManager.AttributeInfo> getAttributeInfo(@Nullable String value) {
		return getAttributeInfo(value, null, 0);
	}

	/**
	 * Parse an enchantment string into cast-time {@link ConfigData}.
	 * <p>
	 * Format: {@code <enchantment> [level]} — level defaults to {@code 1}. Both tokens support expressions.
	 */
	@NotNull
	public static ConfigData<Map.Entry<Enchantment, Integer>> getEnchantmentInfo(@Nullable String value) {
		if (value == null || value.isBlank())
			return (caster, target, location, power, args) -> null;

		String[] parts = value.trim().split("\\s+");
		if (parts.length < 1 || parts[0] == null || parts[0].isBlank())
			return (caster, target, location, power, args) -> null;

		ConfigData<String> enchantName = getString(parts[0]);
		ConfigData<Integer> level = parts.length > 1 ? getInteger(parts[1])
				: (caster, target, location, power, args) -> 1;

		if (enchantName.isConstant() && level.isConstant()) {
			Map.Entry<Enchantment, Integer> entry = buildEnchantmentEntry(value, enchantName.get(null), level.get(null));
			return (caster, target, location, power, args) -> entry;
		}

		return new ConfigData<>() {
			@Override
			public Map.Entry<Enchantment, Integer> get(LivingEntity caster, LivingEntity target, Location location,
					float power, String[] args) {
				return buildEnchantmentEntry(value, enchantName.get(caster, target, location, power, args),
						level.get(caster, target, location, power, args));
			}

			@Override
			public boolean isConstant() {
				return false;
			}
		};
	}

	/**
	 * Build a cast-time evaluated enchantment map from a string list
	 * ({@code <enchantment> [level]} per entry).
	 */
	@NotNull
	public static ConfigData<Map<Enchantment, Integer>> getEnchantmentsConfigData(@Nullable List<String> enchantments) {
		if (enchantments == null || enchantments.isEmpty())
			return (caster, target, location, power, args) -> null;

		final List<ConfigData<Map.Entry<Enchantment, Integer>>> suppliers = new ArrayList<>();
		boolean isConstant = true;

		for (String raw : enchantments) {
			if (raw == null || raw.isBlank())
				continue;

			ConfigData<Map.Entry<Enchantment, Integer>> supplier = getEnchantmentInfo(raw);
			suppliers.add(supplier);
			if (isConstant && !supplier.isConstant())
				isConstant = false;
		}

		if (suppliers.isEmpty())
			return (caster, target, location, power, args) -> null;

		if (isConstant) {
			Map<Enchantment, Integer> resolved = new HashMap<>();
			for (ConfigData<Map.Entry<Enchantment, Integer>> supplier : suppliers) {
				Map.Entry<Enchantment, Integer> entry = supplier.get(null, null, 1f, null);
				if (entry != null)
					resolved.put(entry.getKey(), entry.getValue());
			}
			if (resolved.isEmpty())
				return (caster, target, location, power, args) -> null;
			return (caster, target, location, power, args) -> resolved;
		}

		return new ConfigData<>() {
			@Override
			public Map<Enchantment, Integer> get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				Map<Enchantment, Integer> resolved = new HashMap<>();
				for (ConfigData<Map.Entry<Enchantment, Integer>> supplier : suppliers) {
					Map.Entry<Enchantment, Integer> entry = supplier.get(caster, target, location, power, args);
					if (entry != null)
						resolved.put(entry.getKey(), entry.getValue());
				}
				return resolved.isEmpty() ? null : resolved;
			}

			@Override
			public boolean isConstant() {
				return false;
			}
		};
	}

	@Nullable
	private static Map.Entry<Enchantment, Integer> buildEnchantmentEntry(String debugValue, String enchantName,
			Integer level) {
		if (enchantName == null || level == null)
			return null;

		Enchantment enchant = EnchantmentHandler.getEnchantment(enchantName);
		if (enchant == null) {
			MagicSpells.error("Invalid enchantment defined: " + enchantName + " (" + debugValue + ")");
			return null;
		}

		return Map.entry(enchant, level);
	}

	@Nullable
	private static AttributeManager.AttributeInfo buildAttributeInfo(String debugValue, NamespacedKey modifierKey,
			String attributeName, Double number, String attributeOperation, String slotName) {
		if (attributeName == null || number == null || attributeOperation == null)
			return null;

		Attribute attribute = AttributeUtil.getAttribute(attributeName);
		if (attribute == null) {
			MagicSpells.error(
					"AttributeManager has an invalid attribute defined: " + attributeName + " (" + debugValue + ")");
			return null;
		}

		AttributeModifier.Operation op = AttributeUtil.getOperation(attributeOperation);
		if (op == null) {
			MagicSpells.error("AttributeManager has an invalid attribute operation defined: " + attributeOperation
					+ " (" + debugValue + ")");
			return null;
		}

		EquipmentSlotGroup slotGroup = EquipmentSlotGroup.ANY;
		if (slotName != null && !slotName.isBlank()) {
			try {
				EquipmentSlot slot = EquipmentSlot.valueOf(slotName.toUpperCase());
				slotGroup = slot.getGroup();
			} catch (IllegalArgumentException e) {
				MagicSpells.error("AttributeManager has an invalid attribute slot defined: " + slotName + " ("
						+ debugValue + ")");
			}
		}

		return new AttributeManager.AttributeInfo(attribute,
			new AttributeModifier(modifierKey, number, op, slotGroup));
	}

	@NotNull
	private static NamespacedKey stableModifierKey(@Nullable String sourceKey, int index) {
		return new NamespacedKey("magicspells", AttributeSpecParser.stableKeyPath(sourceKey, index));
	}

	@NotNull
	public static ConfigData<Integer> getInteger(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isInt(path)) {
			int value = config.getInt(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Integer> data = FunctionData.build(config.getString(path), Double::intValue);
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}

		return (caster, target, location, power, args) -> null;
	}

	@NotNull
	public static ConfigData<Integer> getInteger(@NotNull ConfigurationSection config, @NotNull String path, int def) {
		if (config.isInt(path)) {
			int value = config.getInt(path, def);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Integer> data = FunctionData.build(config.getString(path), Double::intValue, def);
			if (data == null)
				return (caster, target, location, power, args) -> def;

			return data;
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Integer> getInteger(@NotNull ConfigurationSection config, @NotNull String path,
			ConfigData<Integer> def) {
		if (config.isInt(path)) {
			int value = config.getInt(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Integer> data = FunctionData.build(config.getString(path), Double::intValue, def);
			if (data == null)
				return def;

			return data;
		}

		return def;
	}

	@NotNull
	public static ConfigData<Integer> getInteger(@NotNull String string) {
		try {
			int value = Integer.parseInt(string);
			return (caster, target, location, power, args) -> value;
		} catch (NumberFormatException e) {
			FunctionData<Integer> data = FunctionData.build(string, Double::intValue);
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}
	}

	@NotNull
	public static ConfigData<Long> getLong(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isInt(path) || config.isLong(path)) {
			long value = config.getLong(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Long> data = FunctionData.build(config.getString(path), Double::longValue);
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}

		return (caster, target, location, power, args) -> null;
	}

	@NotNull
	public static ConfigData<Long> getLong(@NotNull ConfigurationSection config, @NotNull String path, long def) {
		if (config.isInt(path) || config.isLong(path)) {
			long value = config.getLong(path, def);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Long> data = FunctionData.build(config.getString(path), Double::longValue, def);
			if (data == null)
				return (caster, target, location, power, args) -> def;

			return data;
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Long> getLong(@NotNull ConfigurationSection config, @NotNull String path,
			ConfigData<Long> def) {
		if (config.isInt(path) || config.isLong(path)) {
			long value = config.getLong(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Long> data = FunctionData.build(config.getString(path), Double::longValue, def);
			if (data == null)
				return def;

			return data;
		}

		return def;
	}

	@NotNull
	public static ConfigData<Short> getShort(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isInt(path)) {
			short value = (short) config.getInt(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Short> data = FunctionData.build(config.getString(path), Double::shortValue);
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}

		return (caster, target, location, power, args) -> null;
	}

	@NotNull
	public static ConfigData<Short> getShort(@NotNull ConfigurationSection config, @NotNull String path, short def) {
		if (config.isInt(path)) {
			short value = (short) config.getInt(path, def);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Short> data = FunctionData.build(config.getString(path), Double::shortValue, def);
			if (data == null)
				return (caster, target, location, power, args) -> def;

			return data;
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Short> getShort(@NotNull ConfigurationSection config, @NotNull String path,
			ConfigData<Short> def) {
		if (config.isInt(path)) {
			short value = (short) config.getInt(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Short> data = FunctionData.build(config.getString(path), Double::shortValue, def);
			if (data == null)
				return def;

			return data;
		}

		return def;
	}

	@NotNull
	public static ConfigData<Byte> getByte(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isInt(path)) {
			byte value = (byte) config.getInt(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Byte> data = FunctionData.build(config.getString(path), Double::byteValue);
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}

		return (caster, target, location, power, args) -> null;
	}

	@NotNull
	public static ConfigData<Byte> getByte(@NotNull ConfigurationSection config, @NotNull String path, byte def) {
		if (config.isInt(path)) {
			byte value = (byte) config.getInt(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Byte> data = FunctionData.build(config.getString(path), Double::byteValue, def);
			if (data == null)
				return (caster, target, location, power, args) -> def;

			return data;
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Byte> getByte(@NotNull ConfigurationSection config, @NotNull String path,
			ConfigData<Byte> def) {
		if (config.isInt(path)) {
			byte value = (byte) config.getInt(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Byte> data = FunctionData.build(config.getString(path), Double::byteValue, def);
			if (data == null)
				return def;

			return data;
		}

		return def;
	}

	@NotNull
	public static ConfigData<Double> getDouble(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isInt(path) || config.isLong(path) || config.isDouble(path)) {
			double value = config.getDouble(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Double> data = FunctionData.build(config.getString(path), Function.identity());
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}

		return (caster, target, location, power, args) -> null;
	}

	@NotNull
	public static ConfigData<Double> getDouble(@NotNull ConfigurationSection config, @NotNull String path, double def) {
		if (config.isInt(path) || config.isLong(path) || config.isDouble(path)) {
			double value = config.getDouble(path, def);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Double> data = FunctionData.build(config.getString(path), Function.identity(), def);
			if (data == null)
				return (caster, target, location, power, args) -> def;

			return data;
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Double> getDouble(@NotNull ConfigurationSection config, @NotNull String path,
			ConfigData<Double> def) {
		if (config.isInt(path) || config.isLong(path) || config.isDouble(path)) {
			double value = config.getDouble(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Double> data = FunctionData.build(config.getString(path), Function.identity(), def);
			if (data == null)
				return def;

			return data;
		}

		return def;
	}

	@NotNull
	public static ConfigData<Double> getDouble(@NotNull String string) {
		try {
			double value = Double.parseDouble(string);
			return (caster, target, location, power, args) -> value;
		} catch (NumberFormatException e) {
			FunctionData<Double> data = FunctionData.build(string, Function.identity());
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}
	}

	@NotNull
	public static ConfigData<Float> getFloat(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isInt(path) || config.isLong(path) || config.isDouble(path)) {
			float value = (float) config.getDouble(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Float> data = FunctionData.build(config.getString(path), Double::floatValue);
			if (data == null)
				return (caster, target, location, power, args) -> null;

			return data;
		}

		return (caster, target, location, power, args) -> null;
	}

	@NotNull
	public static ConfigData<Float> getFloat(@NotNull ConfigurationSection config, @NotNull String path, float def) {
		if (config.isInt(path) || config.isLong(path) || config.isDouble(path)) {
			float value = (float) config.getDouble(path, def);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Float> data = FunctionData.build(config.getString(path), Double::floatValue, def);
			if (data == null)
				return (caster, target, location, power, args) -> def;

			return data;
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Float> getFloat(@NotNull ConfigurationSection config, @NotNull String path,
			ConfigData<Float> def) {
		if (config.isInt(path) || config.isLong(path) || config.isDouble(path)) {
			float value = (float) config.getDouble(path);
			return (caster, target, location, power, args) -> value;
		}

		if (config.isString(path)) {
			FunctionData<Float> data = FunctionData.build(config.getString(path), Double::floatValue, def);
			if (data == null)
				return def;

			return data;
		}

		return def;
	}

	@NotNull
	public static ConfigData<String> getString(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable String def) {
		String value = config.getString(path, def);
		if (value == null)
			return (caster, target, location, power, args) -> null;

		return getString(value);
	}

	@NotNull
	public static ConfigData<String> getString(@Nullable String value) {
		if (value == null)
			return (caster, target, location, power, args) -> null;

		StringData data = new StringData(value);
		if (data.isConstant())
			return (caster, target, location, power, args) -> value;

		List<ConfigData<String>> values = data.getValues();
		List<String> fragments = data.getFragments();
		if (values.size() == 1 && fragments.size() == 2 && fragments.get(0).isEmpty() && fragments.get(1).isEmpty())
			return values.get(0);

		return data;
	}

	@NotNull
	public static ConfigData<Component> getComponent(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable Component def) {
		ConfigData<String> supplier = getString(config, path, null);
		if (supplier.isConstant()) {
			String value = supplier.get(null);
			if (value == null)
				return (caster, target, location, power, args) -> def;

			Component component = Util.getMiniMessage(value);
			return (caster, target, location, power, args) -> component;
		}

		return new ConfigData<>() {

			@Override
			public Component get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				String value = supplier.get(caster, target, location, power, args);
				if (value == null)
					return def;

				return Util.getMiniMessage(value);
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	public static ConfigData<Boolean> getBoolean(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isBoolean(path)) {
			boolean val = config.getBoolean(path);
			return (caster, target, location, power, args) -> val;
		}

		if (config.isString(path)) {
			ConfigData<String> supplier = getString(config, path, null);
			return (caster, target, location, power, args) -> Boolean
					.parseBoolean(supplier.get(caster, target, location, power, args));
		}

		return (caster, target, location, power, args) -> null;
	}

	public static ConfigData<Boolean> getBoolean(@NotNull ConfigurationSection config, @NotNull String path,
			boolean def) {
		if (config.isBoolean(path)) {
			boolean val = config.getBoolean(path);
			return (caster, target, location, power, args) -> val;
		}

		if (config.isString(path)) {
			ConfigData<String> supplier = getString(config, path, Boolean.toString(def));
			return (caster, target, location, power, args) -> Boolean
					.parseBoolean(supplier.get(caster, target, location, power, args));
		}

		return (caster, target, location, power, args) -> def;
	}

	public static ConfigData<Boolean> getBoolean(@NotNull ConfigurationSection config, @NotNull String path,
			ConfigData<Boolean> def) {
		if (config.isBoolean(path)) {
			boolean val = config.getBoolean(path);
			return (caster, target, location, power, args) -> val;
		}

		if (config.isString(path)) {
			ConfigData<String> supplier = getString(config, path, null);
			return (caster, target, location, power, args) -> {
				String value = supplier.get(caster, target, location, power, args);
				return value == null ? def.get(caster, target, location, power, args) : Boolean.parseBoolean(value);
			};
		}

		return def;
	}

	@NotNull
	public static <T extends Enum<T>> ConfigData<T> getEnum(@NotNull ConfigurationSection config,
			@NotNull String path,
			@NotNull Class<T> type,
			@Nullable T def) {
		String value = config.getString(path);
		if (value == null)
			return (caster, target, location, power, args) -> def;

		try {
			T val = Enum.valueOf(type, value.toUpperCase());
			return (caster, target, location, power, args) -> val;
		} catch (IllegalArgumentException e) {
			ConfigData<String> supplier = getString(value);
			if (supplier.isConstant())
				return (caster, target, location, power, args) -> def;

			return new ConfigData<>() {

				@Override
				public T get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
					String val = supplier.get(caster, target, location, power, args);
					if (val == null)
						return def;

					try {
						return Enum.valueOf(type, val.toUpperCase());
					} catch (IllegalArgumentException ex) {
						return def;
					}
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}
	}

	public static ConfigData<Material> getMaterial(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable Material def) {
		String value = config.getString(path);
		if (value == null)
			return (caster, target, location, power, args) -> def;

		Material val = Util.getMaterial(value);
		if (val != null)
			return (caster, target, location, power, args) -> val;

		ConfigData<String> supplier = getString(value);
		if (supplier.isConstant())
			return (caster, target, location, power, args) -> def;

		return new ConfigData<>() {

			@Override
			public Material get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				String val = supplier.get(caster, target, location, power, args);
				if (val == null)
					return def;

				Material material = Util.getMaterial(val);
				return material == null ? def : material;
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	public static ConfigData<MagicItem> getMagicItem(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable String def) {
		String value = config.getString(path);
		if (value == null)
			return (caster, target, location, power, args) -> MagicItems.getMagicItemFromString(def);

		ConfigData<String> supplier = getString(value);
		if (supplier.isConstant())
			return (caster, target, location, power, args) -> MagicItems.getMagicItemFromString(value);

		return new ConfigData<>() {

			@Override
			public MagicItem get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				String val = supplier.get(caster, target, location, power, args);
				if (val == null)
					return MagicItems.getMagicItemFromString(def);

				MagicItem material = MagicItems.getMagicItemFromString(val);
				return material == null ? MagicItems.getMagicItemFromString(def) : material;
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	public static ConfigData<MagicItem> getMagicItem(@NotNull String string) {
		String value = string;
		if (value == null)
			return (caster, target, location, power, args) -> null;

		ConfigData<String> supplier = getString(value);
		if (supplier.isConstant())
			return (caster, target, location, power, args) -> MagicItems.getMagicItemFromString(value);

		return new ConfigData<>() {

			@Override
			public MagicItem get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				String val = supplier.get(caster, target, location, power, args);
				if (val == null)
					return MagicItems.getMagicItemFromString(string);

				MagicItem material = MagicItems.getMagicItemFromString(val);
				return material == null ? MagicItems.getMagicItemFromString(string) : material;
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	@NotNull
	public static ConfigData<PotionEffectType> getPotionEffectType(@NotNull ConfigurationSection config,
			@NotNull String path, @Nullable PotionEffectType def) {
		String value = config.getString(path);
		if (value == null)
			return (caster, target, location, power, args) -> def;

		PotionEffectType type = Util.getPotionEffectType(value);
		if (type != null)
			return (caster, target, location, power, args) -> type;

		ConfigData<String> supplier = getString(value);
		if (supplier.isConstant())
			return (caster, target, location, power, args) -> def;

		return new ConfigData<>() {

			@Override
			public PotionEffectType get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				String val = supplier.get(caster, target, location, power, args);
				if (val == null)
					return def;

				PotionEffectType type = Util.getPotionEffectType(val);
				return type == null ? def : type;
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	@NotNull
	public static ConfigData<Particle> getParticle(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable Particle def) {
		String value = config.getString(path);
		if (value == null)
			return (caster, target, location, power, args) -> def;

		Particle val = ParticleUtil.getParticle(value);
		if (val != null)
			return (caster, target, location, power, args) -> val;

		ConfigData<String> supplier = getString(value);
		if (supplier.isConstant())
			return (caster, target, location, power, args) -> def;

		return new ConfigData<>() {

			@Override
			public Particle get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				String val = supplier.get(caster, target, location, power, args);
				if (val == null)
					return def;

				Particle particle = ParticleUtil.getParticle(val);
				return particle == null ? def : particle;
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	@NotNull
	public static ConfigData<BlockData> getBlockData(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable BlockData def) {
		String value = config.getString(path);
		if (value == null)
			return (caster, target, location, power, args) -> def;

		try {
			BlockData val = Bukkit.createBlockData(value.trim().toLowerCase());
			return (caster, target, location, power, args) -> val;
		} catch (IllegalArgumentException e) {
			ConfigData<String> supplier = getString(value);
			if (supplier.isConstant())
				return (caster, target, location, power, args) -> def;

			return new ConfigData<>() {

				@Override
				public BlockData get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					String val = supplier.get(caster, target, location, power, args);
					if (val == null)
						return def;

					try {
						return Bukkit.createBlockData(val.trim().toLowerCase());
					} catch (IllegalArgumentException e) {
						return def;
					}
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}
	}

	public static <T extends Keyed> ConfigData<T> getRegistryEntry(@NotNull ConfigurationSection config,
			@NotNull String path, @NotNull Registry<T> registry, @Nullable T def) {
		String value = config.getString(path);
		if (value == null)
			return (caster, target, location, power, args) -> def;

		NamespacedKey key = NamespacedKey.fromString(value);
		if (key != null) {
			T val = registry.get(key);
			if (val != null)
				return (caster, target, location, power, args) -> val;
		}

		ConfigData<String> supplier = getString(value);
		if (supplier.isConstant())
			return (caster, target, location, power, args) -> def;

		return new ConfigData<>() {

			@Override
			public T get(LivingEntity caster, LivingEntity target, Location location, float power, String[] args) {
				String val = supplier.get(caster, target, location, power, args);
				if (val == null)
					return def;

				NamespacedKey key = NamespacedKey.fromString(val);
				if (key == null)
					return def;

				T entry = registry.get(key);
				return entry == null ? def : entry;
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	@NotNull
	public static ConfigData<Vector> getVector(@NotNull ConfigurationSection config, @NotNull String path,
			@NotNull Vector def) {
		if (config.isString(path)) {
			String value = config.getString(path);
			if (value == null)
				return (caster, target, location, power, args) -> def;

			String[] data = value.split(",");
			if (data.length != 3)
				return (caster, target, location, power, args) -> def;

			try {
				Vector vector = new Vector(Double.parseDouble(data[0]), Double.parseDouble(data[1]),
						Double.parseDouble(data[2]));
				return (caster, target, location, power, args) -> vector;
			} catch (NumberFormatException e) {
				return (caster, target, location, power, args) -> def;
			}
		}

		if (config.isConfigurationSection(path)) {
			ConfigurationSection section = config.getConfigurationSection(path);
			if (section == null)
				return (caster, target, location, power, args) -> def;

			ConfigData<Double> x = getDouble(section, "x", def.getX());
			ConfigData<Double> y = getDouble(section, "y", def.getY());
			ConfigData<Double> z = getDouble(section, "z", def.getZ());

			if (x.isConstant() && y.isConstant() && z.isConstant()) {
				Vector vector = new Vector(x.get(null), y.get(null), z.get(null));
				return (caster, target, location, power, args) -> vector;
			}

			return (caster, target, location, power, args) -> new Vector(
					x.get(caster, target, location, power, args),
					y.get(caster, target, location, power, args),
					z.get(caster, target, location, power, args));
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<EulerAngle> getEulerAngle(@NotNull ConfigurationSection config, @NotNull String path,
			@NotNull EulerAngle def) {
		if (config.isString(path)) {
			String value = config.getString(path);
			if (value == null)
				return (caster, target, location, power, args) -> def;

			String[] data = value.split(",");
			if (data.length != 3)
				return (caster, target, location, power, args) -> def;

			try {
				EulerAngle angle = new EulerAngle(Double.parseDouble(data[0]), Double.parseDouble(data[1]),
						Double.parseDouble(data[2]));
				return (caster, target, location, power, args) -> angle;
			} catch (NumberFormatException e) {
				return (caster, target, location, power, args) -> def;
			}
		}

		if (config.isConfigurationSection(path)) {
			ConfigurationSection section = config.getConfigurationSection(path);
			if (section == null)
				return (caster, target, location, power, args) -> def;

			ConfigData<Double> x = getDouble(section, "x", def.getX());
			ConfigData<Double> y = getDouble(section, "y", def.getY());
			ConfigData<Double> z = getDouble(section, "z", def.getZ());

			if (x.isConstant() && y.isConstant() && z.isConstant()) {
				EulerAngle angle = new EulerAngle(x.get(null), y.get(null), z.get(null));
				return (caster, target, location, power, args) -> angle;
			}

			return (caster, target, location, power, args) -> new EulerAngle(
					x.get(caster, target, location, power, args),
					y.get(caster, target, location, power, args),
					z.get(caster, target, location, power, args));
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Vector3f> getVector3f(@NotNull ConfigurationSection config, @NotNull String path,
			@NotNull Vector3f def) {
		if (config.isString(path)) {
			ConfigData<String> supplier = getString(config, path, null);
			if (supplier.isConstant()) {
				String value = supplier.get(null);
				if (value == null)
					return (caster, target, location, power, args) -> def;
				String[] parts = value.split(",");
				if (parts.length != 3)
					return (caster, target, location, power, args) -> def;
				try {
					Vector3f vector = new Vector3f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
							Float.parseFloat(parts[2]));
					return (caster, target, location, power, args) -> vector;
				} catch (NumberFormatException e) {
					return (caster, target, location, power, args) -> def;
				}
			}

			return new ConfigData<>() {

				@Override
				public Vector3f get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					String value = supplier.get(caster, target, location, power, args);
					if (value == null)
						return def;
					String[] parts = value.split(",");
					if (parts.length != 3)
						return def;
					try {
						return new Vector3f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
								Float.parseFloat(parts[2]));
					} catch (NumberFormatException e) {
						return def;
					}
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		if (config.isConfigurationSection(path)) {
			ConfigurationSection section = config.getConfigurationSection(path);
			if (section == null)
				return (caster, target, location, power, args) -> def;

			ConfigData<Float> x = getFloat(section, "x", def.x);
			ConfigData<Float> y = getFloat(section, "y", def.y);
			ConfigData<Float> z = getFloat(section, "z", def.z);

			if (x.isConstant() && y.isConstant() && z.isConstant()) {
				Vector3f vector = new Vector3f(x.get(null), y.get(null), z.get(null));
				return (caster, target, location, power, args) -> vector;
			}

			return new ConfigData<>() {

				@Override
				public Vector3f get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					return new Vector3f(x.get(caster, target, location, power, args),
							y.get(caster, target, location, power, args),
							z.get(caster, target, location, power, args));
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Quaternionf> getQuaternionf(@NotNull ConfigurationSection config, @NotNull String path,
			@NotNull Quaternionf def) {
		if (config.isString(path)) {
			ConfigData<String> supplier = getString(config, path, null);
			if (supplier.isConstant()) {
				String value = supplier.get(null);
				if (value == null)
					return (caster, target, location, power, args) -> def;
				String[] parts = value.split(",");
				if (parts.length != 4)
					return (caster, target, location, power, args) -> def;
				try {
					Quaternionf rot = new Quaternionf(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
							Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
					return (caster, target, location, power, args) -> rot;
				} catch (NumberFormatException e) {
					return (caster, target, location, power, args) -> def;
				}
			}

			return new ConfigData<>() {

				@Override
				public Quaternionf get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					String value = supplier.get(caster, target, location, power, args);
					if (value == null)
						return def;
					String[] parts = value.split(",");
					if (parts.length != 4)
						return def;
					try {
						return new Quaternionf(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]),
								Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
					} catch (NumberFormatException e) {
						return def;
					}
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		if (config.isConfigurationSection(path)) {
			ConfigurationSection section = config.getConfigurationSection(path);
			if (section == null)
				return (caster, target, location, power, args) -> def;

			if (section.contains("angle") && section.contains("axis")) {
				ConfigData<Float> angle = getFloat(section, "angle", 0);
				ConfigData<Vector3f> axis = getVector3f(section, "axis", new Vector3f(0, 1, 0));

				if (angle.isConstant() && axis.isConstant()) {
					Quaternionf quat = new Quaternionf().rotationAxis((float) Math.toRadians(angle.get(null)),
							axis.get(null));
					return (caster, target, location, power, args) -> quat;
				}

				return new ConfigData<>() {

					@Override
					public Quaternionf get(LivingEntity caster, LivingEntity target, Location location, float power,
							String[] args) {
						return new Quaternionf().rotationAxis(
								(float) Math.toRadians(angle.get(caster, target, location, power, args)),
								axis.get(caster, target, location, power, args));
					}

					@Override
					public boolean isConstant() {
						return false;
					}

				};
			}

			ConfigData<Float> x = getFloat(section, "x", def.x);
			ConfigData<Float> y = getFloat(section, "y", def.y);
			ConfigData<Float> z = getFloat(section, "z", def.z);
			ConfigData<Float> w = getFloat(section, "w", def.w);

			if (x.isConstant() && y.isConstant() && z.isConstant() && w.isConstant()) {
				Quaternionf quat = new Quaternionf(x.get(null), y.get(null), z.get(null), w.get(null));
				return (caster, target, location, power, args) -> quat;
			}

			return new ConfigData<>() {

				@Override
				public Quaternionf get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					return new Quaternionf(x.get(caster, target, location, power, args),
							y.get(caster, target, location, power, args),
							z.get(caster, target, location, power, args), w.get(caster, target, location, power, args));
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		return (caster, target, location, power, args) -> def;
	}

	public static ConfigData<Color> getColor(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable Color def) {
		if (config.isInt(path) || config.isString(path)) {
			String value = config.getString(path);
			if (value == null)
				return (caster, target, location, power, args) -> def;

			ConfigData<String> supplier = getString(value);
			if (supplier.isConstant()) {
				Color color = ColorUtil.getColorFromHexString(value, false);
				if (color == null)
					return (caster, target, location, power, args) -> def;

				return (caster, target, location, power, args) -> color;
			}

			return new ConfigData<>() {

				@Override
				public Color get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					Color color = ColorUtil.getColorFromHexString(supplier.get(caster, target, location, power, args),
							false);
					return color == null ? def : color;
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		if (config.isConfigurationSection(path)) {
			ConfigurationSection section = config.getConfigurationSection(path);
			if (section == null)
				return (caster, target, location, power, args) -> def;

			ConfigData<Integer> red = getInteger(section, "red");
			ConfigData<Integer> green = getInteger(section, "green");
			ConfigData<Integer> blue = getInteger(section, "blue");

			if (red.isConstant() && green.isConstant() && blue.isConstant()) {
				Integer r = red.get(null);
				Integer g = green.get(null);
				Integer b = blue.get(null);
				if (r == null || g == null || b == null || r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255)
					return (caster, target, location, power, args) -> def;

				Color c = Color.fromRGB(r, g, b);
				return (caster, target, location, power, args) -> c;
			}

			return new ConfigData<>() {

				@Override
				public Color get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					Integer r = red.get(caster, target, location, power, args);
					Integer g = green.get(caster, target, location, power, args);
					Integer b = blue.get(caster, target, location, power, args);
					if (r == null || g == null || b == null || r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255)
						return def;

					return Color.fromRGB(r, g, b);
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		return (caster, target, location, power, args) -> def;
	}

	public static ConfigData<Color> getARGBColor(@NotNull ConfigurationSection config, @NotNull String path,
			@Nullable Color def) {
		if (config.isInt(path) || config.isString(path)) {
			String value = config.getString(path);
			if (value == null)
				return (caster, target, location, power, args) -> def;

			ConfigData<String> supplier = getString(value);
			if (supplier.isConstant()) {
				Color color = ColorUtil.getColorFromARGHexString(value, false);
				if (color == null)
					return (caster, target, location, power, args) -> def;

				return (caster, target, location, power, args) -> color;
			}

			return new ConfigData<>() {

				@Override
				public Color get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					Color color = ColorUtil
							.getColorFromARGHexString(supplier.get(caster, target, location, power, args), false);
					return color == null ? def : color;
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		if (config.isConfigurationSection(path)) {
			ConfigurationSection section = config.getConfigurationSection(path);
			if (section == null)
				return (caster, target, location, power, args) -> def;

			ConfigData<Integer> alpha = getInteger(section, "alpha");
			ConfigData<Integer> red = getInteger(section, "red");
			ConfigData<Integer> green = getInteger(section, "green");
			ConfigData<Integer> blue = getInteger(section, "blue");

			if (alpha.isConstant() && red.isConstant() && green.isConstant() && blue.isConstant()) {
				Integer a = alpha.get(null);
				Integer r = red.get(null);
				Integer g = green.get(null);
				Integer b = blue.get(null);
				if (a == null || r == null || g == null || b == null || a < 0 || a > 255 || r < 0 || r > 255 || g < 0
						|| g > 255 || b < 0 || b > 255)
					return (caster, target, location, power, args) -> def;

				Color c = Color.fromARGB(a, r, g, b);
				return (caster, target, location, power, args) -> c;
			}

			return new ConfigData<>() {

				@Override
				public Color get(LivingEntity caster, LivingEntity target, Location location, float power,
						String[] args) {
					Integer a = alpha.get(caster, target, location, power, args);
					Integer r = red.get(caster, target, location, power, args);
					Integer g = green.get(caster, target, location, power, args);
					Integer b = blue.get(caster, target, location, power, args);
					if (a == null || r == null || g == null || b == null || a < 0 || a > 255 || r < 0 || r > 255
							|| g < 0 || g > 255 || b < 0 || b > 255)
						return def;

					return Color.fromARGB(a, r, g, b);
				}

				@Override
				public boolean isConstant() {
					return false;
				}

			};
		}

		return (caster, target, location, power, args) -> def;
	}

	@NotNull
	public static ConfigData<Particle.Spell> getSpellOptions(@NotNull ConfigurationSection config,
			@NotNull String colorPath,
			@NotNull String powerPath,
			@Nullable Particle.Spell def) {
		ConfigData<Color> color = getColor(config, colorPath, def == null ? null : def.getColor());
		ConfigData<Float> power = def == null ? getFloat(config, powerPath) : getFloat(config, powerPath, def.getPower());

		if (color.isConstant() && power.isConstant()) {
			Color c = color.get(null);
			if (c == null)
				return (caster, target, location, castPower, args) -> def;

			Float p = power.get(null);
			if (p == null)
				return (caster, target, location, castPower, args) -> def;

			Particle.Spell options = new Particle.Spell(c, p);
			return (caster, target, location, castPower, args) -> options;
		}

		return new ConfigData<>() {

			@Override
			public Particle.Spell get(Entity caster, Entity target, Location location, float castPower,
					String[] args) {
				Color c = color.get(caster, target, location, castPower, args);
				if (c == null)
					return def;

				Float p = power.get(caster, target, location, castPower, args);
				if (p == null)
					return def;

				return new Particle.Spell(c, p);
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	@NotNull
	public static ConfigData<DustOptions> getDustOptions(@NotNull ConfigurationSection config,
			@NotNull String colorPath,
			@NotNull String sizePath,
			@Nullable DustOptions def) {
		ConfigData<Color> color = getColor(config, colorPath, def == null ? null : def.getColor());
		ConfigData<Float> size = def == null ? getFloat(config, sizePath) : getFloat(config, sizePath, def.getSize());
		String sizeConfigPath = config.getCurrentPath() + '.' + sizePath;

		if (color.isConstant() && size.isConstant()) {
			Color c = color.get(null);
			if (c == null)
				return (caster, target, location, power, args) -> def;

			Float s = size.get(null);
			if (s == null)
				return (caster, target, location, power, args) -> def;

			DustOptions options = new DustOptions(c, clampDustSize(s, sizeConfigPath));
			return (caster, target, location, power, args) -> options;
		}

		return new ConfigData<>() {

			@Override
			public DustOptions get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				Color c = color.get(caster, target, location, power, args);
				if (c == null)
					return def;

				Float s = size.get(caster, target, location, power, args);
				if (s == null)
					return def;

				return new DustOptions(c, clampDustSize(s, sizeConfigPath));
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	@NotNull
	public static ConfigData<DustTransition> getDustTransition(@NotNull ConfigurationSection config,
			@NotNull String colorPath,
			@NotNull String toColorPath,
			@NotNull String sizePath,
			@Nullable DustTransition def) {
		ConfigData<Color> color = getColor(config, colorPath, def == null ? null : def.getColor());
		ConfigData<Color> toColor = getColor(config, toColorPath, def == null ? null : def.getToColor());
		ConfigData<Float> size = def == null ? getFloat(config, sizePath) : getFloat(config, sizePath, def.getSize());
		String sizeConfigPath = config.getCurrentPath() + '.' + sizePath;

		if (color.isConstant() && toColor.isConstant() && size.isConstant()) {
			Color c = color.get(null);
			if (c == null)
				return (caster, target, location, power, args) -> def;

			Color tc = toColor.get(null);
			if (tc == null)
				return (caster, target, location, power, args) -> def;

			Float s = size.get(null);
			if (s == null)
				return (caster, target, location, power, args) -> def;

			DustTransition transition = new DustTransition(c, tc, clampDustSize(s, sizeConfigPath));
			return (caster, target, location, power, args) -> transition;
		}

		return new ConfigData<>() {

			@Override
			public DustTransition get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				Color c = color.get(caster, target, location, power, args);
				if (c == null)
					return def;

				Color tc = toColor.get(caster, target, location, power, args);
				if (tc == null)
					return def;

				Float s = size.get(caster, target, location, power, args);
				if (s == null)
					return def;

				return new DustTransition(c, tc, clampDustSize(s, sizeConfigPath));
			}

			@Override
			public boolean isConstant() {
				return false;
			}

		};
	}

	private static float clampDustSize(float size, String configPath) {
		float clampedSize = Math.clamp(size, 0.01F, 4F);
		if (Float.compare(size, clampedSize) != 0) {
			MagicSpells.error("Dust particle size " + size + " at '" + configPath
					+ "' is outside the supported range [0.01, 4.0]; using " + clampedSize + " instead.");
		}
		return clampedSize;
	}

	@NotNull
	public static ConfigData<List<String>> getStringList(@NotNull ConfigurationSection config, @NotNull String path) {
		List<String> value = config.getStringList(path);
		if (value.isEmpty())
			return (caster, target, location, power, args) -> null;

		return getStringList(value);
	}

	@NotNull
	public static ConfigData<List<String>> getStringList(@Nullable List<String> value) {
		if (value == null || value.isEmpty()) {
			return (caster, target, location, power, args) -> null;
		}

		List<ConfigData<String>> configDataList = new ArrayList<>();
		for (String str : value) {
			configDataList.add(getString(str));
		}

		return new ConfigData<>() {

			@Override
			public List<String> get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				List<String> results = new ArrayList<>();
				for (ConfigData<String> configData : configDataList) {
					results.add(configData.get(caster, target, location, power, args));
				}
				return results;
			}

			@Override
			public boolean isConstant() {
				for (ConfigData<String> configData : configDataList) {
					if (!configData.isConstant()) {
						return false;
					}
				}
				return true;
			}
		};
	}

	@NotNull
	public static ConfigData<List<Integer>> getIntList(@NotNull ConfigurationSection config, @NotNull String path) {
		if (config.isList(path)) {
			List<String> value = config.getStringList(path);
			if (value.isEmpty())
				return (caster, target, location, power, args) -> null;
			return getIntList(value);
		}

		// Handle single string value
		String singleValue = config.getString(path);
		if (singleValue == null)
			return (caster, target, location, power, args) -> null;

		List<String> singleItemList = new ArrayList<>();
		singleItemList.add(singleValue);
		return getIntList(singleItemList);
	}

	@NotNull
	public static ConfigData<List<Integer>> getIntList(@Nullable List<String> value) {
		if (value == null || value.isEmpty()) {
			return (caster, target, location, power, args) -> null;
		}

		List<ConfigData<Integer>> configDataList = new ArrayList<>();
		for (String str : value) {
			configDataList.add(getInteger(str));
		}

		return new ConfigData<>() {

			@Override
			public List<Integer> get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				List<Integer> results = new ArrayList<>();
				for (ConfigData<Integer> configData : configDataList) {
					results.add(configData.get(caster, target, location, power, args));
				}
				return results;
			}

			@Override
			public boolean isConstant() {
				for (ConfigData<Integer> configData : configDataList) {
					if (!configData.isConstant()) {
						return false;
					}
				}
				return true;
			}
		};
	}

	@NotNull
	public static ConfigData<ConfigurationSection> getConfigurationSection(@NotNull ConfigurationSection config,
			@NotNull String path) {
		if (!config.isConfigurationSection(path))
			return (caster, target, location, power, args) -> null;

		ConfigurationSection section = config.getConfigurationSection(path);
		if (section == null)
			return (caster, target, location, power, args) -> null;

		ConfigurationSection sectionData = new YamlConfiguration();

		for (String key : section.getKeys(false)) {
			if (section.isConfigurationSection(key)) {
				ConfigData<ConfigurationSection> configData = getConfigurationSection(section, key);
				sectionData.set(key, configData);
			}

			if (section.isBoolean(key)) {
				ConfigData<Boolean> configData = getBoolean(section, key);
				sectionData.set(key, configData);
			}

			if (section.isInt(key)) {
				ConfigData<Integer> configData = getInteger(section, key);
				sectionData.set(key, configData);
			}

			if (section.isDouble(key)) {
				ConfigData<Double> configData = getDouble(section, key);
				sectionData.set(key, configData);
			}

			if (section.isList(key)) {
				ConfigData<List<String>> configData = getStringList(section, key);
				sectionData.set(key, configData);
			}

			else {
				ConfigData<String> configData = getString(section, key, null);
				sectionData.set(key, configData);
			}
		}

		return new ConfigData<>() {

			@Override
			public ConfigurationSection get(LivingEntity caster, LivingEntity target, Location location, float power,
					String[] args) {
				ConfigurationSection results = new YamlConfiguration();

				for (String key : sectionData.getKeys(false)) {
					ConfigData<?> data = (ConfigData<?>) sectionData.get(key);
					results.set(key, data.get(caster, target, location, power, args));
				}

				return results;
			}

			@Override
			public boolean isConstant() {
				return false;
			}
		};

	}

}
