package com.nisovin.magicspells.util.config;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellSnippetValidatorTest {

	private static SpellSnippetValidator validator;

	@BeforeAll
	static void loadIndex() {
		validator = SpellSnippetValidator.loadDefault();
	}

	@Test
	void indexContainsKnownSpellClasses() {
		assertTrue(validator.knownSpellClasses().contains(".instant.DummySpell")
			|| validator.knownSpellClasses().contains(".buff.DummySpell")
			|| validator.knownSpellClasses().stream().anyMatch(c -> c.endsWith(".DummySpell")));
		assertTrue(validator.knownSpellClasses().contains(".instant.ConjureSpell"));
	}

	@Test
	void acceptsValidConjureSnippet() {
		List<String> issues = validator.validateYaml("""
			give_sword:
			  spell-class: .instant.ConjureSpell
			  add-to-inventory: true
			  cooldown: 5
			""");
		assertEquals(List.of(), issues);
	}

	@Test
	void normalizesSpellClassWithoutLeadingDot() {
		List<String> issues = validator.validateSpell("x", Map.of(
			"spell-class", "instant.ConjureSpell",
			"add-to-inventory", true
		));
		assertEquals(List.of(), issues);
	}

	@Test
	void rejectsUnknownSpellClass() {
		List<String> issues = validator.validateYaml("""
			bad:
			  spell-class: .instant.NotARealSpell
			""");
		assertEquals(1, issues.size());
		assertTrue(issues.get(0).contains("unknown spell-class"));
	}

	@Test
	void rejectsUnknownOptionKey() {
		List<String> issues = validator.validateYaml("""
			bad:
			  spell-class: .instant.ConjureSpell
			  not-a-real-option: true
			""");
		assertFalse(issues.isEmpty());
		assertTrue(issues.get(0).contains("unknown option 'not-a-real-option'"));
	}

	@Test
	void requiresSpellClass() {
		List<String> issues = validator.validateSpell("orphan", Map.of("cooldown", 1));
		assertEquals(List.of("orphan: missing spell-class"), issues);
	}

}
