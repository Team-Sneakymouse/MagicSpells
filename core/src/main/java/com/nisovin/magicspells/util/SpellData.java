package com.nisovin.magicspells.util;

import org.jetbrains.annotations.UnknownNullability;

import java.util.Arrays;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

/**
 * Immutable cast-context carrier. Prefer creating a root instance at an initial cast
 * entry point and deriving mid-chain via {@code with*} methods rather than packing
 * loose caster/target/location/power/args.
 */
public record SpellData(Entity caster, Entity target, Location location, Entity recipient,
						float power, String[] args, ItemStack castItem) {

	public static final SpellData NULL = new SpellData(null, null, null, null, 1f, null, null);

	public SpellData {
		location = location == null ? null : location.clone();
		castItem = castItem == null ? null : castItem.clone();
	}

	public SpellData(Entity caster, Entity target, Location location, Entity recipient,
			float power, String[] args) {
		this(caster, target, location, recipient, power, args, null);
	}

	public SpellData(Entity caster, Entity target, Location location, float power, String[] args) {
		this(caster, target, location, null, power, args, null);
	}

	public SpellData(Entity caster, Entity target, Location location, float power) {
		this(caster, target, location, null, power, null, null);
	}

	public SpellData(Entity caster, Entity target, float power, String[] args) {
		this(caster, target, null, null, power, args, null);
	}

	public SpellData(Entity caster, Location location, float power, String[] args) {
		this(caster, null, location, null, power, args, null);
	}

	public SpellData(Entity caster, Entity target, float power) {
		this(caster, target, null, null, power, null, null);
	}

	public SpellData(Entity caster, Location location, float power) {
		this(caster, null, location, null, power, null, null);
	}

	public SpellData(Entity caster, Entity target) {
		this(caster, target, null, null, 1f, null, null);
	}

	public SpellData(Entity caster, Location location) {
		this(caster, null, location, null, 1f, null, null);
	}

	public SpellData(Entity caster, float power, String[] args) {
		this(caster, null, null, null, power, args, null);
	}

	public SpellData(Entity caster, float power) {
		this(caster, null, null, null, power, null, null);
	}

	public SpellData(Entity caster) {
		this(caster, null, null, null, 1f, null, null);
	}

	public Builder builder() {
		return new Builder(this);
	}

	public SpellData invert() {
		Entity newCaster = this.caster;
		Entity newTarget = this.target;

		if (this.caster != null) newTarget = this.caster;
		if (this.target != null) newCaster = this.target;

		return Objects.equals(newCaster, this.caster) && Objects.equals(newTarget, this.target)
			? this
			: new SpellData(newCaster, newTarget, location, recipient, power, args, castItem);
	}

	@Override
	@UnknownNullability
	public Location location() {
		return location == null ? null : location.clone();
	}

	@Override
	@UnknownNullability
	public ItemStack castItem() {
		return castItem == null ? null : castItem.clone();
	}

	@Override
	public Entity recipient() {
		return recipient == null ? caster : recipient;
	}

	public SpellData retarget(Entity target, Location location) {
		return Objects.equals(this.target, target) && Objects.equals(this.location, location)
			? this
			: new SpellData(caster, target, location, recipient, power, args, castItem);
	}

	public SpellData caster(Entity caster) {
		return Objects.equals(this.caster, caster)
			? this
			: new SpellData(caster, target, location, recipient, power, args, castItem);
	}

	public SpellData target(Entity target) {
		return Objects.equals(this.target, target)
			? this
			: new SpellData(caster, target, location, recipient, power, args, castItem);
	}

	public SpellData location(Location location) {
		return Objects.equals(this.location, location)
			? this
			: new SpellData(caster, target, location == null ? null : location.clone(), recipient, power, args, castItem);
	}

	public SpellData recipient(Entity recipient) {
		return Objects.equals(this.recipient, recipient)
			? this
			: new SpellData(caster, target, location, recipient, power, args, castItem);
	}

	public SpellData power(float power) {
		return this.power == power
			? this
			: new SpellData(caster, target, location, recipient, power, args, castItem);
	}

	public SpellData args(String[] args) {
		return Arrays.equals(this.args, args)
			? this
			: new SpellData(caster, target, location, recipient, power, args, castItem);
	}

	public SpellData castItem(ItemStack castItem) {
		return Objects.equals(this.castItem, castItem)
			? this
			: new SpellData(caster, target, location, recipient, power, args, castItem == null ? null : castItem.clone());
	}

	public SpellData noTargeting() {
		return target == null && location == null
			? this
			: new SpellData(caster, null, null, recipient, power, args, castItem);
	}

	public SpellData noTarget() {
		return target == null
			? this
			: new SpellData(caster, null, location, recipient, power, args, castItem);
	}

	public SpellData noLocation() {
		return location == null
			? this
			: new SpellData(caster, target, null, recipient, power, args, castItem);
	}

	public boolean hasCaster() {
		return caster != null;
	}

	public boolean hasTarget() {
		return target != null;
	}

	public boolean hasLocation() {
		return location != null;
	}

	public boolean hasRecipient() {
		return recipient != null || caster != null;
	}

	public boolean hasArgs() {
		return args != null && args.length > 0;
	}

	public boolean hasCastItem() {
		return castItem != null;
	}

	public boolean isValid() {
		return (caster == null || caster.isValid())
			&& (target == null || target.isValid())
			&& (recipient == null || recipient.isValid());
	}

	public static class Builder {

		private Entity caster;
		private Entity recipient;
		private Entity target;
		private Location location;
		private float power;
		private String[] args;
		private ItemStack castItem;

		public Builder() {
			this(SpellData.NULL);
		}

		public Builder(SpellData data) {
			caster = data.caster;
			target = data.target;
			location = data.location;
			recipient = data.recipient;
			power = data.power;
			args = data.args;
			castItem = data.castItem;
		}

		public Builder caster(Entity caster) {
			this.caster = caster;
			return this;
		}

		public Builder target(Entity target) {
			this.target = target;
			return this;
		}

		public Builder location(Location location) {
			this.location = location;
			return this;
		}

		public Builder recipient(Entity recipient) {
			this.recipient = recipient;
			return this;
		}

		public Builder power(float power) {
			this.power = power;
			return this;
		}

		public Builder args(String[] args) {
			this.args = args;
			return this;
		}

		public Builder castItem(ItemStack castItem) {
			this.castItem = castItem;
			return this;
		}

		public SpellData build() {
			return new SpellData(caster, target, location, recipient, power, args, castItem);
		}

	}

}
