package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.destroystokyo.paper.entity.ai.MobGoals;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;
import org.bukkit.block.BlockFace;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import net.kyori.adventure.text.Component;

import org.apache.commons.math4.core.jdkmath.JdkMath;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MobUtil;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.EntityData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.ai.LookAtEntityGoal;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.util.managers.AttributeManager;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class SpawnEntitySpell extends TargetedSpell implements TargetedLocationSpell, TargetedEntityFromLocationSpell {

	private List<LivingEntity> entities;
	private final Map<LivingEntity, EntityPulser> pulsers;
	private final Map<UUID, List<LivingEntity>> toggledEntities;
	private static int totalEntities = 0;

	private EntityData entityData;

	private ItemStack mainHandItem;
	private ItemStack offHandItem;
	private ItemStack helmet;
	private ItemStack chestplate;
	private ItemStack leggings;
	private ItemStack boots;

	private ConfigData<Float> mainHandItemDropChance;
	private ConfigData<Float> offHandItemDropChance;
	private ConfigData<Float> helmetDropChance;
	private ConfigData<Float> chestplateDropChance;
	private ConfigData<Float> leggingsDropChance;
	private ConfigData<Float> bootsDropChance;
	private ConfigData<Float> yOffset;

	private ConfigData<Integer> duration;
	private ConfigData<Integer> fireTicks;
	private ConfigData<Integer> targetPriorityLimit;
	private ConfigData<Integer> targetInterval;
	private final int spellInterval;

	private ConfigData<Double> targetRange;
	private ConfigData<Double> targetPriorityRange;
	private ConfigData<Double> retargetRange;

	private ConfigData<Boolean> toggle;

	private String location;

	private Component nameplateText;

	private boolean noAI;
	private boolean gravity;
	private boolean removeAI;
	private boolean setOwner;
	private boolean removeMob;
	private boolean invulnerable;
	private boolean useCasterName;
	private boolean addLookAtPlayerAI;
	private boolean allowSpawnInMidair;
	private boolean cancelAttack;
	private boolean synchroniseIntervalSpells;

	private Subspell attackSpell;
	private String attackSpellName;

	private Subspell spellOnSpawn;
	private String spellOnSpawnName;

	private Subspell spellOnDeath;
	private String spellOnDeathName;

	private Subspell intervalSpell;
	private String intervalSpellName;

	private List<PotionEffect> potionEffects;
	private ConfigData<Set<AttributeManager.AttributeInfo>> attributes;

	private Random random = ThreadLocalRandom.current();

	private final EntityPulserTicker ticker;

	private Set<String> mountList;
	private boolean removeMountsOnAnyDeath;

	// DEBUG INFO: level 2, invalid potion effect on internalname spell data
	public SpawnEntitySpell(MagicConfig config, String spellName) {
		super(config, spellName);

		entities = new ArrayList<>();

		ConfigurationSection entitySection = getConfigSection("entity");
		if (entitySection != null)
			entityData = new EntityData(entitySection);

		// Equipment
		MagicItem magicMainHandItem = MagicItems.getMagicItemFromString(getConfigString("main-hand", ""));
		if (magicMainHandItem != null) {
			mainHandItem = magicMainHandItem.getItemStack();
			if (mainHandItem != null && BlockUtils.isAir(mainHandItem.getType()))
				mainHandItem = null;
		}

		MagicItem magicOffHandItem = MagicItems.getMagicItemFromString(getConfigString("off-hand", ""));
		if (magicOffHandItem != null) {
			offHandItem = magicOffHandItem.getItemStack();
			if (offHandItem != null && BlockUtils.isAir(offHandItem.getType()))
				offHandItem = null;
		}

		MagicItem magicHelmetItem = MagicItems.getMagicItemFromString(getConfigString("helmet", ""));
		if (magicHelmetItem != null) {
			helmet = magicHelmetItem.getItemStack();
			if (helmet != null && BlockUtils.isAir(helmet.getType()))
				helmet = null;
		}

		MagicItem magicChestplateItem = MagicItems.getMagicItemFromString(getConfigString("chestplate", ""));
		if (magicChestplateItem != null) {
			chestplate = magicChestplateItem.getItemStack();
			if (chestplate != null && BlockUtils.isAir(chestplate.getType()))
				chestplate = null;
		}

		MagicItem magicLeggingsItem = MagicItems.getMagicItemFromString(getConfigString("leggings", ""));
		if (magicLeggingsItem != null) {
			leggings = magicLeggingsItem.getItemStack();
			if (leggings != null && BlockUtils.isAir(leggings.getType()))
				leggings = null;
		}

		MagicItem magicBootsItem = MagicItems.getMagicItemFromString(getConfigString("boots", ""));
		if (magicBootsItem != null) {
			boots = magicBootsItem.getItemStack();
			if (boots != null && BlockUtils.isAir(boots.getType()))
				boots = null;
		}

		if (mainHandItem != null)
			mainHandItem.setAmount(1);
		if (offHandItem != null)
			offHandItem.setAmount(1);
		if (helmet != null)
			helmet.setAmount(1);
		if (chestplate != null)
			chestplate.setAmount(1);
		if (leggings != null)
			leggings.setAmount(1);
		if (boots != null)
			boots.setAmount(1);

		mainHandItemDropChance = getConfigDataFloat("main-hand-drop-chance", 0);
		offHandItemDropChance = getConfigDataFloat("off-hand-drop-chance", 0);
		helmetDropChance = getConfigDataFloat("helmet-drop-chance", 0);
		chestplateDropChance = getConfigDataFloat("chestplate-drop-chance", 0);
		leggingsDropChance = getConfigDataFloat("leggings-drop-chance", 0);
		bootsDropChance = getConfigDataFloat("boots-drop-chance", 0);
		yOffset = getConfigDataFloat("y-offset", 0.1F);

		duration = getConfigDataInt("duration", 0);
		fireTicks = getConfigDataInt("fire-ticks", 0);
		targetInterval = getConfigDataInt("target-interval", -1);
		spellInterval = getConfigInt("spell-interval", 20);
		targetPriorityLimit = getConfigDataInt("target-priority-limit", 5);

		targetRange = getConfigDataDouble("target-range", 20);
		targetPriorityRange = getConfigDataDouble("target-priority-range", 10);
		retargetRange = getConfigDataDouble("retarget-range", 50);

		toggle = getConfigDataBoolean("toggle", false);

		location = getConfigString("location", "target");
		nameplateText = Util.getMiniMessage(getConfigString("nameplate-text", null));

		noAI = getConfigBoolean("no-ai", false);
		gravity = getConfigBoolean("gravity", true);
		setOwner = getConfigBoolean("set-owner", false);
		removeAI = getConfigBoolean("remove-ai", false);
		removeMob = getConfigBoolean("remove-mob", true);
		invulnerable = getConfigBoolean("invulnerable", false);
		useCasterName = getConfigBoolean("use-caster-name", false);
		addLookAtPlayerAI = getConfigBoolean("add-look-at-player-ai", false);
		allowSpawnInMidair = getConfigBoolean("allow-spawn-in-midair", false);
		cancelAttack = getConfigBoolean("cancel-attack", true);
		synchroniseIntervalSpells = getConfigBoolean("synchronise-interval-spells", false);

		attackSpellName = getConfigString("attack-spell", "");
		spellOnSpawnName = getConfigString("spell-on-spawn", "");
		spellOnDeathName = getConfigString("spell-on-death", "");
		intervalSpellName = getConfigString("interval-spell", "");

		// Attributes
		// - [AttributeName] [Number] [Operation]
		List<String> attributeList = getConfigStringList("attributes", null);
		if (attributeList != null && !attributeList.isEmpty()) {
			attributes = MagicSpells.getAttributeManager().getAttributesConfigData(attributeList,
					internalName + ".attributes");
		}

		List<String> list = getConfigStringList("potion-effects", null);
		if (list != null && !list.isEmpty()) {
			potionEffects = new ArrayList<>();
			for (String data : list) {
				String[] split = data.split(" ");
				try {
					PotionEffectType type = Util.getPotionEffectType(split[0]);
					if (type == null)
						throw new Exception("");
					int duration = 600;
					if (split.length > 1)
						duration = Integer.parseInt(split[1]);
					int strength = 0;
					if (split.length > 2)
						strength = Integer.parseInt(split[2]);
					boolean ambient = split.length > 3
							&& (split[3].equalsIgnoreCase("ambient") || split[3].equalsIgnoreCase("true"));
					boolean hidden = split.length > 4
							&& (split[4].equalsIgnoreCase("hidden") || split[4].equalsIgnoreCase("true"));
					potionEffects.add(new PotionEffect(type, duration, strength, ambient, !hidden));
				} catch (Exception e) {
					MagicSpells.error(
							"SpawnMonsterSpell '" + spellName + "' has an invalid potion effect defined: " + data);
				}
			}
		}

		mountList = getConfigKeys("mounts");
		removeMountsOnAnyDeath = getConfigBoolean("remove-mounts-if-any-die", true);

		pulsers = new HashMap<>();
		toggledEntities = new HashMap<>();
		ticker = new EntityPulserTicker();
	}

	@Override
	public void initialize() {
		super.initialize();

		if (entityData == null || entityData.getEntityType() == null) {
			MagicSpells.error("SpawnEntitySpell '" + internalName + "' has an invalid entity defined!");
			entityData = null;
		}

		if (!attackSpellName.isEmpty()) {
			attackSpell = new Subspell(attackSpellName);
			if (!attackSpell.process()) {
				MagicSpells.error("SpawnEntitySpell '" + internalName + "' has an invalid attack-spell defined!");
				attackSpell = null;
			}
			attackSpellName = null;
		}

		if (!spellOnSpawnName.isEmpty()) {
			spellOnSpawn = new Subspell(spellOnSpawnName);
			if (!spellOnSpawn.process()) {
				MagicSpells.error("SpawnEntitySpell '" + internalName + "' has an invalid spell-on-spawn '"
						+ spellOnSpawnName + "' defined!");
				spellOnSpawn = null;
			}
			spellOnSpawnName = null;
		}

		if (!spellOnDeathName.isEmpty()) {
			spellOnDeath = new Subspell(spellOnDeathName);
			if (!spellOnDeath.process()) {
				MagicSpells.error("SpawnEntitySpell '" + internalName + "' has an invalid spell-on-death '"
						+ spellOnDeathName + "' defined!");
				spellOnDeath = null;
			}
			spellOnDeathName = null;
		}

		if (!intervalSpellName.isEmpty()) {
			intervalSpell = new Subspell(intervalSpellName);
			if (!intervalSpell.process()) {
				MagicSpells.error("SpawnEntitySpell '" + internalName + "' has an invalid interval-spell defined!");
				intervalSpell = null;
			}
			intervalSpellName = null;
		}
	}

	@Override
	public void turnOff() {
		for (LivingEntity entity : entities) {
			List<Entity> forRemoval = new ArrayList<>();
			Entity _ent = entity;
			Entity _riding = entity.getVehicle();

			while (!_ent.getPassengers().isEmpty()) {
				forRemoval.add(_ent);
				_ent = _ent.getPassengers().get(0);
			}
			while (_riding != null) {
				forRemoval.add(_riding);
				_riding = _riding.getVehicle();
			}

			for (Entity ent : forRemoval) {
				if (!(ent instanceof Player))
					ent.remove();
			}
			if (!(_ent instanceof Player))
				_ent.remove();
		}

		ticker.stop();
		entities.clear();
		pulsers.clear();
		toggledEntities.clear();
		totalEntities = 0;
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			// Handle toggle mode
			if (toggle.get(caster, null, power, args) && caster instanceof Player player) {
				UUID playerId = player.getUniqueId();
				if (toggledEntities.containsKey(playerId)) {
					List<LivingEntity> playerEntities = toggledEntities.get(playerId);
					// Check if any entities are still valid and alive
					boolean hasValidEntities = playerEntities.stream()
							.anyMatch(e -> e != null && e.isValid() && !e.isDead());
					if (hasValidEntities) {
						// Remove all entities
						for (LivingEntity entity : new ArrayList<>(playerEntities)) {
							if (entity != null && entity.isValid()) {
								// Remove mounts if present
								if (mountList != null && !mountList.isEmpty()) {
									Entity _riding = entity.getVehicle();
									while (_riding != null) {
										Entity _prev = _riding;
										_riding = _riding.getVehicle();
										if (!(_prev instanceof Player))
											_prev.remove();
									}
								}

								// Remove passengers (mounts on top)
								List<Entity> passengers = new ArrayList<>(entity.getPassengers());
								for (Entity passenger : passengers) {
									if (!(passenger instanceof Player))
										passenger.remove();
								}

								entity.remove();
								entities.remove(entity);
								pulsers.remove(entity);
								totalEntities--;
							}
						}
						toggledEntities.remove(playerId);
						return PostCastAction.HANDLE_NORMALLY;
					} else {
						// Clean up invalid entities
						toggledEntities.remove(playerId);
					}
				}
			}

			Location loc = null;
			LivingEntity target = null;

			switch (location.toLowerCase()) {
				case "focus" -> {
					loc = getRandomLocationFrom(caster.getLocation(), 3);
					TargetInfo<LivingEntity> info = getTargetedEntity(caster, power, args);
					if (info.noTarget())
						return noTarget(caster, args, info);
					target = info.target();
					power = info.power();
				}
				case "target" -> {
					Block block = getTargetedBlock(caster, power, args);
					if (block != null && block.getType() != Material.AIR) {
						if (BlockUtils.isPathable(block))
							loc = block.getLocation();
						else if (BlockUtils.isPathable(block.getRelative(BlockFace.UP)))
							loc = block.getLocation().add(0, 1, 0);
					}
				}
				case "caster" -> loc = caster.getLocation();
				case "random" -> loc = getRandomLocationFrom(caster.getLocation(), getRange(power));
				case "casteroffset" -> {
					String[] split = location.split(":");
					float y = Float.parseFloat(split[1]);
					loc = caster.getLocation().add(0, y, 0);
					loc.setPitch(0);
				}
			}

			if (loc == null)
				return noTarget(caster, args);
			spawnMob(caster, caster.getLocation(), loc, target, power, args);
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		switch (location.toLowerCase()) {
			case "target" -> spawnMob(caster, caster.getLocation(), target, null, power, args);
			case "caster" -> spawnMob(caster, caster.getLocation(), caster.getLocation(), null, power, args);
			case "random" -> {
				Location loc = getRandomLocationFrom(target, getRange(power));
				if (loc != null)
					spawnMob(caster, caster.getLocation(), loc, null, power, args);
			}
			case "offset" -> {
				String[] split = location.split(":");
				float y = Float.parseFloat(split[1]);
				Location loc = target.clone().add(0, y, 0);
				loc.setPitch(0);
				spawnMob(caster, caster.getLocation(), loc, null, power, args);
			}
		}
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return castAtLocation(caster, target, power, null);
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		switch (location.toLowerCase()) {
			case "target", "caster" -> spawnMob(null, target, target, null, power, args);
			case "random" -> {
				Location loc = getRandomLocationFrom(target, getRange(power));
				if (loc != null)
					spawnMob(null, target, loc, null, power, args);
			}
			case "offset" -> {
				String[] split = location.split(":");
				float y = Float.parseFloat(split[1]);
				Location loc = target.clone().add(0, y, 0);
				loc.setPitch(0);
				spawnMob(null, target, loc, null, power, args);
			}
		}
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(Location target, float power) {
		return castAtLocation(target, power, null);
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		if (location.equals("focus"))
			spawnMob(caster, from, from, target, power, args);
		else
			castAtLocation(caster, from, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power) {
		return castAtEntityFromLocation(caster, from, target, power, null);
	}

	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		if (location.equals("focus"))
			spawnMob(null, from, from, target, power, args);
		else
			castAtLocation(from, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power) {
		return castAtEntityFromLocation(from, target, power, null);
	}

	private Location getRandomLocationFrom(Location location, int range) {
		World world = location.getWorld();
		int x;
		int y;
		int z;
		int attempts = 0;
		Block block;
		Block block2;

		while (attempts < 10) {
			x = location.getBlockX() + random.nextInt(range << 1) - range;
			y = location.getBlockY() + 2;
			z = location.getBlockZ() + random.nextInt(range << 1) - range;

			block = world.getBlockAt(x, y, z);
			if (block.getType() == Material.WATER)
				return block.getLocation();
			if (BlockUtils.isPathable(block)) {
				if (allowSpawnInMidair)
					return block.getLocation();
				int c = 0;
				while (c < 5) {
					block2 = block.getRelative(BlockFace.DOWN);
					if (BlockUtils.isPathable(block2))
						block = block2;
					else
						return block.getLocation();
					c++;
				}
			}

			attempts++;
		}
		return null;
	}

	private void spawnMob(LivingEntity caster, Location source, Location loc, LivingEntity target, float power,
			String[] args) {
		if (entityData == null || entityData.getEntityType() == null)
			return;

		int duration = this.duration.get(caster, target, power, args);

		loc.setYaw((float) (JdkMath.random() * 360));
		LivingEntity entity = (LivingEntity) entityData.spawn(
				loc.add(0.5, yOffset.get(caster, target, power, args), 0.5),
				e -> {
					LivingEntity preSpawned = (LivingEntity) e;
					prepMob(caster, target, preSpawned, power, args, duration);

					int fireTicks = this.fireTicks.get(caster, target, power, args);
					if (fireTicks > 0)
						preSpawned.setFireTicks(fireTicks);
					if (potionEffects != null)
						preSpawned.addPotionEffects(potionEffects);

					// Apply attributes
					if (attributes != null) {
						Set<AttributeManager.AttributeInfo> resolved = attributes
								.get(new SpellData(caster, preSpawned, loc, power, args));
						if (resolved != null)
							MagicSpells.getAttributeManager().addEntityAttributes(preSpawned, resolved);
					}

					if (removeAI) {
						if (addLookAtPlayerAI) {
							if (preSpawned instanceof Mob mob) {
								MobGoals mobGoals = Bukkit.getMobGoals();
								mobGoals.removeAllGoals(mob);
								mobGoals.addGoal(mob, 1, new LookAtEntityGoal(mob, HumanEntity.class, 10.0F, 1.0F));
							}
						} else {
							preSpawned.setAI(false);
						}
					}
					preSpawned.setAI(!noAI);
					preSpawned.setInvulnerable(invulnerable);

					if (target != null)
						MobUtil.setTarget(preSpawned, target);
				});

		if (entity == null) {
			MagicSpells.error("SpawnEntitySpell '" + internalName + "' failed to spawn entity (check entity.entity type).");
			return;
		}

		if (mountList != null && !mountList.isEmpty())
			createMounts(caster, target, power, args, entity, duration);

		int targetInterval = this.targetInterval.get(caster, null, power, args);
		if (targetInterval > 0)
			new Targeter(caster, entity, power, args);

		AttackMonitor monitor = new AttackMonitor(caster, entity, target, power, args);
		MagicSpells.registerEvents(monitor);

		MagicSpells.scheduleDelayedTask(() -> HandlerList.unregisterAll(monitor), duration > 0 ? duration : 12000);

		if (spellOnSpawn != null) {
			spellOnSpawn.subcast(caster, entity, power, args);
		}

		if (caster != null)
			playSpellEffects(caster, source, entity, power, args);
		else
			playSpellEffects(source, entity, power, args);

		entities.add(entity);

		// Track entity in toggle map if toggle is enabled
		if (toggle.get(caster, null, power, args) && caster instanceof Player player) {
			UUID playerId = player.getUniqueId();
			toggledEntities.computeIfAbsent(playerId, k -> new ArrayList<>()).add(entity);
		}

		if (duration > 0) {
			MagicSpells.scheduleDelayedTask(() -> {
				if (entity == null || !entity.isValid())
					return;

				if (mountList != null && !mountList.isEmpty()) {
					// Removing the mounts of the entity is removed
					Entity _riding = entity.getVehicle();
					while (_riding != null) {
						Entity _prev = _riding;
						_riding = _riding.getVehicle();
						if (!(_prev instanceof Player))
							_prev.remove();
					}
				}

				if (spellOnDeath != null) {
					spellOnDeath.subcast(entity, entity, power, args);
				}

				entity.remove();
				entities.remove(entity);

				// Remove from toggle map if present
				if (caster instanceof Player player) {
					UUID playerId = player.getUniqueId();
					if (toggledEntities.containsKey(playerId)) {
						toggledEntities.get(playerId).remove(entity);
						if (toggledEntities.get(playerId).isEmpty()) {
							toggledEntities.remove(playerId);
						}
					}
				}

				totalEntities--;
			}, duration);
		}
		if (intervalSpell != null && spellInterval > 0) {
			ticker.start();
			pulsers.put(entity, new EntityPulser(caster, entity, power, args));
			if (duration > 0) {
				MagicSpells.scheduleDelayedTask(() -> {
					pulsers.remove(entity);
				}, duration);
			}
		}

		totalEntities++;
	}

	private void prepMob(LivingEntity caster, LivingEntity target, LivingEntity entity, float power, String[] args,
			int duration) {
		entity.setGravity(gravity);

		if (setOwner && entity instanceof Tameable tameable && tameable.isTamed()
				&& caster instanceof AnimalTamer tamer)
			tameable.setOwner(tamer);

		if (entity instanceof Enderman) {
			if (mainHandItem != null && !BlockUtils.isAir(mainHandItem.getType())) {
				((Enderman) entity).setCarriedBlock(mainHandItem.getType().createBlockData());
			}
		} else if (entity instanceof LivingEntity) {
			EntityEquipment entityEquipment = ((LivingEntity) entity).getEquipment();
			if (mainHandItem != null && !BlockUtils.isAir(mainHandItem.getType())) {
				entityEquipment.setItemInMainHand(mainHandItem);
				entityEquipment
						.setItemInMainHandDropChance(mainHandItemDropChance.get(caster, target, power, args) / 100f);
			}
			if (offHandItem != null && !BlockUtils.isAir(offHandItem.getType())) {
				entityEquipment.setItemInOffHand(offHandItem);
				entityEquipment
						.setItemInOffHandDropChance(offHandItemDropChance.get(caster, target, power, args) / 100f);
			}
		}

		final EntityEquipment equip = ((LivingEntity) entity).getEquipment();
		equip.setHelmet(helmet);
		equip.setChestplate(chestplate);
		equip.setLeggings(leggings);
		equip.setBoots(boots);
		if (!(entity instanceof ArmorStand)) {
			equip.setHelmetDropChance(helmetDropChance.get(caster, target, power, args) / 100f);
			equip.setChestplateDropChance(chestplateDropChance.get(caster, target, power, args) / 100f);
			equip.setLeggingsDropChance(leggingsDropChance.get(caster, target, power, args) / 100f);
			equip.setBootsDropChance(bootsDropChance.get(caster, target, power, args) / 100f);
		}

		entity.addScoreboardTag(MagicSpells.ENTITY_TAG);
		if (duration > 0)
			entity.addScoreboardTag(MagicSpells.EXPIRATION_TIME_MILLIS_TAG + ":"
					+ (System.currentTimeMillis() + (duration * (1000 / TimeUtil.TICKS_PER_SECOND))));

		if (useCasterName && caster != null) {
			if (caster instanceof Player player)
				entity.customName(player.displayName());
			else
				entity.customName(caster.name());
			entity.setCustomNameVisible(true);
		} else if (nameplateText != null) {
			entity.customName(nameplateText);
			entity.setCustomNameVisible(true);
		}

		entity.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "PersistenceRequired"),
				PersistentDataType.BYTE, (byte) 1);
		entity.setRemoveWhenFarAway(false);
	}

	private void createMounts(LivingEntity caster, LivingEntity target, float power, String[] args, LivingEntity head,
			int duration) {

		List<Entity> ents = new ArrayList<>();

		// All need to be unique names
		for (String sectionName : mountList) {
			ConfigurationSection section = getConfigSection("mounts." + sectionName);
			EntityData mountData = new EntityData(section);
			Entity mount = mountData.spawn(head.getLocation(), e -> {
				{
					LivingEntity preSpawned = (LivingEntity) e;
					prepMob(caster, target, preSpawned, power, args, duration);

					int fireTicks = this.fireTicks.get(caster, target, power, args);
					if (fireTicks > 0)
						preSpawned.setFireTicks(fireTicks);
					if (potionEffects != null)
						preSpawned.addPotionEffects(potionEffects);

					// Apply attributes
					if (attributes != null) {
						Set<AttributeManager.AttributeInfo> resolved = attributes
								.get(new SpellData(caster, preSpawned, power, args));
						if (resolved != null)
							MagicSpells.getAttributeManager().addEntityAttributes(preSpawned, resolved);
					}

					if (removeAI) {
						if (addLookAtPlayerAI) {
							if (preSpawned instanceof Mob mob) {
								MobGoals mobGoals = Bukkit.getMobGoals();
								mobGoals.removeAllGoals(mob);
								mobGoals.addGoal(mob, 1, new LookAtEntityGoal(mob, HumanEntity.class, 10.0F, 1.0F));
							}
						} else {
							preSpawned.setAI(false);
						}
					}
					preSpawned.setAI(!noAI);
					preSpawned.setInvulnerable(invulnerable);

					if (target != null)
						MobUtil.setTarget(preSpawned, target);
				}
			});
			ents.add(mount);
		}

		for (int i = ents.size() - 1; i > 0; i--) {
			ents.get(i).addPassenger(ents.get(i - 1));
		}
		ents.get(0).addPassenger(head);
	}

	@EventHandler
	private void onEntityDeath(EntityDeathEvent event) {
		entityDeath(event.getEntity());
	}

	@EventHandler
	private void onExplode(EntityExplodeEvent event) {
		if (event.getEntity() instanceof LivingEntity)
			entityDeath((LivingEntity) event.getEntity());
	}

	private void entityDeath(LivingEntity entity) {
		if (!entities.contains(entity)) {
			if (!removeMountsOnAnyDeath)
				return;
			List<Entity> forRemoval = new ArrayList<>();
			Entity _ent = entity;
			Entity _riding = entity.getVehicle();

			while (_ent.getPassengers().size() > 0) {
				forRemoval.add(_ent);
				_ent = _ent.getPassengers().get(0);
			}
			while (_riding != null) {
				forRemoval.add(_riding);
				_riding = _riding.getVehicle();
			}

			if (_ent instanceof LivingEntity && entities.contains(_ent)) {
				for (Entity ent : forRemoval) {
					if (!(ent instanceof Player))
						ent.remove();
				}
				if (!(_ent instanceof Player))
					_ent.remove();
			}

			return;
		}
		if (removeMob) {
			if (mountList != null && !mountList.isEmpty()) {
				// Removing the mounts of the entity is removed
				Entity _riding = entity.getVehicle();
				while (_riding != null) {
					Entity _prev = _riding;
					_riding = _riding.getVehicle();
					if (!(_prev instanceof Player))
						_prev.remove();
				}
			}
			entities.remove(entity);
			if (pulsers.containsKey(entity))
				pulsers.remove(entity);

			// Remove from toggle map if present
			for (Map.Entry<UUID, List<LivingEntity>> entry : new HashMap<>(toggledEntities).entrySet()) {
				List<LivingEntity> playerEntities = entry.getValue();
				if (playerEntities.remove(entity)) {
					if (playerEntities.isEmpty()) {
						toggledEntities.remove(entry.getKey());
					}
					break;
				}
			}

			if (spellOnDeath != null) {
				spellOnDeath.subcast(entity, entity, 1F, new String[0]);
			}
			totalEntities--;
		}
	}

	private class AttackMonitor implements Listener {

		private final LivingEntity caster;
		private final LivingEntity monster;
		private final String[] args;
		private final float power;

		private LivingEntity target;
		private boolean inAttackSpell;

		private AttackMonitor(LivingEntity caster, LivingEntity monster, LivingEntity target, float power,
				String[] args) {
			this.caster = caster;
			this.monster = monster;
			this.target = target;
			this.power = power;
			this.args = args;
		}

		@EventHandler(ignoreCancelled = true)
		private void onDamage(EntityDamageByEntityEvent event) {
			if (event instanceof com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent)
				return;
			if (inAttackSpell)
				return;

			if (attackSpell == null || attackSpell.getSpell() == null || attackSpell.getSpell().onCooldown(monster))
				return;

			Entity damager = event.getDamager();
			if (damager instanceof Projectile) {
				if (((Projectile) damager).getShooter() != null
						&& ((Projectile) damager).getShooter() instanceof Entity) {
					damager = (Entity) ((Projectile) damager).getShooter();
				}
			}

			if (damager != monster)
				return;

			if (attackSpell != null && event.getEntity() instanceof LivingEntity damaged) {
				inAttackSpell = true;
				try {
					attackSpell.subcast(monster, monster.getLocation(), damaged, power, args);
				} finally {
					inAttackSpell = false;
				}
				event.setCancelled(cancelAttack);
			}
		}

		@EventHandler
		private void onTarget(EntityTargetEvent event) {
			if (event.getEntity() == monster) {
				if (!validTargetList.canTarget(caster, event.getTarget()) || (targetModifiers != null
						&& !targetModifiers.check(monster, (LivingEntity) event.getTarget())))
					event.setCancelled(true);
				else if (event.getTarget() == null)
					retarget(null);
				else if (target != null && event.getTarget() != target)
					event.setTarget(target);
			}
		}

		@EventHandler
		private void onDeath(EntityDeathEvent event) {
			if (event.getEntity() != target)
				return;
			target = null;
			retarget(event.getEntity());
		}

		private void retarget(LivingEntity ignore) {
			LivingEntity t = null;

			double retargetRange = SpawnEntitySpell.this.retargetRange.get(caster, null, power, args);
			double r = retargetRange * retargetRange;

			for (Entity e : monster.getNearbyEntities(retargetRange, retargetRange, retargetRange)) {
				if (!(e instanceof LivingEntity))
					continue;
				if (!validTargetList.canTarget(caster, e))
					continue;
				if (e == ignore)
					continue;
				if (targetModifiers != null && !targetModifiers.check(monster, (LivingEntity) e))
					continue;

				if (e instanceof Player p) {
					GameMode gamemode = p.getGameMode();
					if (gamemode == GameMode.CREATIVE || gamemode == GameMode.SPECTATOR)
						continue;
				}
				int distanceSquared = (int) monster.getLocation().distanceSquared(e.getLocation());
				if (distanceSquared < r) {
					r = distanceSquared;
					t = (LivingEntity) e;
					if (r < 25)
						break;
				}
			}
			target = t;
			if (t == null)
				return;
			MobUtil.setTarget(monster, t);
		}

	}

	private class Targeter implements Runnable {

		private final LivingEntity caster;
		private final LivingEntity entity;
		private final String[] args;
		private final float power;
		private final int taskId;

		private Targeter(LivingEntity caster, LivingEntity entity, float power, String[] args) {
			this.caster = caster;
			this.entity = entity;
			this.power = power;
			this.args = args;

			this.taskId = MagicSpells.scheduleRepeatingTask(this, 1, targetInterval.get(caster, null, power, args));
		}

		@Override
		public void run() {
			if (entity.isDead() || !entity.isValid()) {
				MagicSpells.cancelTask(taskId);
				return;
			}

			double targetRange = SpawnEntitySpell.this.targetRange.get(caster, null, power, args);
			double targetRangeSq = targetRange * targetRange;
			double targetPriorityRange = SpawnEntitySpell.this.targetPriorityRange.get(caster, null, power, args);
			double targetPriorityRangeSq = targetPriorityRange * targetPriorityRange;
			int targetPriorityLimit = SpawnEntitySpell.this.targetPriorityLimit.get(caster, null, power, args);
			double scanRange = targetPriorityLimit > 0 ? Math.max(targetRange, targetPriorityRange * 2) : targetRange;

			List<LivingEntity> targetable = new ArrayList<>();
			List<LivingEntity> priorityTargetable = new ArrayList<>();
			Map<LivingEntity, Integer> targeted = new HashMap<>();

			for (LivingEntity e : entity.getWorld().getNearbyLivingEntities(entity.getLocation(), scanRange, scanRange, scanRange)) {
				double distanceSq = e.getLocation().distanceSquared(entity.getLocation());

				if (distanceSq >= targetRangeSq)
					continue;

				if (targetPriorityLimit > 0 && e instanceof Mob && ((targetPriorityRangeSq * 4) > distanceSq)) {
					LivingEntity eTarget = ((Mob) e).getTarget();
					if (eTarget != null && eTarget.isValid()) {
						if (targeted.keySet().contains(eTarget)) {
							targeted.put(eTarget, targeted.get(eTarget) + 1);
						} else {
							targeted.put(eTarget, 1);
						}
					}
				}

				if (!validTargetList.canTarget(caster, e))
					continue;
				if (targetModifiers != null && !targetModifiers.check(entity, e))
					continue;

				targetable.add(e);

				if (distanceSq <= targetPriorityRangeSq) {
					priorityTargetable.add(e);
				}
			}

			if (targetable.isEmpty())
				return;

			LivingEntity target = null;
			EntityTargetEvent.TargetReason reason = EntityTargetEvent.TargetReason.CLOSEST_ENTITY;

			// Goes through all entities on the priority list from nearest to farthest. If
			// they are engaged by 5 or fewer entities, they will be the target
			if (!priorityTargetable.isEmpty()) {
				priorityTargetable
						.sort(Comparator.comparingDouble(e -> e.getLocation().distance(entity.getLocation())));

				for (LivingEntity e : priorityTargetable) {
					if (!targeted.keySet().contains(e) || targeted.get(e) < targetPriorityLimit) {
						target = e;
						break;
					}
				}
			}

			// If no valid target was found on the priority list, pick a random one from the
			// main list
			if (target == null) {
				target = targetable.get(random.nextInt(targetable.size()));
				reason = EntityTargetEvent.TargetReason.RANDOM_TARGET;
			}

			MobUtil.setTarget(entity, target, reason);
		}

	}

	private class EntityPulser {

		private final LivingEntity caster;
		private final LivingEntity entity;
		private final float power;
		private final int delay = random.nextInt(SpawnEntitySpell.this.spellInterval);

		private EntityPulser(LivingEntity caster, LivingEntity entity, float power, String[] args) {
			this.caster = caster;
			this.entity = entity;
			this.power = power;
		}

		private void pulse() {
			if (entity != null && entity.isValid() && pulsers.containsKey(entity) && entity.getWorld()
					.isChunkLoaded(entity.getLocation().getBlockX() >> 4, entity.getLocation().getBlockZ() >> 4)) {
				activate();
			} else {
				pulsers.remove(entity);
			}
		}

		private void activate() {
			LivingEntity target = null;
			if (entity instanceof Mob)
				target = ((Mob) entity).getTarget();

			if (intervalSpell.isTargetedEntityFromLocationSpell()) {
				if (target != null)
					intervalSpell.castAtEntityFromLocation(caster, entity.getLocation(), target, power);
			} else if (intervalSpell.isTargetedEntitySpell()) {
				if (target != null)
					intervalSpell.castAtEntity(caster, (LivingEntity) target, power);
			} else if (intervalSpell.isTargetedLocationSpell()) {
				Location location = entity.getLocation();
				if (target != null && !(intervalSpell.getSpell() instanceof InstantSpell)) {
					location = target.getLocation();
				}

				intervalSpell.castAtLocation(caster, location, power);
			} else {
				intervalSpell.cast(entity, power);
			}
		}
	}

	private class EntityPulserTicker implements Runnable {

		private int taskId = -1;

		private void start() {
			if (taskId < 0)
				taskId = MagicSpells.scheduleRepeatingTask(this, 0, spellInterval);
		}

		private void stop() {
			if (taskId > 0) {
				MagicSpells.cancelTask(taskId);
				taskId = -1;
			}
		}

		@Override
		public void run() {
			for (Map.Entry<LivingEntity, EntityPulser> entry : new HashMap<>(pulsers).entrySet()) {
				if (synchroniseIntervalSpells) {
					entry.getValue().pulse();
				} else {
					MagicSpells.scheduleDelayedTask(() -> entry.getValue().pulse(), entry.getValue().delay);
				}
			}
			if (pulsers.isEmpty())
				stop();
		}

	}

	public static int getTotalEntities() {
		return totalEntities;
	}

	public static void setTotalEntities(int i) {
		totalEntities = i;
	}

}