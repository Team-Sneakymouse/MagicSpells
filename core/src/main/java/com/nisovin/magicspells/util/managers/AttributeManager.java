package com.nisovin.magicspells.util.managers;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.Collection;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Location;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.AttributeSpec;
import com.nisovin.magicspells.util.AttributeSpecParser;
import com.nisovin.magicspells.util.AttributeUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.handlers.DebugHandler;

public class AttributeManager {

	// add attributes to item meta
	public ItemMeta addMetaAttribute(ItemMeta meta, Attribute attribute, AttributeModifier modifier) {
		if (meta == null)
			throw new NullPointerException("itemMeta");
		if (attribute == null)
			throw new NullPointerException("attribute");
		if (modifier == null)
			throw new NullPointerException("modifier");
		try {
			meta.addAttributeModifier(attribute, modifier);
		} catch (IllegalArgumentException exception) {
			MagicSpells.log("That attribute has already been applied!");
			DebugHandler.debugIllegalArgumentException(exception);
		}
		return meta;
	}

	public ItemMeta addMetaAttribute(ItemMeta meta, AttributeInfo attributeInfo) {
		return addMetaAttribute(meta, attributeInfo.getAttribute(), attributeInfo.getAttributeModifier());
	}

	// add attributes to item stack
	public void addItemAttribute(ItemStack item, Attribute attribute, AttributeModifier modifier) {
		if (item == null)
			throw new NullPointerException("itemStack");
		item.setItemMeta(addMetaAttribute(item.getItemMeta(), attribute, modifier));
	}

	public void addItemAttribute(ItemStack item, AttributeInfo attributeInfo) {
		if (item == null)
			throw new NullPointerException("itemStack");
		item.setItemMeta(addMetaAttribute(item.getItemMeta(), attributeInfo));
	}

	public void addItemAttributes(ItemStack itemStack, Set<AttributeInfo> attributes) {
		attributes.forEach(attributeInfo -> addItemAttribute(itemStack, attributeInfo));
	}

	// add attributes to the living entity
	public void addEntityAttribute(LivingEntity livingEntity, Attribute attribute, AttributeModifier modifier) {
		if (livingEntity == null)
			throw new NullPointerException("livingEntity");
		if (attribute == null)
			throw new NullPointerException("attribute");
		if (modifier == null)
			throw new NullPointerException("modifier");
		if (livingEntity.getAttribute(attribute) == null)
			throw new NullPointerException("inapplicable attribute");
		try {
			livingEntity.getAttribute(attribute).addModifier(modifier);
		} catch (IllegalArgumentException exception) {
			MagicSpells.log("That attribute has already been applied!");
			DebugHandler.debugIllegalArgumentException(exception);
		}
	}

	public void addEntityAttribute(LivingEntity livingEntity, AttributeInfo attributeInfo) {
		addEntityAttribute(livingEntity, attributeInfo.getAttribute(), attributeInfo.getAttributeModifier());
	}

	public void addEntityAttributes(LivingEntity livingEntity, Set<AttributeInfo> attributes) {
		attributes.forEach(attributeInfo -> addEntityAttribute(livingEntity, attributeInfo));
	}

	// has attribute modifier
	public boolean hasEntityAttribute(LivingEntity entity, Attribute attribute, AttributeModifier modifier) {
		return entity.getAttribute(attribute).getModifiers().contains(modifier);
	}

	public boolean hasEntityAttribute(LivingEntity entity, AttributeInfo attributeInfo) {
		return entity.getAttribute(attributeInfo.getAttribute()).getModifiers()
				.contains(attributeInfo.getAttributeModifier());
	}

	// get attribute and attribute modifier from string
	// - [AttributeName] [Number] [Operation]
	public AttributeInfo getAttributeInfo(String str) {
		AttributeSpec spec;
		try {
			spec = AttributeSpecParser.parse(str, true);
		} catch (IllegalArgumentException e) {
			if (e.getCause() instanceof NumberFormatException nfe) {
				DebugHandler.debugNumberFormat(nfe);
			}
			return null;
		}

		Attribute attribute = AttributeUtil.getAttribute(spec.attributeName());
		if (attribute == null) {
			MagicSpells.error("AttributeManager has an invalid attribute defined: " + spec.attributeName());
			return null;
		}

		AttributeModifier.Operation operation = AttributeUtil.getOperation(spec.operationName());
		if (operation == null) {
			MagicSpells.error("AttributeManager has an invalid attribute operation defined: " + spec.operationName());
			return null;
		}

		NamespacedKey key = new NamespacedKey("magicspells",
			AttributeSpecParser.stableKeyPath(spec.attributeName(), 0));
		return new AttributeInfo(attribute, new AttributeModifier(key, spec.amount(), operation));
	}

	/**
	 * Build a cast-time evaluated attribute set.
	 * <p>
	 * Each entry is defined as:
	 *
	 * <pre>
	 * - [AttributeName] [Number] [Operation] [Slot]
	 * </pre>
	 *
	 * Operation defaults to {@code add_number}; slot is optional. All tokens
	 * support variables (and the number token supports math via
	 * {@link ConfigDataUtil#getDouble(String)}).
	 * <p>
	 * Attribute modifier UUIDs are made stable per {@code sourceKey + index} so
	 * that toggle/remove behavior works even when values are resolved at cast-time.
	 */
	public ConfigData<Set<AttributeInfo>> getAttributesConfigData(List<String> attributes, String sourceKey) {
		if (attributes == null || attributes.isEmpty()) {
			return (caster, target, location, power, args) -> null;
		}

		final List<ConfigData<AttributeInfo>> suppliers = new ArrayList<>();
		boolean isConstant = true;

		for (int i = 0; i < attributes.size(); i++) {
			String raw = attributes.get(i);
			if (raw == null || raw.isBlank())
				continue;

			ConfigData<AttributeInfo> supplier = ConfigDataUtil.getAttributeInfo(raw, sourceKey, i);
			suppliers.add(supplier);
			if (isConstant && !supplier.isConstant())
				isConstant = false;
		}

		if (suppliers.isEmpty())
			return (caster, target, location, power, args) -> null;

		if (isConstant) {
			Set<AttributeInfo> resolved = new HashSet<>();
			for (ConfigData<AttributeInfo> supplier : suppliers) {
				AttributeInfo info = supplier.get(null, null, 1f, null);
				if (info != null)
					resolved.add(info);
			}
			if (resolved.isEmpty())
				return (caster, target, location, power, args) -> null;
			return (caster, target, location, power, args) -> resolved;
		}

		return new ConfigData<>() {
			@Override
			public Set<AttributeInfo> get(Entity caster, Entity target, Location location, float power,
					String[] args) {
				Set<AttributeInfo> resolved = new HashSet<>();
				for (ConfigData<AttributeInfo> supplier : suppliers) {
					AttributeInfo info = supplier.get(caster, target, power, args);
					if (info != null)
						resolved.add(info);
				}
				return resolved.isEmpty() ? null : resolved;
			}

			@Override
			public boolean isConstant() {
				return false;
			}
		};
	}

	public Set<AttributeInfo> getAttributesConfigData(List<String> attributes, String sourceKey, SpellData data) {
		return getAttributesConfigData(attributes, sourceKey).get(data);
	}

	// get attribute info from string list
	public Set<AttributeInfo> getAttributes(List<String> attributes) {
		if (attributes == null || attributes.isEmpty())
			return null;
		Set<AttributeInfo> attributeMap = new HashSet<>();

		for (String str : attributes) {
			AttributeInfo attributeInfo = getAttributeInfo(str);
			if (attributeInfo == null)
				continue;
			attributeMap.add(attributeInfo);
		}

		return attributeMap;
	}

	// clear meta attributes
	public ItemMeta removeMetaAttributeModifier(ItemMeta meta, Attribute attribute, AttributeModifier modifier) {
		if (meta == null)
			throw new NullPointerException("itemMeta");
		if (attribute == null)
			throw new NullPointerException("attribute");
		if (modifier == null)
			throw new NullPointerException("modifier");
		meta.removeAttributeModifier(attribute, modifier);
		return meta;
	}

	public ItemMeta clearMetaAttributeModifiers(ItemMeta meta, Attribute attribute) {
		if (meta == null)
			throw new NullPointerException("itemMeta");
		if (attribute == null)
			throw new NullPointerException("attribute");
		Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
		modifiers.forEach(modifier -> meta.removeAttributeModifier(attribute, modifier));
		return meta;
	}

	public ItemMeta clearMetaAttributeModifiers(ItemMeta meta, Set<AttributeInfo> attributeInfos) {
		for (AttributeInfo attributeInfo : attributeInfos) {
			meta = removeMetaAttributeModifier(meta, attributeInfo.getAttribute(),
					attributeInfo.getAttributeModifier());
		}
		return meta;
	}

	// clear item stack attributes
	public void removeItemAttributeModifier(ItemStack item, Attribute attribute, AttributeModifier modifier) {
		item.setItemMeta(removeMetaAttributeModifier(item.getItemMeta(), attribute, modifier));
	}

	public void clearItemAttributeModifiers(ItemStack item, Attribute attribute) {
		item.setItemMeta(clearMetaAttributeModifiers(item.getItemMeta(), attribute));
	}

	public void clearItemAttributeModifiers(ItemStack item, Set<AttributeInfo> attributeInfos) {
		item.setItemMeta(clearMetaAttributeModifiers(item.getItemMeta(), attributeInfos));
	}

	// clear entity attributes
	public void removeEntityAttributeModifier(LivingEntity livingEntity, Attribute attribute,
			AttributeModifier modifier) {
		if (livingEntity == null)
			throw new NullPointerException("livingEntity");
		if (attribute == null)
			throw new NullPointerException("attribute");
		if (modifier == null)
			throw new NullPointerException("modifier");
		livingEntity.getAttribute(attribute).removeModifier(modifier);
	}

	public void clearEntityAttributeModifiers(LivingEntity livingEntity, Attribute attribute) {
		if (livingEntity == null)
			throw new NullPointerException("livingEntity");
		if (attribute == null)
			throw new NullPointerException("attribute");
		Collection<AttributeModifier> modifiers = livingEntity.getAttribute(attribute).getModifiers();
		modifiers.forEach(modifier -> livingEntity.getAttribute(attribute).removeModifier(modifier));
	}

	public void clearEntityAttributeModifiers(LivingEntity livingEntity, Set<AttributeInfo> attributeInfos) {
		attributeInfos.forEach(attributeInfo -> removeEntityAttributeModifier(livingEntity,
				attributeInfo.getAttribute(), attributeInfo.getAttributeModifier()));
	}

	public static class AttributeInfo {

		private Attribute attribute;
		private AttributeModifier attributeModifier;

		public AttributeInfo(Attribute attribute, AttributeModifier attributeModifier) {
			this.attribute = attribute;
			this.attributeModifier = attributeModifier;
		}

		public Attribute getAttribute() {
			return attribute;
		}

		public void setAttribute(Attribute attribute) {
			this.attribute = attribute;
		}

		public AttributeModifier getAttributeModifier() {
			return attributeModifier;
		}

		public void setAttributeModifier(AttributeModifier attributeModifier) {
			this.attributeModifier = attributeModifier;
		}

	}

}
