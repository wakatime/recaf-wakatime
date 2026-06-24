package github.antipaster.cli;

import github.antipaster.config.WakaTime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WakaConfig {
	private WakaConfig() {
	}

	public static boolean hasApiKey() {
		String env = System.getenv("WAKATIME_API_KEY");
		if (env != null && !env.isBlank())
			return true;
		return readApiKey().isPresent();
	}

	public static Optional<String> readApiKey() {
		Path file = WakaTime.CONFIG_FILE;
		if (!Files.isRegularFile(file))
			return Optional.empty();
		try {
			boolean inSettings = false;
			for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				String line = raw.trim();
				if (line.isEmpty() || line.startsWith("#") || line.startsWith(";"))
					continue;
				if (isSection(line)) {
					inSettings = sectionName(line).equalsIgnoreCase("settings");
					continue;
				}
				if (!inSettings)
					continue;
				int eq = line.indexOf('=');
				if (eq < 0)
					continue;
				if (line.substring(0, eq).trim().equalsIgnoreCase("api_key")) {
					String value = line.substring(eq + 1).trim();
					if (!value.isEmpty())
						return Optional.of(value);
				}
			}
		} catch (IOException ignored) {
		}
		return Optional.empty();
	}

	public static void writeApiKey(String apiKey) {
		Path file = WakaTime.CONFIG_FILE;
		try {
			List<String> lines = Files.isRegularFile(file)
					? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
					: new ArrayList<>();
			if (!replaceInSettings(lines, apiKey))
				appendSettings(lines, apiKey);
			Files.write(file, lines, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static boolean replaceInSettings(List<String> lines, String apiKey) {
		boolean inSettings = false;
		boolean sawSettings = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (isSection(line)) {
				if (inSettings) {
					// Leaving the settings section without finding the key; insert it here.
					lines.add(i, "api_key = " + apiKey);
					return true;
				}
				inSettings = sectionName(line).equalsIgnoreCase("settings");
				sawSettings |= inSettings;
				continue;
			}
			if (inSettings) {
				int eq = line.indexOf('=');
				if (eq >= 0 && line.substring(0, eq).trim().equalsIgnoreCase("api_key")) {
					lines.set(i, "api_key = " + apiKey);
					return true;
				}
			}
		}
		if (inSettings || sawSettings) {
			lines.add("api_key = " + apiKey);
			return true;
		}
		return false;
	}

	private static void appendSettings(List<String> lines, String apiKey) {
		if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank())
			lines.add("");
		lines.add("[settings]");
		lines.add("api_key = " + apiKey);
	}

	private static boolean isSection(String line) {
		return line.startsWith("[") && line.endsWith("]");
	}

	private static String sectionName(String line) {
		return line.substring(1, line.length() - 1).trim();
	}
}
