package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.ArrayList;

/** Bounded, encounter-ordered selection from eight nested search boxes. */
final class MagnetItemBuckets<T> {

	private static final int BUCKET_COUNT = 8;

	private final List<List<T>> buckets = new ArrayList<>(BUCKET_COUNT);
	private final double radius;
	private final int limit;

	MagnetItemBuckets(double radius, int limit) {
		this.radius = radius;
		this.limit = limit;
		for (int i = 0; i < BUCKET_COUNT; i++)
			buckets.add(new ArrayList<>());
	}

	void add(T item, double dx, double dy, double dz) {
		if (limit <= 0)
			return;

		// Match queries at radius / 8, 2 * radius / 8, etc. without querying eight times.
		double distance = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
		int index = radius > 0 ? (int) Math.ceil(distance / radius * BUCKET_COUNT) - 1 : 0;
		index = Math.clamp(index, 0, BUCKET_COUNT - 1);
		List<T> bucket = buckets.get(index);
		// Later items in this bucket can never displace these first limit items.
		if (bucket.size() < limit)
			bucket.add(item);
	}

	List<T> select() {
		List<T> selected = new ArrayList<>();
		for (List<T> bucket : buckets) {
			for (T item : bucket) {
				if (selected.size() >= limit)
					return selected;
				selected.add(item);
			}
		}
		return selected;
	}

}
