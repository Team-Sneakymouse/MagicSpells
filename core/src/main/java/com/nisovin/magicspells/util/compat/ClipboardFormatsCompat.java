package com.nisovin.magicspells.util.compat;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;

/**
 * WorldEdit 7.4+ prefers {@code ClipboardFormats.findByPath}; older server jars only
 * have {@code findByFile}. Resolve at runtime so compile-against-7.4 jars still load.
 */
public final class ClipboardFormatsCompat {

	private static final Method FIND_BY_PATH = resolveFindByPath();

	private ClipboardFormatsCompat() {
	}

	@Nullable
	public static ClipboardFormat find(File file) {
		if (file == null) return null;
		if (FIND_BY_PATH != null) {
			try {
				return (ClipboardFormat) FIND_BY_PATH.invoke(null, file.toPath());
			} catch (ReflectiveOperationException ignored) {
				// fall through
			}
		}
		return findByFile(file);
	}

	@SuppressWarnings("deprecation")
	private static ClipboardFormat findByFile(File file) {
		return ClipboardFormats.findByFile(file);
	}

	@Nullable
	private static Method resolveFindByPath() {
		try {
			return ClipboardFormats.class.getMethod("findByPath", Path.class);
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

}
