package com.nisovin.magicspells.util;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockCauseConfigTest {

	@Test
	void keepsKnownCausesNormalised() {
		List<String> invalid = new ArrayList<>();
		Set<String> causes = BlockCauseConfig.parse(List.of("explosion", " Damage ", "FIRE", "decoration"), invalid::add);
		assertEquals(Set.of("explosion", "damage", "fire", "decoration"), causes);
		assertTrue(invalid.isEmpty());
	}

	@Test
	void reportsAndDropsUnknownCauses() {
		List<String> invalid = new ArrayList<>();
		Set<String> causes = BlockCauseConfig.parse(List.of("explosion", "boom", ""), invalid::add);
		assertEquals(Set.of("explosion"), causes);
		assertEquals(List.of("boom", ""), invalid);
	}

	@Test
	void emptyInputGivesNoCauses() {
		assertTrue(BlockCauseConfig.parse(List.of(), entry -> {}).isEmpty());
	}

}
