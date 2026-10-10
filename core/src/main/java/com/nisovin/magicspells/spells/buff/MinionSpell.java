package com.nisovin.magicspells.spells.buff;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.bukkit.event.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MobUtil;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.spells.DamageSpell;
import com.nisovin.magicspells.util.ValidTargetList;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.managers.AttributeManager;

public class MinionSpell extends BuffSpell {

	private final Map<UUID, LivingEntity> minions;
	private final Map<LivingEntity, UUID> players;
	private final Map<UUID, LivingEntity> targets;

	private final int[] chances;

	private ValidTargetList minionTargetList;
	private EntityType[] creatureTypes;
	private ConfigData<Boolean> powerAffectsHealth;
	private boolean preventCombust;
	private boolean gravity;
	private boolean baby;
	private ConfigData<Double> powerHealthFactor;
	private ConfigData<Double> maxHealth;
	private ConfigData<Double> health;
	private String minionName;
	private Vector spawnOffset;
	private double followRange;
	private float followSpeed;
	private float maxDistance;

	private final String spawnSpellName;
	private final String deathSpellName;
	private final String attackSpellName;

	private Subspell spawnSpell;
	private Subspell deathSpell;
	private Subspell attackSpell;

	private ItemStack mainHandItem;
	private ItemStack offHandItem;
	private ItemStack helmet;
	private ItemStack chestplate;
	private ItemStack leggings;
	private ItemStack boots;

	private float mainHandItemDropChance;
	private float offHandItemDropChance;
	private float helmetDropChance;
	private float chestplateDropChance;
	private float leggingsDropChance;
	private float bootsDropChance;

	private List<PotionEffect> potionEffects;

	private ConfigData<Set<AttributeManager.AttributeInfo>> attributes;

	public MinionSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		minions = new HashMap<>();
		players = new HashMap<>();
		targets = new ConcurrentHashMap<>();

		// Formatted as <entity type> <chance>
		List<String> c = getConfigStringList("mob-chances", new ArrayList<>());
		if (c.isEmpty()) c.add("Zombie 100");

		creatureTypes = new EntityType[c.size()];
		chances = new int[c.size()];
		for (int i = 0; i < c.size(); i++) {
			String[] data = c.get(i).split(" ");
			EntityType creatureType = MobUtil.getEntityType(data[0]);
			int chance = 0;
			if (creatureType != null) {
				try {
					chance = Integer.parseInt(data[1]);
				} catch (NumberFormatException e) {
					// No op
				}
			}
			creatureTypes[i] = creatureType;
			chances[i] = chance;
		}

		// Potion effects
		List<String> potionEffectList = getConfigStringList("potion-effects", null);
		if (potionEffectList != null && !potionEffectList.isEmpty()) {
			potionEffects = new ArrayList<>();
			for (String potion : potionEffectList) {
				String[] split = potion.split(" ");
				try {
					PotionEffectType type = Util.getPotionEffectType(split[0]);
					if (type == null) throw new Exception("");
					int duration = 600;
					if (split.length > 1) duration = Integer.parseInt(split[1]);
					int strength = 0;
					if (split.length > 2) strength = Integer.parseInt(split[2]);
					boolean ambient = split.length > 3 && split[3].equalsIgnoreCase("ambient");
					potionEffects.add(new PotionEffect(type, duration, strength, ambient));
				} catch (Exception e) {
					MagicSpells.error("MinionSpell '" + internalName + "' has an invalid potion effect string " + potion);
				}
			}
		}

		// Attributes
		// - [AttributeName] [Number] [Operation]
		List<String> attributeList = getConfigStringList("attributes", null);
		if (attributeList != null && !attributeList.isEmpty()) {
			attributes = MagicSpells.getAttributeManager().getAttributesConfigData(attributeList, internalName + ".attributes");
		}

		// Equipment
		MagicItem magicMainHandItem = MagicItems.getMagicItemFromString(getConfigString("main-hand", ""));
		if (magicMainHandItem != null) {
			mainHandItem = magicMainHandItem.getItemStack();
			if (mainHandItem != null && BlockUtils.isAir(mainHandItem.getType())) mainHandItem = null;
		}

		MagicItem magicOffHandItem = MagicItems.getMagicItemFromString(getConfigString("off-hand", ""));
		if (magicOffHandItem != null) {
			offHandItem = magicOffHandItem.getItemStack();
			if (offHandItem != null && BlockUtils.isAir(offHandItem.getType())) offHandItem = null;
		}

		MagicItem magicHelmetItem = MagicItems.getMagicItemFromString(getConfigString("helmet", ""));
		if (magicHelmetItem != null) {
			helmet = magicHelmetItem.getItemStack();
			if (helmet != null && BlockUtils.isAir(helmet.getType())) helmet = null;
		}

		MagicItem magicChestplateItem = MagicItems.getMagicItemFromString(getConfigString("chestplate", ""));
		if (magicChestplateItem != null) {
			chestplate = magicChestplateItem.getItemStack();
			if (chestplate != null && BlockUtils.isAir(chestplate.getType())) chestplate = null;
		}

		MagicItem magicLeggingsItem = MagicItems.getMagicItemFromString(getConfigString("leggings", ""));
		if (magicLeggingsItem != null) {
			leggings = magicLeggingsItem.getItemStack();
			if (leggings != null && BlockUtils.isAir(leggings.getType())) leggings = null;
		}

		MagicItem magicBootsItem = MagicItems.getMagicItemFromString(getConfigString("boots", ""));
		if (magicBootsItem != null) {
			boots = magicBootsItem.getItemStack();
			if (boots != null && BlockUtils.isAir(boots.getType())) boots = null;
		}

		if (mainHandItem != null) mainHandItem.setAmount(1);
		if (offHandItem != null) offHandItem.setAmount(1);
		if (helmet != null) helmet.setAmount(1);
		if (chestplate != null) chestplate.setAmount(1);
		if (leggings != null) leggings.setAmount(1);
		if (boots != null) boots.setAmount(1);

		// Minion target list
		minionTargetList = new ValidTargetList(this, getConfigStringList("minion-targets", null));

		mainHandItemDropChance = getConfigFloat("main-hand-drop-chance", 0) / 100F;
		offHandItemDropChance = getConfigFloat("off-hand-drop-chance", 0) / 100F;
		helmetDropChance = getConfigFloat("helmet-drop-chance", 0) / 100F;
		chestplateDropChance = getConfigFloat("chestplate-drop-chance", 0) / 100F;
		leggingsDropChance = getConfigFloat("leggings-drop-chance", 0) / 100F;
		bootsDropChance = getConfigFloat("boots-drop-chance", 0) / 100F;

		spawnSpellName = getConfigString("spell-on-spawn", "");
		attackSpellName = getConfigString("spell-on-attack", "");
		deathSpellName = getConfigString("spell-on-death", "");

		spawnOffset = getConfigVector("spawn-offset", "1,0,0");
		followRange = getConfigDouble("follow-range",  1.5) * -1;
		followSpeed = getConfigFloat("follow-speed", 1F);
		maxDistance = getConfigFloat("max-distance", 30F);
		powerAffectsHealth = getConfigDataBoolean("power-affects-health", false);
		powerHealthFactor = getConfigDataDouble("power-health-factor", 1);
		maxHealth = getConfigDataDouble("max-health", 20);
		health = getConfigDataDouble("health", 20);
		minionName = getConfigString("minion-name", "");
		gravity = getConfigBoolean("gravity", true);
		baby = getConfigBoolean("baby", false);
		preventCombust = getConfigBoolean("prevent-sun-burn", true);
	}

	@Override
	public void initialize() {
		super.initialize();

		spawnSpell = new Subspell(spawnSpellName);
		if (!spawnSpell.process() && !spawnSpellName.isEmpty()) {
			MagicSpells.error("MinionSpell '" + internalName + "' has an invalid spell-on-spawn defined!");
			spawnSpell = null;
		}

		attackSpell = new Subspell(attackSpellName);
		if (!attackSpell.process() && !attackSpellName.isEmpty()) {
			MagicSpells.error("MinionSpell '" + internalName + "' has an invalid spell-on-attack defined!");
			attackSpell = null;
		}

		deathSpell = new Subspell(deathSpellName);
		if (!deathSpell.process() && !deathSpellName.isEmpty()) {
			MagicSpells.error("MinionSpell '" + internalName + "' has an invalid spell-on-death defined!");
			deathSpell = null;
		}
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		if (!(entity instanceof Player player)) return false;
		// Selecting the mob
		EntityType creatureType = null;
		int num = random.nextInt(100);
		int n = 0;
		for (int i = 0; i < creatureTypes.length; i++) {
			if (num < chances[i] + n) {
				creatureType = creatureTypes[i];
				break;
			}

			n += chances[i];
		}

		if (creatureType == null) return false;

		// Spawn location
		Location loc = player.getLocation().clone();
		Vector startDir = loc.clone().getDirection().setY(0).normalize();
		Vector horizOffset = new Vector(-startDir.getZ(), 0, startDir.getX()).normalize();
		loc.add(horizOffset.multiply(spawnOffset.getZ()));
		loc.add(startDir.clone().multiply(spawnOffset.getX()));
		loc.setY(loc.getY() + spawnOffset.getY());

		// Spawn creature
		LivingEntity minion = (LivingEntity) player.getWorld().spawnEntity(loc, creatureType);
		if (!(minion instanceof Mob)) {
			minion.remove();
			MagicSpells.error("MinionSpell '" + internalName + "' can only summon mobs!");
			return false;
		}

		if (minion instanceof Ageable ageable) {
			if (baby) ageable.setBaby();
			else ageable.setAdult();
		}

		minion.setGravity(gravity);
		minion.customName(Util.getMiniMessage(minionName.replace("%c", player.getName())));
		minion.setCustomNameVisible(true);

		double maxHealth = this.maxHealth.get(player, minion, power, args);
		double health = this.health.get(player, minion, power, args);
		double powerHealthFactor = this.powerHealthFactor.get(player, minion, power, args);
		if (Boolean.TRUE.equals(powerAffectsHealth.get(player, minion, power, args))) {
			Util.setMaxHealth(minion, maxHealth * power * powerHealthFactor);
			minion.setHealth(health * power * powerHealthFactor);
		} else {
			Util.setMaxHealth(minion, maxHealth);
			minion.setHealth(health);
		}

		if (spawnSpell != null) spawnSpell.subcast(player, minion.getLocation(), minion, power, args);

		// Apply potion effects
		if (potionEffects != null) minion.addPotionEffects(potionEffects);

		// Apply attributes
		if (attributes != null) {
			Set<AttributeManager.AttributeInfo> resolved = attributes.get(new SpellData(player, minion, power, args));
			if (resolved != null) MagicSpells.getAttributeManager().addEntityAttributes(minion, resolved);
		}

		// Equip the minion
		final EntityEquipment eq = minion.getEquipment();
		if (mainHandItem != null) eq.setItemInMainHand(mainHandItem.clone());
		if (offHandItem != null) eq.setItemInOffHand(offHandItem.clone());
		if (helmet != null) eq.setHelmet(helmet.clone());
		if (chestplate != null) eq.setChestplate(chestplate.clone());
		if (leggings != null) eq.setLeggings(leggings.clone());
		if (boots != null) eq.setBoots(boots.clone());

		// Equipment drop chance
		eq.setItemInMainHandDropChance(mainHandItemDropChance);
		eq.setItemInOffHandDropChance(offHandItemDropChance);
		eq.setHelmetDropChance(helmetDropChance);
		eq.setChestplateDropChance(chestplateDropChance);
		eq.setLeggingsDropChance(leggingsDropChance);
		eq.setBootsDropChance(bootsDropChance);

		minions.put(player.getUniqueId(), minion);
		players.put(minion, player.getUniqueId());
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return minions.containsKey(entity.getUniqueId());
	}

	public boolean isMinion(Entity entity) {
		return minions.containsValue(entity);
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		LivingEntity minion = minions.remove(entity.getUniqueId());
		if (minion != null && !minion.isDead()) minion.remove();

		players.remove(minion);
		targets.remove(entity.getUniqueId());
	}

	@Override
	protected void turnOff() {
		Util.forEachValueOrdered(minions, Entity::remove);
		minions.clear();
		players.clear();
		targets.clear();
	}

	@EventHandler
	public void onEntityTarget(EntityTargetEvent e) {
		if (minions.isEmpty() || e.getTarget() == null) return;
		if (!(e.getEntity() instanceof LivingEntity minion)) return;
		if (!isMinion(e.getEntity())) return;

		Player pl = Bukkit.getPlayer(players.get(minion));
		if (pl == null) return;

		if (targets.get(pl.getUniqueId()) == null || !targets.containsKey(pl.getUniqueId()) || !targets.get(pl.getUniqueId()).isValid()) {
			e.setCancelled(true);
			return;
		}

		if (isExpired(pl)) {
			turnOff(pl);
			return;
		}

		LivingEntity target = targets.get(pl.getUniqueId());

		// Minion is targeting the right entity
		if (e.getTarget().equals(target)) return;

		// If its dead or owner/minion is the target, cancel and return
		if (target.isDead() || target.equals(pl) || target.equals(minion)) {
			e.setCancelled(true);
			return;
		}

		// Set the correct target
		e.setTarget(target);
		addUseAndChargeCost(pl);
		MobUtil.setTarget(minion, target);
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityDamage(EntityDamageByEntityEvent e) {
		Entity entity = e.getEntity();
		Entity damager = e.getDamager();
		if (damager.isDead() || !damager.isValid()) return;
		if (entity.isDead() || !entity.isValid()) return;
		if (!(entity instanceof LivingEntity)) return;
		// Check if the damaged entity is a player
		if (entity instanceof Player pl && isActive((Player) entity)) {
			// If a Minion tries to attack his owner, cancel the damage and stop the minion
			if (minions.get(pl.getUniqueId()).equals(damager)) {
				targets.remove(pl.getUniqueId());
				MobUtil.setTarget(minions.get(pl.getUniqueId()), null);
				e.setCancelled(true);
				return;
			}
			// Check if the player was damaged by a projectile
			if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof LivingEntity) {
				// Check if the shooter is alive
				Entity shooter = (LivingEntity) ((Projectile) damager).getShooter();
				if (shooter.isValid() && !shooter.isDead()) damager = shooter;
			}

			// If distance between previous target and the player is less than between the new target, the minion will keep focusing the previous target
			LivingEntity previousTarget = targets.get(pl.getUniqueId());
			if (previousTarget != null && previousTarget.getWorld().equals(pl.getWorld()) && pl.getLocation().distanceSquared(previousTarget.getLocation()) < pl.getLocation().distanceSquared(damager.getLocation())) return;

			targets.put(pl.getUniqueId(), (LivingEntity) damager);
			MobUtil.setTarget(minions.get(pl.getUniqueId()), (LivingEntity) damager);
			return;
		}

		// Check if the damaged entity is a minion
		if (isMinion(entity)) {
			LivingEntity minion = (LivingEntity) entity;
			Player owner = Bukkit.getPlayer(players.get(minion));
			if (owner == null || !owner.isOnline() || !owner.isValid()) return;
			// Owner cant damage his minion
			if (damager.equals(owner)) {
				e.setCancelled(true);
				return;
			}

			if (((LivingEntity) entity).getHealth() - e.getFinalDamage() <= 0 && deathSpell != null)
				deathSpell.subcast(owner, minion.getLocation(), minion, 1, null);

			// If the minion is far away from the owner, forget about attacking
			if (owner.getWorld().equals(minion.getWorld()) && owner.getLocation().distanceSquared(minion.getLocation()) > maxDistance * maxDistance) return;

			// If the owner has no targets and someone will attack the minion, he will strike back
			if (targets.get(owner.getUniqueId()) == null || targets.get(owner.getUniqueId()).isDead() || !targets.get(owner.getUniqueId()).isValid()) {
				// Check if the minion damager is an arrow, if so, get the shooter, otherwise get the living damager
				LivingEntity minionDamager = null;
				if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof LivingEntity shooter) {
					// Check if the shooter is alive
					if (shooter.isValid() && !shooter.isDead()) minionDamager = shooter;

				} else if (damager instanceof LivingEntity) {
					minionDamager = (LivingEntity) damager;
				}
				if (minionDamager != null) {
					targets.put(owner.getUniqueId(), minionDamager);
					MobUtil.setTarget(minion, minionDamager);
				}
			}
		}

		if (damager instanceof Player pl) {
			// Check if player's damaged target is his minion, if its not, make him attack your target
			if (!isActive(pl)) return;
			for (BuffSpell buff : MagicSpells.getBuffManager().getActiveBuffs(pl)) {
				if (!(buff instanceof MinionSpell)) continue;
				if (entity.equals(((MinionSpell)buff).minions.get(pl.getUniqueId()))) {
					e.setCancelled(true);
					return;
				}
			}

			if (isMinion(entity) && minions.get(pl.getUniqueId()).equals(entity)) {
				e.setCancelled(true);
				return;
			}

			// Check if the entity can be targeted by the minion
			if (!minionTargetList.canTarget(entity)) return;

			addUseAndChargeCost(pl);
			targets.put(pl.getUniqueId(), (LivingEntity) entity);
			MobUtil.setTarget(minions.get(pl.getUniqueId()), (LivingEntity) entity);

		}

		if (isMinion(damager)) {
			// Minion is the damager
			LivingEntity minion = (LivingEntity) damager;
			Player owner = Bukkit.getPlayer(players.get(minion));
			if (owner == null || !owner.isOnline() || !owner.isValid()) return;

			if (attackSpell != null) attackSpell.subcast(owner, minion.getLocation(), (LivingEntity) entity, 1, null);
		}

		// The target died, the minion will follow his owner
		if (targets.containsValue(entity) && ((LivingEntity) entity).getHealth() - e.getFinalDamage() <= 0) {
			for (UUID id : targets.keySet()) {
				if (!targets.get(id).equals(entity)) continue;
				Player pl = Bukkit.getPlayer(id);

				if (pl == null || !pl.isValid() || !pl.isOnline()) continue;

				targets.remove(id);
				MobUtil.setTarget(minions.get(id), null);

				Location loc = pl.getLocation().clone();
				loc.add(loc.getDirection().setY(0).normalize().multiply(followRange));
				((Mob) minions.get(pl.getUniqueId())).getPathfinder().moveTo(loc, followSpeed);
			}
		}
	}

	// Owner cant damage his minion with spells
	@EventHandler(ignoreCancelled = true)
	public void onSpellTarget(SpellTargetEvent e) {
		if (!(e.getSpell() instanceof DamageSpell)) return;
		if (!isActive(e.getCaster())) return;
		if (e.getTarget().equals(minions.get(e.getCaster().getUniqueId()))) e.setCancelled(true);
	}

	@EventHandler
	public void onEntityDeath(EntityDeathEvent e) {
		if (!isMinion(e.getEntity())) return;
		EntityEquipment eq = e.getEntity().getEquipment();
		List<ItemStack> newDrops = new ArrayList<>();
		for (ItemStack drop : e.getDrops()) {
			for (int i = 0; i < eq.getArmorContents().length; i++) {
				if (drop.equals(eq.getArmorContents()[i])) newDrops.add(drop);
			}
			if (drop.equals(mainHandItem) || drop.equals(offHandItem)) newDrops.add(drop);
		}

		// Clear all the regular drops
		e.getDrops().clear();
		e.setDroppedExp(0);

		// Apply new drops
		for (ItemStack item : newDrops) {
			e.getDrops().add(item);
		}
		Player pl = Bukkit.getPlayer(players.get(e.getEntity()));
		if (pl == null || !pl.isValid() || !pl.isOnline()) return;
		turnOff(pl);
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent e) {
		if (e.getFrom().getBlock().equals(e.getTo().getBlock())) return;
		Player pl = e.getPlayer();
		if (!isActive(pl)) return;
		LivingEntity minion = minions.get(pl.getUniqueId());

		if ((pl.getWorld().equals(minion.getWorld()) && pl.getLocation().distanceSquared(minion.getLocation()) > maxDistance * maxDistance) || targets.get(pl.getUniqueId()) == null || !targets.containsKey(pl.getUniqueId())) {

			// The minion has a target, but he is far away from his owner, remove his current target
			if (targets.get(pl.getUniqueId()) != null) {
				targets.remove(pl.getUniqueId());
				MobUtil.setTarget(minion, null);
			}

			// The distance between minion and his owner is greater that the defined max distance or the minion has no targets, he will follow his owner
			Location loc = pl.getLocation().clone();
			loc.add(loc.getDirection().setY(0).normalize().multiply(followRange));
			((Mob) minions.get(pl.getUniqueId())).getPathfinder().moveTo(loc, followSpeed);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityCombust(EntityCombustEvent e) {
		if (!preventCombust || !isMinion(e.getEntity())) return;
		e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityUnload(ChunkUnloadEvent event) {
		Chunk chunk = event.getChunk();
		Player owner;

		for (LivingEntity minion : minions.values()) {
			if (!minion.isValid()) continue;
			if (minion.getLocation().getChunk() != chunk) continue;

			owner = Bukkit.getPlayer(players.get(minion));
			if (owner == null) continue;
			if (!owner.isOnline()) continue;
			if (owner.isDead()) continue;

			minion.teleport(owner);
		}
	}

	public Map<UUID, LivingEntity> getMinions() {
		return minions;
	}

	public Map<LivingEntity, UUID> getPlayers() {
		return players;
	}

	public Map<UUID, LivingEntity> getTargets() {
		return targets;
	}

	public List<PotionEffect> getPotionEffects() {
		return potionEffects;
	}

	public Set<AttributeManager.AttributeInfo> getAttributes() {
		if (attributes == null) return null;
		if (!attributes.isConstant()) return null;
		return attributes.get((SpellData) null);
	}

	public ValidTargetList getMinionTargetList() {
		return minionTargetList;
	}

	public void setMinionTargetList(ValidTargetList minionTargetList) {
		this.minionTargetList = minionTargetList;
	}

	public EntityType[] getCreatureTypes() {
		return creatureTypes;
	}

	public void setCreatureTypes(EntityType[] creatureTypes) {
		this.creatureTypes = creatureTypes;
	}

	public boolean shouldPowerAffectHealth() {
		return Boolean.TRUE.equals(powerAffectsHealth.get(null));
	}

	public void setPowerAffectsHealth(boolean powerAffectsHealth) {
		this.powerAffectsHealth = (c, t, l, p, a) -> powerAffectsHealth;
	}

	public boolean shouldPreventCombust() {
		return preventCombust;
	}

	public void setPreventCombust(boolean preventCombust) {
		this.preventCombust = preventCombust;
	}

	public boolean hasGravity() {
		return gravity;
	}

	public void setGravity(boolean gravity) {
		this.gravity = gravity;
	}

	public boolean isBaby() {
		return baby;
	}

	public void setBaby(boolean baby) {
		this.baby = baby;
	}

	public double getPowerHealthFactor() {
		return powerHealthFactor.get(null);
	}

	public void setPowerHealthFactor(double powerHealthFactor) {
		this.powerHealthFactor = (c, t, l, p, a) -> powerHealthFactor;
	}

	public double getMaxHealth() {
		return maxHealth.get(null);
	}

	public void setMaxHealth(double maxHealth) {
		this.maxHealth = (c, t, l, p, a) -> maxHealth;
	}

	public double getHealth() {
		return health.get(null);
	}

	public void setHealth(double health) {
		this.health = (c, t, l, p, a) -> health;
	}

	public String getMinionName() {
		return minionName;
	}

	public void setMinionName(String minionName) {
		this.minionName = minionName;
	}

	public Vector getSpawnOffset() {
		return spawnOffset;
	}

	public void setSpawnOffset(Vector spawnOffset) {
		this.spawnOffset = spawnOffset;
	}

	public double getFollowRange() {
		return followRange;
	}

	public void setFollowRange(double followRange) {
		this.followRange = followRange;
	}

	public float getFollowSpeed() {
		return followSpeed;
	}

	public void setFollowSpeed(float followSpeed) {
		this.followSpeed = followSpeed;
	}

	public float getMaxDistance() {
		return maxDistance;
	}

	public void setMaxDistance(float maxDistance) {
		this.maxDistance = maxDistance;
	}

	public Subspell getSpawnSpell() {
		return spawnSpell;
	}

	public void setSpawnSpell(Subspell spawnSpell) {
		this.spawnSpell = spawnSpell;
	}

	public Subspell getDeathSpell() {
		return deathSpell;
	}

	public void setDeathSpell(Subspell deathSpell) {
		this.deathSpell = deathSpell;
	}

	public Subspell getAttackSpell() {
		return attackSpell;
	}

	public void setAttackSpell(Subspell attackSpell) {
		this.attackSpell = attackSpell;
	}

	public ItemStack getMainHandItem() {
		return mainHandItem;
	}

	public void setMainHandItem(ItemStack mainHandItem) {
		this.mainHandItem = mainHandItem;
	}

	public ItemStack getOffHandItem() {
		return offHandItem;
	}

	public void setOffHandItem(ItemStack offHandItem) {
		this.offHandItem = offHandItem;
	}

	public ItemStack getHelmet() {
		return helmet;
	}

	public void setHelmet(ItemStack helmet) {
		this.helmet = helmet;
	}

	public ItemStack getChestplate() {
		return chestplate;
	}

	public void setChestplate(ItemStack chestplate) {
		this.chestplate = chestplate;
	}

	public ItemStack getLeggings() {
		return leggings;
	}

	public void setLeggings(ItemStack leggings) {
		this.leggings = leggings;
	}

	public ItemStack getBoots() {
		return boots;
	}

	public void setBoots(ItemStack boots) {
		this.boots = boots;
	}

	public float getMainHandItemDropChance() {
		return mainHandItemDropChance;
	}

	public void setMainHandItemDropChance(float mainHandItemDropChance) {
		this.mainHandItemDropChance = mainHandItemDropChance;
	}

	public float getOffHandItemDropChance() {
		return offHandItemDropChance;
	}

	public void setOffHandItemDropChance(float offHandItemDropChance) {
		this.offHandItemDropChance = offHandItemDropChance;
	}

	public float getHelmetDropChance() {
		return helmetDropChance;
	}

	public void setHelmetDropChance(float helmetDropChance) {
		this.helmetDropChance = helmetDropChance;
	}

	public float getChestplateDropChance() {
		return chestplateDropChance;
	}

	public void setChestplateDropChance(float chestplateDropChance) {
		this.chestplateDropChance = chestplateDropChance;
	}

	public float getLeggingsDropChance() {
		return leggingsDropChance;
	}

	public void setLeggingsDropChance(float leggingsDropChance) {
		this.leggingsDropChance = leggingsDropChance;
	}

	public float getBootsDropChance() {
		return bootsDropChance;
	}

	public void setBootsDropChance(float bootsDropChance) {
		this.bootsDropChance = bootsDropChance;
	}

}
