package com.comedymonkey.postrider.testutil;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Finds the mod's own resource files in tests, on any mod loader: on the classpath (where the loader put the mod's
 * resources), or else in the source tree (common/src/main/resources).
 */
public final class ModResourceFiles {
	private ModResourceFiles() {}

	/** The resource at this path under the mod's resources (e.g. "assets", "postrider", "lang", "en_us.json"), or null. */
	@Nullable
	public static Path find(String... path) {
		String name = String.join("/", path);
		// Every copy on the classpath, skipping jars: the mod's own resources are a folder in tests, and a jar's copy of a
		// file in a shared namespace is someone else's (NeoForge 21.1's jar has vanilla's assets/minecraft/atlases/blocks.json,
		// and its test classpath reads it like a file, ahead of the mod's)
		try {
			for (URL url : java.util.Collections.list(ModResourceFiles.class.getClassLoader().getResources(name))) {
				if (url.toString().contains(".jar"))
					continue;
				try {
					Path found = Path.of(url.toURI());
					if (Files.exists(found))
						return found;
				} catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException ignored) {
					// Not a plain file: try the next, then the source tree
				}
			}
		} catch (IOException ignored) {
			// Fall back to the source tree
		}
		// The tests may run in a folder below the project's (a run folder), so look up from there for the resources
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
			for (String resources : new String[] {"common/src/main/resources", "src/main/resources"}) {
				Path source = dir.resolve(resources).resolve(name);
				if (Files.exists(source))
					return source;
			}
		return null;
	}

	public static JsonElement readJson(Path file) {
		try (Reader reader = Files.newBufferedReader(file)) {
			return JsonParser.parseReader(reader);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
