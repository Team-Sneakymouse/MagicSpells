package com.nisovin.magicspells.util;

import com.nisovin.magicspells.MagicSpells;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

import org.joml.Vector3f;
import org.joml.Quaternionf;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import java.util.function.Consumer;
import org.bukkit.util.EulerAngle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.config.ConfigDataUtil;

public class EntityData {

	private final Multimap<EntityType, Transformer<?, ?>> options = MultimapBuilder.enumKeys(EntityType.class)
			.arrayListValues().build();

	private ConfigData<EntityType> entityType;

	private final ConfigData<BlockData> fallingBlockData;
	private final ConfigData<Material> dropItemMaterial;
	private final ConfigData<Vector> relativeOffset;

	// Legacy support for DisguiseSpell section format

	// Ageable
	private final ConfigData<Boolean> baby;

	// AbstractHorse & Pig
	private final ConfigData<Boolean> saddled;

	// ChestedHorse
	private final ConfigData<Boolean> chested;

	// Creeper
	private final ConfigData<Boolean> powered;

	// Enderman
	private final ConfigData<BlockData> carriedBlockData;

	// Horse
	private final ConfigData<Horse.Color> horseColor;
	private final ConfigData<Horse.Style> horseStyle;

	// Llama
	private final ConfigData<Llama.Color> llamaColor;

	// Parrot
	private final ConfigData<Parrot.Variant> parrotVariant;

	// Puffer Fish & Slime
	private final ConfigData<Integer> size;

	// Sheep
	private final ConfigData<Boolean> sheared;

	// Sheep & Tropical Fish & Wolf
	private final ConfigData<DyeColor> color;

	// Tameable
	private final ConfigData<Boolean> tamed;

	// Tropical Fish
	private final ConfigData<TropicalFish.Pattern> tropicalFishPattern;
	private final ConfigData<DyeColor> tropicalFishPatternColor;

	// Villager
	private final ConfigData<Villager.Profession> profession;

	// Display
	private final List<DisplayTransformation> transformations = new ArrayList<>();

	// Item Display
	private final ConfigData<MagicItem> item;

	public EntityData(ConfigurationSection config) {
		entityType = ConfigDataUtil.getEnum(config, "entity", EntityType.class, null);

		relativeOffset = ConfigDataUtil.getVector(config, "relative-offset", new Vector(0, 0, 0));

		Multimap<Class<?>, Transformer<?, ?>> transformers = MultimapBuilder.linkedHashKeys().arrayListValues().build();

		addOptBoolean(transformers, config, "glowing", Entity.class, Entity::setGlowing);
		addOptBoolean(transformers, config, "visible-by-default", Entity.class, Entity::setVisibleByDefault);

		// Ageable
		baby = addBoolean(transformers, config, "baby", false, Ageable.class, (ageable, baby) -> {
			if (baby)
				ageable.setBaby();
			else
				ageable.setAdult();
		});

		addOptInteger(transformers, config, "age", Ageable.class, Ageable::setAge);

		// Tameable
		tamed = addBoolean(transformers, config, "tamed", false, Tameable.class, Tameable::setTamed);

		// AbstractHorse
		saddled = addBoolean(transformers, config, "saddled", false, AbstractHorse.class, (horse, saddled) -> {
			if (saddled)
				horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
		});

		// Armor Stand
		addBoolean(transformers, config, "small", false, ArmorStand.class, ArmorStand::setSmall);
		addBoolean(transformers, config, "marker", false, ArmorStand.class, ArmorStand::setMarker);
		addBoolean(transformers, config, "visible", true, ArmorStand.class, ArmorStand::setVisible);
		addBoolean(transformers, config, "has-arms", true, ArmorStand.class, ArmorStand::setArms);
		addBoolean(transformers, config, "has-base-plate", true, ArmorStand.class, ArmorStand::setBasePlate);

		EulerAngle def = new EulerAngle(0, 0, 0);
		addEulerAngle(transformers, config, "head-angle", def, ArmorStand.class, ArmorStand::setHeadPose);
		addEulerAngle(transformers, config, "body-angle", def, ArmorStand.class, ArmorStand::setBodyPose);
		addEulerAngle(transformers, config, "left-arm-angle", def, ArmorStand.class, ArmorStand::setLeftArmPose);
		addEulerAngle(transformers, config, "right-arm-angle", def, ArmorStand.class, ArmorStand::setRightArmPose);
		addEulerAngle(transformers, config, "left-leg-angle", def, ArmorStand.class, ArmorStand::setLeftLegPose);
		addEulerAngle(transformers, config, "right-leg-angle", def, ArmorStand.class, ArmorStand::setRightLegPose);

		// Axolotl
		addOptEnum(transformers, config, "type", Axolotl.class, Axolotl.Variant.class, Axolotl::setVariant);

		// Cat
		addOptRegistryEntry(transformers, config, "type", Cat.class, RegistryKey.CAT_VARIANT, Cat::setCatType);

		// ChestedHorse
		chested = addBoolean(transformers, config, "chested", false, ChestedHorse.class,
				ChestedHorse::setCarryingChest);

		// Creeper
		powered = addBoolean(transformers, config, "powered", false, Creeper.class, Creeper::setPowered);

		// Dropped Item
		dropItemMaterial = ConfigDataUtil.getMaterial(config, "material", null);

		// Enderman
		carriedBlockData = addBlockData(transformers, config, "material", null, Enderman.class,
				Enderman::setCarriedBlock);

		// Falling Block
		fallingBlockData = ConfigDataUtil.getBlockData(config, "material", null);

		// Fox
		addOptEnum(transformers, config, "type", Fox.class, Fox.Type.class, Fox::setFoxType);

		// Frog
		addOptRegistryEntry(transformers, config, "type", Frog.class, RegistryKey.FROG_VARIANT, Frog::setVariant);

		// Horse
		horseColor = addOptEnum(transformers, config, "color", Horse.class, Horse.Color.class, Horse::setColor);
		horseStyle = addOptEnum(transformers, config, "style", Horse.class, Horse.Style.class, Horse::setStyle);

		// Llama
		llamaColor = addOptEnum(transformers, config, "color", Llama.class, Llama.Color.class, Llama::setColor);
		addOptMaterial(transformers, config, "material", Llama.class,
				(llama, material) -> llama.getInventory().setDecor(new ItemStack(material)));

		// Mushroom Cow
		addOptEnum(transformers, config, "type", MushroomCow.class, MushroomCow.Variant.class, MushroomCow::setVariant);

		// Panda
		addOptEnum(transformers, config, "main-gene", Panda.class, Panda.Gene.class, Panda::setMainGene);
		addOptEnum(transformers, config, "hidden-gene", Panda.class, Panda.Gene.class, Panda::setHiddenGene);

		// Parrot
		parrotVariant = addOptEnum(transformers, config, "type", Parrot.class, Parrot.Variant.class,
				Parrot::setVariant);

		// Phantom
		addInteger(transformers, config, "size", 0, Phantom.class, Phantom::setSize);

		// Puffer Fish
		size = addInteger(transformers, config, "size", 0, PufferFish.class, PufferFish::setPuffState);

		// Rabbit
		addOptEnum(transformers, config, "type", Rabbit.class, Rabbit.Type.class, Rabbit::setRabbitType);

		// Sheep
		sheared = addBoolean(transformers, config, "sheared", false, Sheep.class, Sheep::setSheared);
		color = addOptEnum(transformers, config, "color", Sheep.class, DyeColor.class, Sheep::setColor);

		// Slime
		addInteger(transformers, config, "size", 0, Slime.class, Slime::setSize);

		// Steerable
		addBoolean(transformers, config, "saddled", false, Steerable.class, Steerable::setSaddle);

		// Tropical Fish
		addOptEnum(transformers, config, "color", TropicalFish.class, DyeColor.class, TropicalFish::setBodyColor);
		tropicalFishPatternColor = addOptEnum(transformers, config, "pattern-color", TropicalFish.class, DyeColor.class,
				TropicalFish::setPatternColor);
		tropicalFishPattern = addOptEnum(transformers, config, "type", TropicalFish.class, TropicalFish.Pattern.class,
				TropicalFish::setPattern);

		// Villager
		profession = addOptRegistryEntry(transformers, config, "type", Villager.class, Registry.VILLAGER_PROFESSION,
				Villager::setProfession);

		// Wolf
		addBoolean(transformers, config, "angry", false, Wolf.class, Wolf::setAngry);
		addOptEnum(transformers, config, "color", Wolf.class, DyeColor.class, Wolf::setCollarColor);

		if (EntityType.valueOf("BLOCK_DISPLAY") != null) {

			for (int i = 0; i < Integer.MAX_VALUE; i++) {
				ConfigurationSection transConf = config
						.getConfigurationSection("transformation" + ((i == 0) ? "" : "-" + (i + 1)));

				if (transConf == null)
					break;

				ConfigData<Integer> interpolationDelay = ConfigDataUtil.getInteger(transConf, "interpolation-delay", 0);
				ConfigData<Integer> interpolationDuration = ConfigDataUtil.getInteger(transConf,
						"interpolation-duration", 0);
				ConfigData<Integer> loopInterval = ConfigDataUtil.getInteger(transConf, "loop-interval", 0);

				// Display
				ConfigData<Quaternionf> leftRotation = ConfigDataUtil.getQuaternionf(transConf, "left-rotation",
						new Quaternionf(0, 0, 0, 1));
				ConfigData<Quaternionf> rightRotation = ConfigDataUtil.getQuaternionf(transConf, "right-rotation",
						new Quaternionf(0, 0, 0, 1));
				ConfigData<Vector3f> translation = ConfigDataUtil.getVector3f(transConf, "translation",
						new Vector3f(0, 0, 0));
				ConfigData<Vector3f> scale = ConfigDataUtil.getVector3f(transConf, "scale", new Vector3f(1, 1, 1));

				ConfigData<Transformation> transformation;
				if (leftRotation.isConstant() && rightRotation.isConstant() && translation.isConstant()
						&& scale.isConstant()) {
					Quaternionf lr = leftRotation.get(null);
					Quaternionf rr = rightRotation.get(null);
					Vector3f t = translation.get(null);
					Vector3f s = scale.get(null);

					Transformation transform = new Transformation(t, lr, s, rr);
					transformation = (caster, target, location, power, args) -> transform;
				} else {
					transformation = (caster, target, location, power, args) -> {
						Quaternionf lr = leftRotation.get(caster, target, location, power, args);
						Quaternionf rr = rightRotation.get(caster, target, location, power, args);
						Vector3f t = translation.get(caster, target, location, power, args);
						Vector3f s = scale.get(caster, target, location, power, args);

						return new Transformation(t, lr, s, rr);
					};
				}
				transformations.add(new DisplayTransformation(transformation, interpolationDelay, interpolationDuration,
						loopInterval));
			}

			addOptFloat(transformers, config, "view-range", Display.class, Display::setViewRange);
			addOptFloat(transformers, config, "shadow-radius", Display.class, Display::setShadowRadius);
			addOptFloat(transformers, config, "shadow-strength", Display.class, Display::setShadowStrength);
			addOptFloat(transformers, config, "width", Display.class, Display::setDisplayWidth);
			addOptFloat(transformers, config, "height", Display.class, Display::setDisplayHeight);
			addOptEnum(transformers, config, "billboard", Display.class, Display.Billboard.class,
					Display::setBillboard);
			addOptARGBColor(transformers, config, "glow-color-override", Display.class, Display::setGlowColorOverride);

			ConfigData<Integer> blockLight = ConfigDataUtil.getInteger(config, "brightness.block");
			ConfigData<Integer> skyLight = ConfigDataUtil.getInteger(config, "brightness.sky");
			ConfigData<Display.Brightness> brightness = (caster, target, location, power, args) -> null;
			if (checkNull(blockLight) && checkNull(skyLight)) {
				if (blockLight.isConstant() && skyLight.isConstant()) {
					int bl = blockLight.get(null);
					int sl = skyLight.get(null);

					if (0 <= bl && bl <= 15 && 0 <= sl && sl <= 15) {
						Display.Brightness b = new Display.Brightness(bl, sl);
						brightness = (caster, target, location, power, args) -> b;
					}
				} else {
					brightness = (caster, target, location, power, args) -> {
						Integer bl = blockLight.get(caster, target, location, power, args);
						if (bl == null || bl < 0 || bl > 15)
							return null;

						Integer sl = skyLight.get(caster, target, location, power, args);
						if (sl == null || sl < 0 || sl > 15)
							return null;

						return new Display.Brightness(bl, sl);
					};
				}
			}
			transformers.put(Display.class, new Transformer<>(brightness, Display::setBrightness, true));

			// BlockDisplay
			addOptBlockData(transformers, config, "block", BlockDisplay.class, BlockDisplay::setBlock);

			// ItemDisplay
			item = ConfigDataUtil.getMagicItem(config, "item", null);
			addOptEnum(transformers, config, "item-display-transform", ItemDisplay.class,
					ItemDisplay.ItemDisplayTransform.class, ItemDisplay::setItemDisplayTransform);

			// TextDisplay
			addOptComponent(transformers, config, "text", TextDisplay.class, TextDisplay::text);
			addOptInteger(transformers, config, "line-width", TextDisplay.class, TextDisplay::setLineWidth);
			addOptARGBColor(transformers, config, "background", TextDisplay.class, TextDisplay::setBackgroundColor);
			addOptByte(transformers, config, "text-opacity", TextDisplay.class, TextDisplay::setTextOpacity);
			addOptBoolean(transformers, config, "shadow", TextDisplay.class, TextDisplay::setShadowed);
			addOptBoolean(transformers, config, "see-through", TextDisplay.class, TextDisplay::setSeeThrough);
			addOptBoolean(transformers, config, "default-background", TextDisplay.class,
					TextDisplay::setDefaultBackground);
			addOptEnum(transformers, config, "alignment", TextDisplay.class, TextDisplay.TextAlignment.class,
					TextDisplay::setAlignment);
		} else {
			item = null;
		}

		for (EntityType entityType : EntityType.values()) {
			Class<? extends Entity> entityClass = entityType.getEntityClass();
			if (entityClass == null)
				continue;

			for (Class<?> transformerType : transformers.keys())
				if (transformerType.isAssignableFrom(entityClass))
					options.putAll(entityType, transformers.get(transformerType));
		}
	}

	@Nullable
	public Entity spawn(@NotNull Location location) {
		return spawn(location, null, null);
	}

	@Nullable
	public Entity spawn(@NotNull Location location, @Nullable Consumer<Entity> consumer) {
		return spawn(location, null, consumer);
	}

	@Nullable
	public Entity spawn(@NotNull Location location, @Nullable SpellData data, @Nullable Consumer<Entity> consumer) {
		Location startLoc = location.clone();
		Vector dir = startLoc.getDirection().normalize();
		Vector relativeOffset = this.relativeOffset.get(data);

		Vector horizOffset = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
		startLoc.add(horizOffset.multiply(relativeOffset.getZ())).getBlock().getLocation();
		startLoc.add(startLoc.getDirection().clone().multiply(relativeOffset.getX()));
		startLoc.setY(startLoc.getY() + relativeOffset.getY());

		EntityType entityType = this.entityType.get(data);
		if (entityType == null || (!entityType.isSpawnable() && entityType != EntityType.FALLING_BLOCK
				&& entityType != EntityType.ITEM))
			return null;

		boolean[] displayHack = new boolean[] { false, false };
		Entity entity;
		try {
			entity = switch (entityType) {
				case FALLING_BLOCK -> {
					BlockData blockData = fallingBlockData.get(data);
					if (blockData == null)
						yield null;

					Entity e = startLoc.getWorld().spawn(startLoc, FallingBlock.class, fb -> fb.setBlockData(blockData));
					if (consumer != null)
						consumer.accept(e);

					yield e;
				}
				case ITEM -> {
					Material material = dropItemMaterial.get(data);
					if (material == null)
						yield null;

					Entity e = startLoc.getWorld().dropItem(startLoc, new ItemStack(material));
					if (consumer != null)
						consumer.accept(e);

					yield e;
				}
				default -> {
					Class<? extends Entity> entityClass = entityType.getEntityClass();
					if (entityClass == null)
						yield null;

					// Paper throws IllegalStateException for monsters in Peaceful; catch below.
					yield startLoc.getWorld().spawn(startLoc, entityClass, e -> {
						Collection<Transformer<?, ?>> transformers = options.get(entityType);
						// noinspection rawtypes
						for (Transformer transformer : transformers)
							// noinspection unchecked
							transformer.apply(e, data);

						if (consumer != null)
							consumer.accept(e);

						if (EntityType.valueOf("ITEM_DISPLAY") != null && e instanceof ItemDisplay) {
							MagicItem magicItem = item.get(data);
							if (magicItem != null)
								((ItemDisplay) e).setItemStack(magicItem.getItemStack());
						}

						if (EntityType.valueOf("BLOCK_DISPLAY") != null && e instanceof Display) {
							displayHack[0] = true;
							displayHack[1] = e.isVisibleByDefault();

							e.setVisibleByDefault(false);
						}
					});
				}
			};
		} catch (IllegalStateException e) {
			MagicSpells.error("Failed to spawn " + entityType + " at " + startLoc.getWorld().getName()
				+ " (" + e.getMessage() + ")");
			return null;
		}

		if (entity == null)
			return null;

		if (displayHack[0]) {
			entity.teleport(startLoc);
			entity.setVisibleByDefault(displayHack[1]);

			transformations.forEach(transformation -> {
				ConfigData<Transformation> transformationData = transformation.transformation();
				int interpolationDelay = transformation.interpolationDelay().get(data);
				int interpolationDuration = transformation.interpolationDuration().get(data);
				int loopInterval = transformation.loopInterval().get(data);

				if (interpolationDelay > 0 || loopInterval > 0) {
					TransformationRunnable transformationRunnable = new TransformationRunnable((Display) entity,
							transformationData, interpolationDuration, data);

					if (loopInterval > 0) {
						transformationRunnable.task = transformationRunnable.runTaskTimer(MagicSpells.getInstance(),
								interpolationDelay, loopInterval);
					} else {
						transformationRunnable.runTaskLater(MagicSpells.getInstance(), interpolationDelay);
					}
				} else {
					((Display) entity).setInterpolationDuration(interpolationDuration);
					((Display) entity).setInterpolationDelay(interpolationDelay);
					((Display) entity).setTransformation(transformationData.get(data));
				}
			});
		}

		return entity;
	}

	private <T> ConfigData<Boolean> addBoolean(Multimap<Class<?>, Transformer<?, ?>> transformers,
			ConfigurationSection config, String name, boolean def, Class<T> type, BiConsumer<T, Boolean> setter) {
		ConfigData<Boolean> supplier = ConfigDataUtil.getBoolean(config, name, def);
		transformers.put(type, new Transformer<>(supplier, setter));

		return supplier;
	}

	private <T> ConfigData<Integer> addInteger(Multimap<Class<?>, Transformer<?, ?>> transformers,
			ConfigurationSection config, String name, int def, Class<T> type, BiConsumer<T, Integer> setter) {
		ConfigData<Integer> supplier = ConfigDataUtil.getInteger(config, name, def);
		transformers.put(type, new Transformer<>(supplier, setter));

		return supplier;
	}

	private <T> ConfigData<BlockData> addBlockData(Multimap<Class<?>, Transformer<?, ?>> transformers,
			ConfigurationSection config, String name, BlockData def, Class<T> type, BiConsumer<T, BlockData> setter) {
		ConfigData<BlockData> supplier = ConfigDataUtil.getBlockData(config, name, def);
		transformers.put(type, new Transformer<>(supplier, setter));

		return supplier;
	}

	private <T> void addEulerAngle(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, EulerAngle def, Class<T> type, BiConsumer<T, EulerAngle> setter) {
		ConfigData<EulerAngle> supplier = ConfigDataUtil.getEulerAngle(config, name, def);
		transformers.put(type, new Transformer<>(supplier, setter));
	}

	private <T> void addOptBoolean(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, Boolean> setter) {
		ConfigData<Boolean> supplier = ConfigDataUtil.getBoolean(config, name);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T> void addOptByte(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, Byte> setter) {
		ConfigData<Byte> supplier = ConfigDataUtil.getByte(config, name);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T> void addOptInteger(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, Integer> setter) {
		ConfigData<Integer> supplier = ConfigDataUtil.getInteger(config, name);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T> void addOptFloat(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, Float> setter) {
		ConfigData<Float> supplier = ConfigDataUtil.getFloat(config, name);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T, E extends Enum<E>> ConfigData<E> addOptEnum(Multimap<Class<?>, Transformer<?, ?>> transformers,
			ConfigurationSection config, String name, Class<T> type, Class<E> enumType, BiConsumer<T, E> setter) {
		ConfigData<E> supplier = ConfigDataUtil.getEnum(config, name, enumType, null);
		transformers.put(type, new Transformer<>(supplier, setter, true));

		return supplier;
	}

	private <T> void addOptMaterial(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, Material> setter) {
		ConfigData<Material> supplier = ConfigDataUtil.getMaterial(config, name, null);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T> void addOptARGBColor(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, Color> setter) {
		ConfigData<Color> supplier = ConfigDataUtil.getARGBColor(config, name, null);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T> void addOptComponent(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, Component> setter) {
		ConfigData<Component> supplier = ConfigDataUtil.getComponent(config, name, null);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T> void addOptBlockData(Multimap<Class<?>, Transformer<?, ?>> transformers, ConfigurationSection config,
			String name, Class<T> type, BiConsumer<T, BlockData> setter) {
		ConfigData<BlockData> supplier = ConfigDataUtil.getBlockData(config, name, null);
		transformers.put(type, new Transformer<>(supplier, setter, true));
	}

	private <T, R extends Keyed> void addOptRegistryEntry(Multimap<Class<?>, Transformer<?, ?>> transformers,
			ConfigurationSection config, String name, Class<T> type, RegistryKey<R> key, BiConsumer<T, R> setter) {
		addOptRegistryEntry(transformers, config, name, type, RegistryAccess.registryAccess().getRegistry(key), setter);
	}

	private <T, R extends Keyed> ConfigData<R> addOptRegistryEntry(Multimap<Class<?>, Transformer<?, ?>> transformers,
			ConfigurationSection config, String name, Class<T> type, Registry<R> registry, BiConsumer<T, R> setter) {
		ConfigData<R> supplier = ConfigDataUtil.getRegistryEntry(config, name, registry, null);
		transformers.put(type, new Transformer<>(supplier, setter, true));

		return supplier;
	}

	private boolean checkNull(ConfigData<?> data) {
		return !data.isConstant() || data.get(null) != null;
	}

	public Multimap<EntityType, Transformer<?, ?>> getOptions() {
		return options;
	}

	public ConfigData<EntityType> getEntityType() {
		return entityType;
	}

	public ConfigData<Vector> getRelativeOffset() {
		return relativeOffset;
	}

	public void setEntityType(ConfigData<EntityType> entityType) {
		this.entityType = entityType;
	}

	@ApiStatus.Internal
	public ConfigData<Material> getDroppedItemStack() {
		return dropItemMaterial;
	}

	@ApiStatus.Internal
	public ConfigData<BlockData> getFallingBlockData() {
		return fallingBlockData;
	}

	@ApiStatus.Internal
	public ConfigData<Boolean> getBaby() {
		return baby;
	}

	@ApiStatus.Internal
	public ConfigData<Boolean> getChested() {
		return chested;
	}

	@ApiStatus.Internal
	public ConfigData<Boolean> getPowered() {
		return powered;
	}

	@ApiStatus.Internal
	public ConfigData<BlockData> getCarriedBlockData() {
		return carriedBlockData;
	}

	@ApiStatus.Internal
	public ConfigData<Horse.Color> getHorseColor() {
		return horseColor;
	}

	@ApiStatus.Internal
	public ConfigData<Horse.Style> getHorseStyle() {
		return horseStyle;
	}

	@ApiStatus.Internal
	public ConfigData<Boolean> getSaddled() {
		return saddled;
	}

	@ApiStatus.Internal
	public ConfigData<Llama.Color> getLlamaColor() {
		return llamaColor;
	}

	@ApiStatus.Internal
	public ConfigData<Parrot.Variant> getParrotVariant() {
		return parrotVariant;
	}

	@ApiStatus.Internal
	public ConfigData<Integer> getSize() {
		return size;
	}

	@ApiStatus.Internal
	public ConfigData<Boolean> getSheared() {
		return sheared;
	}

	@ApiStatus.Internal
	public ConfigData<DyeColor> getColor() {
		return color;
	}

	@ApiStatus.Internal
	public ConfigData<Boolean> getTamed() {
		return tamed;
	}

	@ApiStatus.Internal
	public ConfigData<TropicalFish.Pattern> getTropicalFishPattern() {
		return tropicalFishPattern;
	}

	@ApiStatus.Internal
	public ConfigData<DyeColor> getTropicalFishPatternColor() {
		return tropicalFishPatternColor;
	}

	@ApiStatus.Internal
	public ConfigData<Villager.Profession> getProfession() {
		return profession;
	}

	public record Transformer<T, C>(ConfigData<C> supplier, BiConsumer<T, C> setter, boolean optional) {

		public Transformer(ConfigData<C> supplier, BiConsumer<T, C> setter) {
			this(supplier, setter, false);
		}

		public void apply(T entity, SpellData data) {
			C value = supplier.get(data);
			if (!optional || value != null)
				setter.accept(entity, value);
		}

	}

}

record DisplayTransformation(
		ConfigData<Transformation> transformation,
		ConfigData<Integer> interpolationDelay,
		ConfigData<Integer> interpolationDuration,
		ConfigData<Integer> loopInterval) {
}

class TransformationRunnable extends BukkitRunnable {

	private final Display entity;
	private final ConfigData<Transformation> transformation;
	private final int interpolationDuration;
	private final SpellData data;
	BukkitTask task = null;

	public TransformationRunnable(Display entity, ConfigData<Transformation> transformation,
			int interpolationDuration, SpellData data) {
		this.entity = entity;
		this.transformation = transformation;
		this.interpolationDuration = interpolationDuration;
		this.data = data;
	}

	@Override
	public void run() {
		if (entity == null || !entity.isValid()) {
			if (task != null)
				task.cancel();
		} else {
			Transformation t = transformation.get(data);
			if (t != null) {
				entity.setInterpolationDuration(interpolationDuration);
				entity.setInterpolationDelay(0);
				entity.setTransformation(t);
			}
		}
	}

}