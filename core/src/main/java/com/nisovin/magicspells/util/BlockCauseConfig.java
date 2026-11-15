package com.nisovin.magicspells.util;

import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.function.Consumer;

import com.sneakyutils.util.AlteredBlockCauses;

/** Reads AlteredBlockManager causes ({@code block-causes}, UndoSpell {@code causes}) from config strings. */
public final class BlockCauseConfig {

	private BlockCauseConfig() {}

	/**
	 * Trimmed, lower-cased causes from {@code raw} that are in SneakyUtils' fixed {@link AlteredBlockCauses} set.
	 * Each other entry is passed to {@code onInvalid} as written and dropped.
	 */
	public static Set<String> parse(List<String> raw, Consumer<String> onInvalid) {
		Set<String> causes = new HashSet<>();
		for (String entry : raw) {
			if (entry == null) continue;
			String cause = entry.trim().toLowerCase(Locale.ROOT);
			if (AlteredBlockCauses.isValid(cause)) causes.add(cause);
			else onInvalid.accept(entry);
		}
		return Set.copyOf(causes);
	}

}
