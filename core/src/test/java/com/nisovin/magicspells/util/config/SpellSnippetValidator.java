package com.nisovin.magicspells.util.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.yaml.snakeyaml.Yaml;

/**
 * Index-backed checks for spell YAML surfaces without a live Paper server.
 * Validates {@code spell-class} and top-level option keys against
 * {@code spell-option-index.json} (regenerate via
 * {@code scripts/wiki/export-test-spell-index.py}).
 */
public final class SpellSnippetValidator {

	private static final String DEFAULT_INDEX =
		"/com/nisovin/magicspells/util/config/spell-option-index.json";

	private final Set<String> sharedKeys;
	private final Map<String, Set<String>> spellOptions;

	public SpellSnippetValidator(Set<String> sharedKeys, Map<String, Set<String>> spellOptions) {
		this.sharedKeys = Set.copyOf(sharedKeys);
		Map<String, Set<String>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Set<String>> entry : spellOptions.entrySet()) {
			copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
		}
		this.spellOptions = Collections.unmodifiableMap(copy);
	}

	public static SpellSnippetValidator loadDefault() {
		try (InputStream in = SpellSnippetValidator.class.getResourceAsStream(DEFAULT_INDEX)) {
			if (in == null) {
				throw new IllegalStateException("Missing classpath resource " + DEFAULT_INDEX
					+ "; run python3 scripts/wiki/export-test-spell-index.py");
			}
			return load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load " + DEFAULT_INDEX, e);
		}
	}

	public static SpellSnippetValidator load(Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		Set<String> shared = new HashSet<>();
		JsonArray sharedArr = root.getAsJsonArray("sharedKeys");
		if (sharedArr != null) {
			for (JsonElement el : sharedArr) {
				shared.add(el.getAsString());
			}
		}
		Map<String, Set<String>> spells = new LinkedHashMap<>();
		JsonObject spellsObj = root.getAsJsonObject("spells");
		if (spellsObj != null) {
			for (Map.Entry<String, JsonElement> entry : spellsObj.entrySet()) {
				Set<String> keys = new HashSet<>();
				for (JsonElement key : entry.getValue().getAsJsonArray()) {
					keys.add(key.getAsString());
				}
				spells.put(entry.getKey(), keys);
			}
		}
		return new SpellSnippetValidator(shared, spells);
	}

	public Set<String> knownSpellClasses() {
		return spellOptions.keySet();
	}

	/**
	 * Validates a MagicSpells spells document (top-level keys are internal spell names).
	 */
	public List<String> validateYaml(String yaml) {
		Objects.requireNonNull(yaml, "yaml");
		Object loaded = new Yaml().load(yaml);
		if (loaded == null) {
			return List.of("YAML document is empty");
		}
		if (!(loaded instanceof Map<?, ?> map)) {
			return List.of("YAML root must be a mapping of spell-name → options");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> root = (Map<String, Object>) map;
		return validateDocument(root);
	}

	public List<String> validateDocument(Map<String, Object> root) {
		Objects.requireNonNull(root, "root");
		List<String> issues = new ArrayList<>();
		for (Map.Entry<String, Object> entry : root.entrySet()) {
			String name = entry.getKey();
			Object value = entry.getValue();
			if (!(value instanceof Map<?, ?>)) {
				issues.add(name + ": spell body must be a mapping");
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> options = (Map<String, Object>) value;
			issues.addAll(validateSpell(name, options));
		}
		return issues;
	}

	public List<String> validateSpell(String internalName, Map<String, Object> options) {
		Objects.requireNonNull(internalName, "internalName");
		Objects.requireNonNull(options, "options");
		List<String> issues = new ArrayList<>();
		Object spellClassRaw = options.get("spell-class");
		if (spellClassRaw == null) {
			issues.add(internalName + ": missing spell-class");
			return issues;
		}
		if (!(spellClassRaw instanceof String spellClass) || spellClass.isBlank()) {
			issues.add(internalName + ": spell-class must be a non-empty string");
			return issues;
		}
		String normalized = normalizeSpellClass(spellClass);
		Set<String> typeKeys = spellOptions.get(normalized);
		if (typeKeys == null) {
			issues.add(internalName + ": unknown spell-class '" + spellClass + "'");
			return issues;
		}
		Set<String> allowed = new HashSet<>(sharedKeys);
		allowed.addAll(typeKeys);
		Set<String> unknown = new TreeSet<>();
		for (String key : options.keySet()) {
			if (!allowed.contains(key)) {
				unknown.add(key);
			}
		}
		for (String key : unknown) {
			issues.add(internalName + ": unknown option '" + key + "' for " + normalized);
		}
		return issues;
	}

	static String normalizeSpellClass(String spellClass) {
		String trimmed = spellClass.trim();
		if (!trimmed.startsWith(".")) {
			return "." + trimmed;
		}
		return trimmed;
	}

}
