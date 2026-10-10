package com.nisovin.magicspells.spells.instant;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MobUtil;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class SteedSpell extends InstantSpell {

	private final Map<UUID, Integer> mounted;

	private boolean gravity;
	private boolean hasChest;

	private ConfigData<Double> jumpStrength;

	private String strInvalidType;
	private String strAlreadyMounted;

	private EntityType type;

	private Horse.Color color;
	private Horse.Style style;

	private ItemStack armor;

	private Subspell spellOnSpawn;
	private String spellOnSpawnName;

	public SteedSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		mounted = new HashMap<>();

		gravity = getConfigBoolean("gravity", true);
		hasChest = getConfigBoolean("has-chest", false);

		jumpStrength = getConfigDataDouble("jump-strength", 1);

		strInvalidType = getConfigString("str-invalid-type", "Invalid entity type.");
		spellOnSpawnName = getConfigString("spell-on-spawn", null);
		strAlreadyMounted = getConfigString("str-already-mounted", "You are already mounted!");

		type = MobUtil.getEntityType(getConfigString("type", "horse"));

		if (type == EntityType.HORSE) {
			String c = getConfigString("color", "");
			String s = getConfigString("style", "");
			String a = getConfigString("armor", "");
			if (!c.isEmpty()) {
				for (Horse.Color h : Horse.Color.values()) {
					if (!h.name().equalsIgnoreCase(c)) continue;
					color = h;
					break;
				}
				if (color == null) DebugHandler.debugBadEnumValue(Horse.Color.class, c);
			}
			if (!s.isEmpty()) {
				for (Horse.Style h : Horse.Style.values()) {
					if (!h.name().equalsIgnoreCase(s)) continue;
					style = h;
					break;
				}
				if (style == null) DebugHandler.debugBadEnumValue(Horse.Style.class, s);
			}
			if (!a.isEmpty()) {
				MagicItem magicItem = MagicItems.getMagicItemFromString(a);
				if (magicItem != null) armor = magicItem.getItemStack();
			}
		}
	}

	@Override
	protected void initialize() {
		super.initialize();

		if (spellOnSpawnName != null) {
			spellOnSpawn = new Subspell(spellOnSpawnName);

			if (!spellOnSpawn.process()) {
				MagicSpells.error("SpawnEntitySpell '" + internalName + "' has an invalid spell-on-spawn '" + spellOnSpawnName + "' defined!");
				spellOnSpawn = null;
			}

			spellOnSpawnName = null;
		}
	}

	@Override
	public void turnOff() {
		for (UUID id : mounted.keySet()) {
			Player player = Bukkit.getPlayer(id);
			if (player == null) continue;
			if (player.getVehicle() == null) continue;
			player.getVehicle().eject();
		}
		mounted.clear();
	}

	@Override
	public CastResult cast(SpellData data) {
		LivingEntity caster = data.caster();
		float power = data.power();
		String[] args = data.args();

		if (caster.getVehicle() != null) {
			sendMessage(strAlreadyMounted, caster, args);
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}

		Class<? extends Entity> entityClass = type == null ? null : type.getEntityClass();
		if (entityClass == null) {
			sendMessage(strInvalidType, caster, args);
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}

		Entity entity = caster.getWorld().spawn(caster.getLocation(), entityClass, e -> {
			e.setGravity(gravity);

			if (e instanceof AbstractHorse abstractHorse) {
				abstractHorse.setAdult();
				abstractHorse.setTamed(true);
				if (caster instanceof AnimalTamer tamer)
					abstractHorse.setOwner(tamer);
				abstractHorse.setJumpStrength(jumpStrength.get(caster, null, power, args));
				abstractHorse.getInventory().setSaddle(new ItemStack(Material.SADDLE));

				if (abstractHorse instanceof Horse horse) {
					if (color != null)
						horse.setColor(color);
					else
						horse.setColor(Horse.Color.values()[random.nextInt(Horse.Color.values().length)]);
					if (style != null)
						horse.setStyle(style);
					else
						horse.setStyle(Horse.Style.values()[random.nextInt(Horse.Style.values().length)]);
					if (armor != null)
						horse.getInventory().setArmor(armor);
				} else if (abstractHorse instanceof ChestedHorse chestedHorse) {
					chestedHorse.setCarryingChest(hasChest);
				}
			}
		});
		entity.addPassenger(caster);

		if (entity instanceof LivingEntity le)
			spellOnSpawn.subcast(new SpellData(caster, le, power, args));
		else
			spellOnSpawn.subcast(new SpellData(caster, entity.getLocation(), power, args));

		playSpellEffects(EffectPosition.CASTER, caster, data);
		mounted.put(caster.getUniqueId(), entity.getEntityId());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}
	
	@EventHandler
	private void onDamage(EntityDamageEvent event) {
		if (mounted.containsValue(event.getEntity().getEntityId())) event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onInventoryInteract(InventoryClickEvent event) {
		Player pl = (Player) event.getWhoClicked();
		Inventory inv = event.getInventory();
		if (inv.getType() != InventoryType.CHEST) return;
		if (!mounted.containsKey(pl.getUniqueId())) return;
		event.setCancelled(true);
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onDismount(EntityDismountEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (!mounted.containsKey(player.getUniqueId())) return;
		mounted.remove(player.getUniqueId());
		event.getDismounted().remove();
		playSpellEffects(EffectPosition.DISABLED, player, new SpellData(player));
	}
	
	@EventHandler
	private void onDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		if (!mounted.containsKey(player.getUniqueId())) return;
		if (player.getVehicle() == null) return;
		mounted.remove(player.getUniqueId());
		Entity vehicle = player.getVehicle();
		vehicle.eject();
		vehicle.remove();
	}
	
	@EventHandler
	private void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (!mounted.containsKey(player.getUniqueId())) return;
		if (player.getVehicle() == null) return;
		mounted.remove(player.getUniqueId());
		Entity vehicle = player.getVehicle();
		vehicle.eject();
		vehicle.remove();
	}

	public Map<UUID, Integer> getMounted() {
		return mounted;
	}

	public boolean hasGravity() {
		return gravity;
	}

	public void setGravity(boolean gravity) {
		this.gravity = gravity;
	}

	public boolean hasChest() {
		return hasChest;
	}

	public void setHasChest(boolean hasChest) {
		this.hasChest = hasChest;
	}

	public String getStrAlreadyMounted() {
		return strAlreadyMounted;
	}

	public void setStrAlreadyMounted(String strAlreadyMounted) {
		this.strAlreadyMounted = strAlreadyMounted;
	}

	public EntityType getType() {
		return type;
	}

	public void setType(EntityType type) {
		this.type = type;
	}

	public Horse.Color getHorseColor() {
		return color;
	}

	public void setHorseColor(Horse.Color color) {
		this.color = color;
	}

	public Horse.Style getHorseStyle() {
		return style;
	}

	public void setHorseStyle(Horse.Style style) {
		this.style = style;
	}

	public ItemStack getArmor() {
		return armor;
	}

	public void setArmor(ItemStack armor) {
		this.armor = armor;
	}

}
