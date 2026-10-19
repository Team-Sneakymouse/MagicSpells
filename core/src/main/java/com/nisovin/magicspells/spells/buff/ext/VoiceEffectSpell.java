package com.nisovin.magicspells.spells.buff.ext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import ca.bungo.sneakyconfigurablevoice.api.VoiceEffect;
import ca.bungo.sneakyconfigurablevoice.api.VoiceEffects;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.castmodifiers.conditions.util.DependsOn;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.config.ConfigData;

/**
 * Applies a SneakyConfigurableVoice preset or an inline effect chain while the buff is active.
 * Requires SneakyConfigurableVoice.
 */
@DependsOn(plugin = "SneakyConfigurableVoice")
public class VoiceEffectSpell extends BuffSpell {

	private final Set<UUID> entities = new HashSet<>();

	private ConfigData<String> preset;
	private List<VoiceEffect> effects;
	private boolean configured;

	public VoiceEffectSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		preset = getConfigDataString("preset", "");
		List<?> rawEffects = getConfigList("effects", null);

		boolean hasEffects = rawEffects != null && !rawEffects.isEmpty();
		boolean constantPreset = preset.isConstant() && !blank(preset.get(null));

		if (hasEffects && (constantPreset || !preset.isConstant())) {
			MagicSpells.error("VoiceEffectSpell '" + internalName
					+ "' defines both 'preset' and 'effects'; use only one.");
			return;
		}

		if (hasEffects) {
			List<VoiceEffect> parsed = new ArrayList<>(rawEffects.size());
			for (int i = 0; i < rawEffects.size(); i++) {
				try {
					parsed.add(parseStage(rawEffects.get(i), i));
				} catch (RuntimeException failure) {
					MagicSpells.error("VoiceEffectSpell '" + internalName + "' effects[" + i + "] invalid: "
							+ failure.getMessage());
					return;
				}
			}
			effects = List.copyOf(parsed);
			configured = true;
			return;
		}

		if (preset.isConstant() && blank(preset.get(null))) {
			MagicSpells.error("VoiceEffectSpell '" + internalName
					+ "' needs a non-blank 'preset' or a non-empty 'effects' list.");
			return;
		}

		configured = true;
	}

	@Override
	public void initialize() {
		super.initialize();
		if (!configured) {
			MagicSpells.error("VoiceEffectSpell '" + internalName + "' is not correctly configured.");
		}
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		if (!configured) return false;

		VoiceEffects service = Bukkit.getServicesManager().load(VoiceEffects.class);
		if (service == null) {
			MagicSpells.error("VoiceEffectSpell '" + internalName
					+ "': VoiceEffects service is not registered.");
			return false;
		}

		if (effects != null) {
			service.setEffects(entity.getUniqueId(), effects);
			entities.add(entity.getUniqueId());
			return true;
		}

		String name = preset.get(entity, entity, power, args);
		if (blank(name)) {
			MagicSpells.error("VoiceEffectSpell '" + internalName + "' resolved an empty preset name.");
			return false;
		}
		if (!service.applyPreset(entity.getUniqueId(), name.trim())) {
			MagicSpells.error("VoiceEffectSpell '" + internalName + "': unknown preset '" + name.trim() + "'.");
			return false;
		}
		entities.add(entity.getUniqueId());
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return entities.contains(entity.getUniqueId());
	}

	@Override
	protected void turnOffBuff(LivingEntity entity) {
		entities.remove(entity.getUniqueId());
		VoiceEffects service = Bukkit.getServicesManager().load(VoiceEffects.class);
		if (service != null) service.clearEffects(entity.getUniqueId());
	}

	@Override
	protected void turnOff() {
		VoiceEffects service = Bukkit.getServicesManager().load(VoiceEffects.class);
		for (UUID id : entities) {
			if (service != null) service.clearEffects(id);
		}
		entities.clear();
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static VoiceEffect parseStage(Object raw, int index) {
		Map<?, ?> map;
		if (raw instanceof Map<?, ?> asMap) {
			map = asMap;
		} else if (raw instanceof ConfigurationSection section) {
			map = section.getValues(false);
		} else {
			throw new IllegalArgumentException("stage " + index + " must be a map with type/params");
		}

		Object typeValue = first(map, "type", "Type");
		if (typeValue == null) throw new IllegalArgumentException("stage " + index + " missing type");
		VoiceEffect.Type type;
		try {
			type = VoiceEffect.Type.valueOf(String.valueOf(typeValue).trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			throw new IllegalArgumentException("stage " + index + " unknown type '" + typeValue + "'");
		}

		return switch (type) {
			case HIGH_PASS, LOW_PASS -> new VoiceEffect(type, number(map, index, "cutoffHz", "hz", "p0"));
			case GAIN -> VoiceEffect.gain(number(map, index, "gain", "p0"));
			case DISTORTION -> VoiceEffect.distortion(number(map, index, "drive", "p0"));
			case BITCRUSH -> VoiceEffect.bitcrush(
					(int) Math.round(number(map, index, "bits", "p0")),
					(int) Math.round(number(map, index, "downsample", "p1")));
			case TREMOLO -> VoiceEffect.tremolo(
					number(map, index, "rateHz", "rate", "p0"),
					number(map, index, "depth", "p1"));
			case ECHO -> VoiceEffect.echo(
					number(map, index, "delayMs", "delay", "p0"),
					number(map, index, "feedback", "p1"),
					number(map, index, "mix", "p2"));
			case REVERB -> VoiceEffect.reverb(
					number(map, index, "room", "p0"),
					number(map, index, "damping", "p1"),
					number(map, index, "mix", "p2"));
			case PITCH -> VoiceEffect.pitch(number(map, index, "ratio", "p0"));
		};
	}

	private static double number(Map<?, ?> map, int index, String... keys) {
		Object value = first(map, keys);
		if (value == null) throw new IllegalArgumentException("stage " + index + " missing " + keys[0]);
		if (value instanceof Number number) return number.doubleValue();
		try {
			return Double.parseDouble(String.valueOf(value).trim());
		} catch (NumberFormatException failure) {
			throw new IllegalArgumentException("stage " + index + " " + keys[0] + " is not a number");
		}
	}

	private static Object first(Map<?, ?> map, String... keys) {
		for (String key : keys) {
			if (map.containsKey(key)) return map.get(key);
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() != null && key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

}
