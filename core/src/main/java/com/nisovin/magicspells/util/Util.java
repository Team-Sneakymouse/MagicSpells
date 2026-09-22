package com.nisovin.magicspells.util;

import java.io.File;
import java.io.InputStream;

import java.net.URL;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.nisovin.magicspells.util.io.AtomicFiles;
import com.nisovin.magicspells.util.compat.EventUtil;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.apache.commons.math4.core.jdkmath.AccurateMath;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Subspell.CastMode;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.handlers.PotionEffectHandler;
import com.nisovin.magicspells.util.magicitems.MagicItemData;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class Util {

	private static final Random random = ThreadLocalRandom.current();

	private static final Pattern WEIRD_HEX_PATTERN = Pattern.compile("[&§]x(([&§][0-9a-f]){6})", Pattern.CASE_INSENSITIVE);
	private static final Pattern COLOR_PATTERN = Pattern.compile("[&§]([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
	private static final Pattern HEX_PATTERN = Pattern.compile("[&§](#[0-9a-f]{6})", Pattern.CASE_INSENSITIVE);

	public static int getRandomInt(int bound) {
		if (bound < 1) return 0;
		return random.nextInt(bound);
	}

	public static double round(double value, int places) {
		return round(value, places, RoundingMode.HALF_UP);
	}

	public static double round(double value, int places, RoundingMode roundingMode) {
		if (places < 0) throw new IllegalArgumentException("places cant be lower than 0");

		return BigDecimal.valueOf(value).setScale(places, roundingMode).doubleValue();
	}

	public static Material getMaterial(String name) {
		return Material.matchMaterial(name);
	}

	// - <potionEffectType> (level) (duration) (ambient)
	public static PotionEffect buildPotionEffect(String effectString) {
		String[] data = effectString.split(" ");
		PotionEffectType t = getPotionEffectType(data[0]);

		if (t == null) {
			MagicSpells.error('\'' + data[0] + "' could not be connected to a potion effect type");
			return null;
		}

		int level = 0;
		if (data.length > 1) {
			try {
				level = Integer.parseInt(data[1]);
			} catch (NumberFormatException ex) {
				DebugHandler.debugNumberFormat(ex);
			}
		}

		int duration = 600;
		if (data.length > 2) {
			try {
				duration = Integer.parseInt(data[2]);
			} catch (NumberFormatException ex) {
				DebugHandler.debugNumberFormat(ex);
			}
		}

		boolean ambient = data.length > 3 && (BooleanUtils.isYes(data[3]) || data[3].equalsIgnoreCase("ambient"));

		boolean particles = data.length > 4 && (BooleanUtils.isYes(data[4]) || data[4].equalsIgnoreCase("particles"));

		boolean icon = data.length > 5 && (BooleanUtils.isYes(data[5]) || data[5].equalsIgnoreCase("icon"));

		return new PotionEffect(t, duration, level, ambient, particles, icon);
	}

	// - <potionEffectType> (duration)
	public static PotionEffect buildSuspiciousStewPotionEffect(String effectString) {
		String[] data = effectString.split(" ");
		PotionEffectType t = getPotionEffectType(data[0]);

		if (t == null) {
			MagicSpells.error('\'' + data[0] + "' could not be connected to a potion effect type");
			return null;
		}

		int duration = 600;
		if (data.length > 1) {
			try {
				duration = Integer.parseInt(data[1]);
			} catch (NumberFormatException ex) {
				DebugHandler.debugNumberFormat(ex);
			}
		}
		return new PotionEffect(t, duration, 0, true);
	}

	public static Color[] getColorsFromString(String str) {
		int[] colors = new int[]{0xFF0000};
		String[] args = str.replace(" ", "").split(",");
		if (args.length > 0) {
			colors = new int[args.length];
			for (int i = 0; i < colors.length; i++) {
				try {
					colors[i] = Integer.parseInt(args[i], 16);
				} catch (NumberFormatException e) {
					colors[i] = 0;
				}
			}
		}

		Color[] c = new Color[colors.length];
		for (int i = 0; i < colors.length; i++) {
			c[i] = Color.fromRGB(colors[i]);
		}
		return c;
	}

	public static PotionEffectType getPotionEffectType(String type) {
		return PotionEffectHandler.getPotionEffectType(type);
	}

	public static Particle getParticle(String type) {
		return ParticleUtil.getParticle(type);
	}

	public static CastMode getCastMode(String type) {
		return CastMode.getFromString(type);
	}

	public static void setFacing(Player player, Vector vector) {
		Location loc = player.getLocation();
		setLocationFacingFromVector(loc, vector);
		player.teleportAsync(loc);
	}

	public static void setLocationFacingFromVector(Location location, Vector vector) {
		double yaw = getYawOfVector(vector);
		double pitch = AccurateMath.toDegrees(-AccurateMath.asin(vector.getY()));
		location.setYaw((float) yaw);
		location.setPitch((float) pitch);
	}

	public static double getYawOfVector(Vector vector) {
		return AccurateMath.toDegrees(AccurateMath.atan2(-vector.getX(), vector.getZ()));
	}

	public static boolean arrayContains(int[] array, int value) {
		for (int i : array) {
			if (i == value) return true;
		}
		return false;
	}

	public static boolean arrayContains(String[] array, String value) {
		for (String i : array) {
			if (Objects.equals(i, value)) return true;
		}
		return false;
	}

	public static boolean arrayContains(Object[] array, Object value) {
		for (Object i : array) {
			if (Objects.equals(i, value)) return true;
		}
		return false;
	}

	public static String arrayJoin(String[] array, char with) {
		if (array == null || array.length == 0) return "";
		int len = array.length;
		StringBuilder sb = new StringBuilder(16 + len << 3);
		sb.append(array[0]);
		for (int i = 1; i < len; i++) {
			sb.append(with);
			sb.append(array[i]);
		}
		return sb.toString();
	}

	public static String listJoin(List<String> list) {
		if (list == null || list.isEmpty()) return "";
		int len = list.size();
		StringBuilder sb = new StringBuilder(len * 12);
		sb.append(list.get(0));
		for (int i = 1; i < len; i++) {
			sb.append(' ');
			sb.append(list.get(i));
		}
		return sb.toString();
	}

	/**
	 * Splits a parameter string on whitespace, treating {@code "} or {@code '} as text
	 * qualifiers: spaces inside a matched quote pair belong to that argument, and the
	 * quote characters themselves are not included in the result.
	 *
	 * @param string input to split
	 * @param max    maximum number of arguments; if {@code > 0}, the last argument
	 *               consumes the remainder of the input (quotes in that remainder are
	 *               not specially parsed)
	 * @see ParamUtil#splitParams(String, int)
	 */
	public static String[] splitParams(String string, int max) {
		return ParamUtil.splitParams(string, max);
	}

	public static String[] splitParams(String string) {
		return ParamUtil.splitParams(string);
	}

	public static String[] splitParams(String[] split, int max) {
		return ParamUtil.splitParams(split, max);
	}

	public static String[] splitParams(String[] split) {
		return ParamUtil.splitParams(split);
	}

	public static boolean removeFromInventory(Player player, Inventory inventory, Map.Entry<MagicItemData, Integer> item) {
		MagicItemData itemData = item.getKey();
		if (itemData == null) return false;

		int amt = item.getValue();
		ItemStack[] items = inventory.getContents();
		ItemStack stack = null;
		for (int i = 0; i < items.length; i++) {
			if (items[i] == null) continue;

			if (!MagicItems.matches(itemData, items[i])) continue;

			stack = items[i].clone();

			if (items[i].getAmount() > amt) {
				items[i].setAmount(items[i].getAmount() - amt);
				amt = 0;
				break;
			}

			if (items[i].getAmount() == amt) {
				items[i] = null;
				amt = 0;
				break;
			}

			amt -= items[i].getAmount();
			items[i] = null;
		}

		if (amt == 0 && stack != null) {
			stack.setAmount(item.getValue());
			PlayerItemBreakEvent event = new PlayerItemBreakEvent(player, stack);
			EventUtil.call(event);

			inventory.setContents(items);
			return true;
		}

		return false;
	}

	public static boolean removeFromInventory(EntityEquipment entityEquipment, Map.Entry<MagicItemData, Integer> item) {
		MagicItemData itemData = item.getKey();
		if (itemData == null) return false;

		int amt = item.getValue();
		ItemStack[] armorContents = entityEquipment.getArmorContents();
		ItemStack[] items = new ItemStack[6];
		System.arraycopy(armorContents, 0, items, 0, 4);
		items[4] = entityEquipment.getItemInMainHand();
		items[5] = entityEquipment.getItemInOffHand();

		for (int i = 0; i < items.length; i++) {
			if (items[i] == null) continue;

			if (!MagicItems.matches(itemData, items[i])) continue;

			if (items[i].getAmount() > amt) {
				items[i].setAmount(items[i].getAmount() - amt);
				amt = 0;
				break;
			}

			if (items[i].getAmount() == amt) {
				items[i] = null;
				amt = 0;
				break;
			}

			amt -= items[i].getAmount();
			items[i] = null;
		}

		if (amt == 0) {
			ItemStack[] updatedArmorContents = new ItemStack[4];
			System.arraycopy(items, 0, updatedArmorContents, 0, 4);
			entityEquipment.setArmorContents(updatedArmorContents);
			entityEquipment.setItemInMainHand(items[4]);
			entityEquipment.setItemInOffHand(items[5]);
			return true;
		}
		return false;
	}

	public static boolean addToInventory(Player player, Inventory inventory, ItemStack item, boolean stackExisting, boolean ignoreMaxStack) {
		return addToInventory(player, inventory, item, stackExisting, ignoreMaxStack, null);
	}

	public static boolean addToInventory(Player player, Inventory inventory, ItemStack item, boolean stackExisting, boolean ignoreMaxStack, Set<Integer> omittedSlots) {
		int amt = item.getAmount();
		ItemStack[] items = new ItemStack[inventory.getStorageContents().length];

		for (int i = 0; i < inventory.getStorageContents().length; i++) {
			ItemStack itemStack = inventory.getStorageContents()[i];
			if (itemStack != null) items[i] = itemStack.clone();
		}

		if (stackExisting) {
			for (int i = 0; i < items.length; i++) {
				if (omittedSlots != null && omittedSlots.contains(i)) continue;
				ItemStack itemStack = items[i];
				if (itemStack == null || !isSimilarNoFlags(itemStack, item)) continue;

				if (itemStack.getAmount() + amt <= itemStack.getMaxStackSize()) {
					itemStack.setAmount(itemStack.getAmount() + amt);
					amt = 0;
					break;
				}

				int diff = itemStack.getMaxStackSize() - itemStack.getAmount();
				itemStack.setAmount(itemStack.getMaxStackSize());
				amt -= diff;
			}
		}

		if (amt > 0) {
			for (int i = 0; i < items.length; i++) {
				if (omittedSlots != null && omittedSlots.contains(i)) continue;
				if (items[i] != null) continue;
				if (amt > item.getMaxStackSize() && !ignoreMaxStack) {
					items[i] = item.clone();
					items[i].setAmount(item.getMaxStackSize());
					amt -= item.getMaxStackSize();
					continue;
				}

				items[i] = item.clone();
				items[i].setAmount(amt);
				amt = 0;
				break;
			}
		}

		if (amt == 0) {
			ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(MagicSpells.getInstance(), "magicspells_craft"), item);

			CraftItemEvent event = new CraftItemEvent(recipe, createInventoryView(player), InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.UNKNOWN);
			EventUtil.call(event);
			event.setCancelled(false);
			inventory.setStorageContents(items);
			return true;
		}

		return false;
	}

	public static boolean isSimilarNoFlags(ItemStack left_, ItemStack right_) {
		ItemStack left = left_.clone();
		ItemMeta leftMeta = left.getItemMeta();
		ItemStack right = right_.clone();
		ItemMeta rightMeta = right.getItemMeta();

		for (ItemFlag itemFlag : ItemFlag.values()) {
			for (ItemMeta meta : Arrays.asList(leftMeta, rightMeta)) {
				if (meta.hasItemFlag(itemFlag)) meta.removeItemFlags(itemFlag);
			}
		}

		left.setItemMeta(leftMeta);
		right.setItemMeta(rightMeta);

		return left.isSimilar(right);
	}

	public static void rotateVector(Vector v, double degrees) {
		double rad = AccurateMath.toRadians(degrees);
		double sin = AccurateMath.sin(rad);
		double cos = AccurateMath.cos(rad);

		double x = (v.getX() * cos) - (v.getZ() * sin);
		double z = (v.getX() * sin) + (v.getZ() * cos);

		v.setX(x);
		v.setZ(z);
	}

	public static Location applyRelativeOffset(Location location, Vector direction, float x, float y, float z) {
		location.add(new Vector(-direction.getZ(), 0.0, direction.getX()).normalize().multiply(z));
		location.add(location.getDirection().multiply(x));
		location.setY(location.getY() + y);

		return location;
	}

	public static Location applyRelativeOffset(Location loc, Vector relativeOffset, float pitch) {
		return loc.add(rotateVector(relativeOffset, loc, pitch));
	}

	public static Location applyRelativeOffset(Location loc, Vector relativeOffset) {
		return loc.add(rotateVector(relativeOffset, loc, loc.getPitch()));
	}

	public static Vector rotateVector(Vector v, Location location, float pitch) {
		return rotateVector(v, location.getYaw(), pitch);
	}

	public static Vector rotateVector(Vector v, float yawDegrees, float pitchDegrees) {
		double yaw = AccurateMath.toRadians(-1.0F * (yawDegrees + 90.0F));
		double pitch = AccurateMath.toRadians(-pitchDegrees);
		double cosYaw = AccurateMath.cos(yaw);
		double cosPitch = AccurateMath.cos(pitch);
		double sinYaw = AccurateMath.sin(yaw);
		double sinPitch = AccurateMath.sin(pitch);
		double initialX = v.getX();
		double initialY = v.getY();
		double x = initialX * cosPitch - initialY * sinPitch;
		double y = initialX * sinPitch + initialY * cosPitch;
		double initialZ = v.getZ();
		double z = initialZ * cosYaw - x * sinYaw;
		x = initialZ * sinYaw + x * cosYaw;
		return new Vector(x, y, z);
	}

	public static Location applyAbsoluteOffset(Location loc, Vector offset) {
		return loc.add(offset);
	}

	public static Location applyOffsets(Location loc, Vector relativeOffset, Vector absoluteOffset, float pitch) {
		return applyAbsoluteOffset(applyRelativeOffset(loc, relativeOffset, pitch), absoluteOffset);
	}

	public static Location applyOffsets(Location loc, Vector relativeOffset, Vector absoluteOffset) {
		return applyAbsoluteOffset(applyRelativeOffset(loc, relativeOffset, loc.getPitch()), absoluteOffset);
	}

	public static Location faceTarget(Location origin, Location target) {
		return origin.setDirection(getVectorToTarget(origin, target));
	}

	public static Vector getVectorToTarget(Location origin, Location target) {
		return target.toVector().subtract(origin.toVector());
	}

	public static boolean downloadFile(String url, File file) {
		try (InputStream input = new URL(url).openStream()) {
			AtomicFiles.copy(file.toPath(), input);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	private static final Map<String, String> uniqueIds = new HashMap<>();

	public static String getUniqueId(Player player) {
		String uid = player.getUniqueId().toString().replace("-", "");
		uniqueIds.put(player.getName(), uid);
		return uid;
	}

	public static String getUniqueId(String playerName) {
		if (uniqueIds.containsKey(playerName)) return uniqueIds.get(playerName);
		Player player = Bukkit.getPlayerExact(playerName);
		if (player != null) return getUniqueId(player);
		return null;
	}

	public static String flattenLineBreaks(String raw) {
		return raw.replaceAll("\n", "\\n");
	}

	public static <T> boolean containsParallel(Collection<T> elements, Predicate<? super T> predicate) {
		return elements.parallelStream().anyMatch(predicate);
	}

	public static <T> boolean containsValueParallel(Map<?, T> map, Predicate<? super T> predicate) {
		return containsParallel(map.values(), predicate);
	}

	public static <T> void forEachOrdered(Collection<T> collection, Consumer<? super T> consumer) {
		collection.stream().forEachOrdered(consumer);
	}

	public static <T> void forEachValueOrdered(Map<?, T> map, Consumer<? super T> consumer) {
		forEachOrdered(map.values(), consumer);
	}

	public static void forEachPlayerOnline(Consumer<? super Player> consumer) {
		forEachOrdered(Bukkit.getOnlinePlayers(), consumer);
	}

	public static int clampValue(int min, int max, int value) {
		if (value < min) return min;
		if (value > max) return max;
		return value;
	}

	public static <C extends Collection<Material>> C getMaterialList(List<String> strings, Supplier<C> supplier) {
		C ret = supplier.get();
		strings.forEach(string -> ret.add(getMaterial(string)));
		return ret;
	}

	public static <E extends Enum<E>> E enumValueSafe(Class<E> clazz, String name) {
		try {
			return Enum.valueOf(clazz, name);
		} catch (Exception e) {
			return null;
		}
	}

	public static double getMaxHealth(LivingEntity entity) {
		return entity.getAttribute(Attribute.MAX_HEALTH).getValue();
	}

	public static void setMaxHealth(LivingEntity entity, double maxHealth) {
		entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
	}

	public static Vector makeFinite(Vector vector) {
		double x = vector.getX();
		double y = vector.getY();
		double z = vector.getZ();

		if (Double.isNaN(x)) x = 0.0D;
		if (Double.isNaN(y)) y = 0.0D;
		if (Double.isNaN(z)) z = 0.0D;

		if (Double.isInfinite(x)) {
			boolean negative = (x < 0.0D);
			x = negative ? -1 : 1;
		}

		if (Double.isInfinite(y)) {
			boolean negative = (y < 0.0D);
			y = negative ? -1 : 1;
		}

		if (Double.isInfinite(z)) {
			boolean negative = (z < 0.0D);
			z = negative ? -1 : 1;
		}

		return new Vector(x, y, z);
	}

	public static Location makeFinite(Location location) {
		double x = location.getX();
		double y = location.getY();
		double z = location.getZ();

		float yaw = location.getYaw();
		float pitch = location.getPitch();

		if (Float.isNaN(yaw)) yaw = 0.0F;
		if (Float.isNaN(pitch)) pitch = 0.0F;

		if (Float.isInfinite(yaw)) {
			boolean negative = (yaw < 0.0F);
			yaw = negative ? -1F : 1F;
		}

		if (Float.isInfinite(pitch)) {
			boolean negative = (pitch < 0.0F);
			pitch = negative ? -1F : 1F;
		}

		if (Double.isNaN(x)) x = 0.0D;
		if (Double.isNaN(y)) y = 0.0D;
		if (Double.isNaN(z)) z = 0.0D;

		if (Double.isInfinite(x)) {
			boolean negative = (x < 0.0D);
			x = negative ? -1 : 1;
		}

		if (Double.isInfinite(y)) {
			boolean negative = (y < 0.0D);
			y = negative ? -1 : 1;
		}

		if (Double.isInfinite(z)) {
			boolean negative = (z < 0.0D);
			z = negative ? -1 : 1;
		}

		return new Location(location.getWorld(), x, y, z, yaw, pitch);
	}

	public static Vector getVector(String str) {
		String[] vecStrings = str.split(",");
		return new Vector(Double.parseDouble(vecStrings[0]), Double.parseDouble(vecStrings[1]), Double.parseDouble(vecStrings[2]));
	}

	public static Component getLegacyFromString(String input) {
		if (input.isEmpty()) return Component.text("");
		Component component = LegacyComponentSerializer.builder()
				.hexColors()
				.useUnusualXRepeatedCharacterHexFormat()
				.character(ChatColor.COLOR_CHAR)
				.build()
				.deserialize(colorize(input));
		return component.decoration(TextDecoration.ITALIC, component.hasDecoration(TextDecoration.ITALIC));
	}

	public static String getMiniMessageFromLegacy(String input) {
		StringBuilder builder = new StringBuilder();

		Matcher matcher = WEIRD_HEX_PATTERN.matcher(input);
		while (matcher.find()) {
			matcher.appendReplacement(builder, "<#" + matcher.group(1).replaceAll("[§&]", "") + ">");
		}
		matcher.appendTail(builder);

		matcher = HEX_PATTERN.matcher(builder.toString());
		builder.setLength(0);
		while (matcher.find()) {
			matcher.appendReplacement(builder, "<" + matcher.group(1) + ">");
		}
		matcher.appendTail(builder);

		matcher = COLOR_PATTERN.matcher(builder.toString());
		builder.setLength(0);
		while (matcher.find()) {
			ChatColor color = ChatColor.getByChar(matcher.group(1).toLowerCase());
			if (color == null) continue;

			matcher.appendReplacement(builder, color == ChatColor.UNDERLINE ? "<underlined>" : "<" + color.asBungee().getName() + ">");
		}
		matcher.appendTail(builder);

		return builder.toString();
	}

	public static String getLegacyFromComponent(Component component) {
		if (component == null) return "";
		return LegacyComponentSerializer.legacySection().serialize(component);
	}

	public static String getLegacyFromMiniMessage(String input) {
		if (input.isEmpty()) return "";
		return getLegacyFromComponent(getMiniMessage(input));
	}

	public static String getPlainString(Component component) {
		if (component == null) return "";

		// Return plain text component
		return PlainTextComponentSerializer.plainText().serialize(component);
	}

	public static Component getPlainComponent(Component input) {
		if (input == null) return Component.empty();

		// Serialize a plain text component and create a new component of the string.
		return Component.text(PlainTextComponentSerializer.plainText().serialize(input));
	}

	public static Component getPlainComponentFromString(String input) {
		if (input.isEmpty()) return Component.empty();

		// Convert legacy color patterns to MiniMessage format, then deserialize it.
		Component component = MiniMessage.miniMessage().deserialize(getMiniMessageFromLegacy(input));

		// Convert mini message component to a plain string and deserialize string to plain component.
		return PlainTextComponentSerializer.plainText().deserialize(PlainTextComponentSerializer.plainText().serialize(component));
	}

	public static Component getMiniMessage(String input) {
		if (input == null) return null;
		if (input.isEmpty()) return Component.empty();

		// Convert legacy color patterns to MiniMessage format, then deserialize it.
		Component component = MiniMessage.miniMessage().deserialize(getMiniMessageFromLegacy(input));

		// Remove italics if they aren't present. Otherwise, item name and lore will render italic text.
		return component.decoration(TextDecoration.ITALIC, component.hasDecoration(TextDecoration.ITALIC));
	}

	public static Component getMiniMessageWithVars(Player player, String input) {
		if (input.isEmpty()) return Component.text("");
		return getMiniMessage(MagicSpells.doReplacements(input, player));
	}

	public static Component getMiniMessageWithArgsAndVars(Player player, String input, String[] args) {
		if (input.isEmpty()) return Component.text("");
		return getMiniMessage(MagicSpells.doReplacements(input, player, args));
	}

	public static String getStringFromComponent(Component component) {
		return component == null ? "" : MiniMessage.miniMessage().serialize(component);
	}

	public static String colorize(String string) {
		if (string.isEmpty()) return "";

		Matcher matcher = ColorUtil.HEX_PATTERN.matcher(ChatColor.translateAlternateColorCodes('&', string));
		StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			String code = matcher.group(1);

			StringBuilder magic = new StringBuilder();
			magic.append(ChatColor.COLOR_CHAR).append('x');
			for (int i = 1; i < code.length(); i++) magic.append(ChatColor.COLOR_CHAR).append(code.charAt(i));

			matcher.appendReplacement(builder, magic.toString());
		}

		return matcher.appendTail(builder).toString();
	}

	public static String decolorize(String string) {
		if (string.isEmpty()) return "";
		return ChatColor.stripColor(colorize(string));
	}

	public static String doVarReplacementAndColorize(Player player, String string) {
		return colorize(MagicSpells.doReplacements(string, player));
	}

	public static void setInventoryTitle(Player player, String title) {
		MagicSpells.getVolatileCodeHandler().setInventoryTitle(player, doVarReplacementAndColorize(player, title));
	}

	public static PlayerProfile setTexture(PlayerProfile profile, String texture, String signature) {
		if (signature == null || signature.isEmpty()) profile.setProperty(new ProfileProperty("textures", texture));
		else profile.setProperty(new ProfileProperty("textures", texture, signature));
		return profile;
	}

	public static String getSkinData(Player player) {
		List<ProfileProperty> skins = player.getPlayerProfile().getProperties().stream().filter(prop -> prop.getName().equals("textures")).collect(Collectors.toList());
		ProfileProperty latestSkin = skins.get(0);
		return "Skin: " + latestSkin.getValue() + "\nSignature: " + latestSkin.getSignature();
	}

	public static void setTexture(SkullMeta meta, String texture, String signature) {
		PlayerProfile profile = meta.getPlayerProfile();
		setTexture(profile, texture, signature);
		meta.setPlayerProfile(profile);
	}

	public static void setSkin(Player player, String skin, String signature) {
		player.setPlayerProfile(setTexture(player.getPlayerProfile(), skin, signature));
	}

	public static void setTexture(SkullMeta meta, String texture, String signature, String uuid, OfflinePlayer offlinePlayer) {
		try {
			PlayerProfile profile;

			if (uuid != null) profile = Bukkit.createProfile(UUID.fromString(uuid), offlinePlayer.getName());
			else profile = Bukkit.createProfile(null, offlinePlayer.getName());

			setTexture(profile, texture, signature);
			meta.setPlayerProfile(profile);
		} catch (SecurityException | IllegalArgumentException e) {
			MagicSpells.handleException(e);
		}
	}

	public static void forEachLivingInLoadedChunks(World world, Consumer<LivingEntity> action) {
		for (Chunk chunk : world.getLoadedChunks()) {
			for (Entity entity : chunk.getEntities()) {
				if (entity instanceof LivingEntity livingEntity) action.accept(livingEntity);
			}
		}
	}

	public static void forEachLivingInLoadedChunks(Consumer<LivingEntity> action) {
		for (World world : Bukkit.getWorlds()) {
			forEachLivingInLoadedChunks(world, action);
		}
	}

	@Nullable
	public static Entity getNearestEntity(Entity entity, double range, @Nullable Predicate<Entity> predicate) {
		Entity nearestEntity = null;
		double nearestDistance = range * range;
		double distance;

		for (Entity nextEntity : entity.getNearbyEntities(range, range, range)) {
			if (predicate != null && !predicate.test(nextEntity)) {
				continue;
			}

			distance = entity.getLocation().distanceSquared(nextEntity.getLocation());

			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearestEntity = nextEntity;
			}
		}
		return nearestEntity;
	}

	public static InventoryView createInventoryView(Player player) {
		return new InventoryView() {
			final CraftingInventory craftingInventory = new MockCraftingInventory();

			@NotNull
			@Override
			public MenuType getMenuType() {
				return MenuType.CRAFTING;
			}

			@NotNull
			@Override
			public Inventory getTopInventory() {
				return craftingInventory;
			}

			@NotNull
			@Override
			public Inventory getBottomInventory() {
				return player.getInventory();
			}

			@NotNull
			@Override
			public HumanEntity getPlayer() {
				return player;
			}

			@NotNull
			@Override
			public InventoryType getType() {
				return InventoryType.CRAFTING;
			}

			@Override
			public void setItem(int i, @Nullable ItemStack itemStack) {

			}

			@Override
			public @Nullable ItemStack getItem(int i) {
				return null;
			}

			@Override
			public void setCursor(@Nullable ItemStack itemStack) {

			}

			@Override
			public @NotNull ItemStack getCursor() {
				return null;
			}

			@Override
			public @Nullable Inventory getInventory(int i) {
				return null;
			}

			@Override
			public int convertSlot(int i) {
				return 0;
			}

			@NotNull
			@Override
			public InventoryType.SlotType getSlotType(int i) {
				return null;
			}

			@Override
			public void close() {

			}

			@Override
			public int countSlots() {
				return 0;
			}

			@Override
			public boolean setProperty(@NotNull Property property, int i) {
				return false;
			}

			@Override
			public @NotNull String getTitle() {
				return "";
			}

			@Override
			public @NotNull String getOriginalTitle() {
				return "";
			}

			@Override
			public void setTitle(@NotNull String title) {

			}

			@Override
			public void open() {
				throw new UnsupportedOperationException("Unimplemented method 'open'");
			}
		};
	}

	private static class MockCraftingInventory implements CraftingInventory {
		ListIterator<ItemStack> listIterator =  new ListIterator<>() {
			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public ItemStack next() {
				return null;
			}

			@Override
			public boolean hasPrevious() {
				return false;
			}

			@Override
			public ItemStack previous() {
				return null;
			}

			@Override
			public int nextIndex() {
				return 0;
			}

			@Override
			public int previousIndex() {
				return 0;
			}

			@Override
			public void remove() {

			}

			@Override
			public void set(ItemStack itemStack) {

			}

			@Override
			public void add(ItemStack itemStack) {

			}
		};

		@Override
		public @Nullable ItemStack getResult() {
			return null;
		}

		@Override
		public @Nullable ItemStack[] getMatrix() {
			return new ItemStack[0];
		}

		@Override
		public void setResult(@Nullable ItemStack newResult) {

		}

		@Override
		public void setMatrix(@Nullable ItemStack[] contents) {

		}

		@Override
		public @Nullable Recipe getRecipe() {
			return null;
		}

		@Override
		public int getSize() {
			return 0;
		}

		@Override
		public int getMaxStackSize() {
			return 0;
		}

		@Override
		public void setMaxStackSize(int size) {

		}

		@Override
		public @Nullable ItemStack getItem(int index) {
			return null;
		}

		@Override
		public void setItem(int index, @Nullable ItemStack item) {

		}

		@Override
		public @NotNull HashMap<Integer, ItemStack> addItem(@NotNull ItemStack... items) throws IllegalArgumentException {
			return new HashMap<>();
		}

		@Override
		public @NotNull HashMap<Integer, ItemStack> removeItem(@NotNull ItemStack... items) throws IllegalArgumentException {
			return new HashMap<>();
		}

		@Override
		public @NotNull HashMap<Integer, ItemStack> removeItemAnySlot(@NotNull ItemStack... items) throws IllegalArgumentException {
			return new HashMap<>();
		}

		@Override
		public @Nullable ItemStack[] getContents() {
			return new ItemStack[0];
		}

		@Override
		public void setContents(@Nullable ItemStack[] items) throws IllegalArgumentException {

		}

		@Override
		public @Nullable ItemStack[] getStorageContents() {
			return new ItemStack[0];
		}

		@Override
		public void setStorageContents(@Nullable ItemStack[] items) throws IllegalArgumentException {

		}

		@Override
		public boolean contains(@NotNull Material material) throws IllegalArgumentException {
			return false;
		}

		@Override
		public boolean contains(@Nullable ItemStack item) {
			return false;
		}

		@Override
		public boolean contains(@NotNull Material material, int amount) throws IllegalArgumentException {
			return false;
		}

		@Override
		public boolean contains(@Nullable ItemStack item, int amount) {
			return false;
		}

		@Override
		public boolean containsAtLeast(@Nullable ItemStack item, int amount) {
			return false;
		}

		@Override
		public @NotNull HashMap<Integer, ? extends ItemStack> all(@NotNull Material material) throws IllegalArgumentException {
			return new HashMap<>();
		}

		@Override
		public @NotNull HashMap<Integer, ? extends ItemStack> all(@Nullable ItemStack item) {
			return new HashMap<>();
		}

		@Override
		public int first(@NotNull Material material) throws IllegalArgumentException {
			return 0;
		}

		@Override
		public int first(@NotNull ItemStack item) {
			return 0;
		}

		@Override
		public int firstEmpty() {
			return 0;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void remove(@NotNull Material material) throws IllegalArgumentException {

		}

		@Override
		public void remove(@NotNull ItemStack item) {

		}

		@Override
		public void clear(int index) {

		}

		@Override
		public void clear() {

		}

		@Override
		public int close() {
			return 0;
		}

		@Override
		public @NotNull List<HumanEntity> getViewers() {
			return List.of();
		}

		@Override
		public @NotNull InventoryType getType() {
			return InventoryType.CRAFTING;
		}

		@Override
		public @Nullable InventoryHolder getHolder() {
			return null;
		}

		@Override
		public @Nullable InventoryHolder getHolder(boolean useSnapshot) {
			return null;
		}

		@Override
		public @NotNull ListIterator<ItemStack> iterator() {
			return listIterator;
		}

		@Override
		public @NotNull ListIterator<ItemStack> iterator(int index) {
			return listIterator;
		}

		@Override
		public @Nullable Location getLocation() {
			return null;
		}
	}

}
