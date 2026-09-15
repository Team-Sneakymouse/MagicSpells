package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagnetItemBucketsTest {

	@Test
	void prioritizesInnerBoxesEvenWhenOuterItemsArriveFirst() {
		MagnetItemBuckets<Integer> buckets = new MagnetItemBuckets<>(8, 3);
		for (int i = 8; i > 0; i--)
			buckets.add(i, i, 0, 0);

		assertEquals(List.of(1, 2, 3), buckets.select());
	}

	@Test
	void preservesEncounterOrderWithinABoxInsteadOfSorting() {
		MagnetItemBuckets<String> buckets = new MagnetItemBuckets<>(8, 2);
		buckets.add("first", 0.9, 0, 0);
		buckets.add("second", 0.1, 0, 0);
		buckets.add("third", 0, 0, 0);

		assertEquals(List.of("first", "second"), buckets.select());
	}

	@Test
	void usesAllAxesAndIncludesBoxBoundaries() {
		MagnetItemBuckets<String> buckets = new MagnetItemBuckets<>(8, 3);
		buckets.add("outer", 0, 0, -1.01);
		buckets.add("corner", -1, 1, -1);
		buckets.add("center", 0, 0, 0);

		assertEquals(List.of("corner", "center", "outer"), buckets.select());
	}

	@Test
	void fillsRemainingCapacityFromSuccessiveBuckets() {
		MagnetItemBuckets<String> buckets = new MagnetItemBuckets<>(8, 4);
		buckets.add("far", 8, 0, 0);
		buckets.add("middle-first", 4, 0, 0);
		buckets.add("near", 1, 0, 0);
		buckets.add("middle-second", 3.5, 0, 0);
		buckets.add("middle-third", 3.2, 0, 0);

		assertEquals(List.of("near", "middle-first", "middle-second", "middle-third"), buckets.select());
	}

	@Test
	void limitsThousandsOfCandidatesToOneHundred() {
		MagnetItemBuckets<Integer> buckets = new MagnetItemBuckets<>(8, 100);
		for (int i = 0; i < 10_000; i++)
			buckets.add(i, i % 8 + 0.5, 0, 0);

		List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < 100; i++)
			expected.add(i * 8);
		assertEquals(expected, buckets.select());
	}

	@Test
	void supportsLimitsAboveOneHundredWithoutAllocatingTheConfiguredMaximum() {
		MagnetItemBuckets<Integer> buckets = new MagnetItemBuckets<>(8, Integer.MAX_VALUE);
		List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < 1_000; i++) {
			buckets.add(i, 1, 0, 0);
			expected.add(i);
		}

		assertEquals(expected, buckets.select());
	}

	@Test
	void handlesEmptyInputAndNonpositiveLimits() {
		assertTrue(new MagnetItemBuckets<>(8, 100).select().isEmpty());
		for (int limit : List.of(0, -1)) {
			MagnetItemBuckets<String> buckets = new MagnetItemBuckets<>(8, limit);
			buckets.add("item", 0, 0, 0);
			assertTrue(buckets.select().isEmpty());
		}
	}

	@Test
	void handlesZeroRadiusAndQueryResultsWhoseCentersExtendBeyondTheRadius() {
		MagnetItemBuckets<String> zero = new MagnetItemBuckets<>(0, 1);
		zero.add("center", 0, 0, 0);
		assertEquals(List.of("center"), zero.select());

		MagnetItemBuckets<String> buckets = new MagnetItemBuckets<>(8, 2);
		buckets.add("overlapping-entity", 8.1, 0, 0);
		buckets.add("near", 1, 0, 0);
		assertEquals(List.of("near", "overlapping-entity"), buckets.select());
	}

}
