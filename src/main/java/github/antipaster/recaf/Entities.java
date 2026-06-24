package github.antipaster.recaf;

import github.antipaster.config.WakaTime;
import github.antipaster.model.Heartbeat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Builds WakaTime heartbeats for Recaf classes. Recaf has no real files on disk for the
 * classes inside a workspace, so we mirror each class as a {@code .java} file under
 * {@link WakaTime#CACHE_DIR} and point the heartbeat's entity at it. The displayed source
 * (decompiled output or assembler text) is written there so wakatime-cli can read accurate
 * line counts; the project name and language are supplied explicitly.
 */
public final class Entities {
	private Entities() {
	}

	public static Heartbeat forClass(String project, String internalName, String text,
									 int lineNumber, int cursorPosition, boolean write) {
		Path file = cacheFile(project, internalName);
		writeText(file, text != null && !text.isEmpty() ? text : "// " + internalName.replace('/', '.'));
		return new Heartbeat(
				file.toAbsolutePath().toString(),
				project,
				WakaTime.LANGUAGE,
				WakaTime.CATEGORY,
				lineNumber,
				cursorPosition,
				write,
				Instant.now().getEpochSecond());
	}

	private static Path cacheFile(String project, String internalName) {
		Path path = WakaTime.CACHE_DIR.resolve(sanitize(project));
		for (String part : (internalName + ".java").split("/"))
			path = path.resolve(sanitize(part));
		return path;
	}

	private static void writeText(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static String sanitize(String value) {
		String cleaned = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals(".."))
			return "_";
		return cleaned;
	}
}
