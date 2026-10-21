package com.comedymonkey.postrider.testutil;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

/** Finds the mod's own resource files in tests: through the loaded mod file, or else in src/main/resources. */
public final class ModResourceFiles {
	private ModResourceFiles() {}

	/** The resource at this path under the mod's resources (e.g. "assets", "postrider", "lang", "en_us.json"), or null. */
	@Nullable
	public static Path find(String... path) {
		try {
			IModFileInfo info = ModList.get().getModFileById(PostRider.MOD_ID);
			if (info != null) {
				Path found = info.getFile().findResource(path);
				if (found != null && Files.exists(found))
					return found;
			}
		} catch (RuntimeException ignored) {
			// Fall back to the source tree
		}
		Path source = Path.of("src", "main", "resources").resolve(String.join("/", path));
		return Files.exists(source) ? source : null;
	}

	public static JsonElement readJson(Path file) {
		try (Reader reader = Files.newBufferedReader(file)) {
			return JsonParser.parseReader(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
