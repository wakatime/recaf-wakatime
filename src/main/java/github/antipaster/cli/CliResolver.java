package github.antipaster.cli;

import github.antipaster.config.WakaTime;
import github.antipaster.util.Net;
import github.antipaster.util.Platform;
import github.antipaster.util.Proc;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CliResolver {
	private static final Logger logger = Logging.get(CliResolver.class);

	public Optional<Path> resolve() {
		Path cli = WakaTime.WAKA_DIR.resolve(Platform.cliFileName());
		try {
			Files.createDirectories(WakaTime.WAKA_DIR);
			if (!Files.isRegularFile(cli)) {
				logger.info("Downloading wakatime-cli ({})", Platform.cliAssetName());
				downloadLatest(cli);
				return Optional.of(cli);
			}
			if (shouldCheckForUpdate()) {
				try {
					maybeUpdate(cli);
				} finally {
					touchLastCheck();
				}
			}
			return Optional.of(cli);
		} catch (Exception e) {
			logger.error("Failed to prepare wakatime-cli", e);
			return Files.isRegularFile(cli) ? Optional.of(cli) : Optional.empty();
		}
	}

	private void maybeUpdate(Path cli) throws IOException, InterruptedException {
		Optional<String> latest = Net.latestTag(WakaTime.GITHUB_LATEST_API, WakaTime.USER_AGENT);
		if (latest.isEmpty())
			return;
		String want = normalize(latest.get());
		String have = normalize(localVersion(cli));
		if (have == null || !have.equals(want)) {
			logger.info("Updating wakatime-cli to {}", latest.get());
			downloadLatest(cli);
		}
	}

	private void downloadLatest(Path cli) throws IOException, InterruptedException {
		Path zip = Files.createTempFile("wakatime-cli", ".zip");
		try {
			Net.download(WakaTime.GITHUB_DOWNLOAD + Platform.cliAssetName(), zip, WakaTime.USER_AGENT);
			extractBinary(zip, cli);
			makeExecutable(cli);
		} finally {
			Files.deleteIfExists(zip);
		}
	}

	private void extractBinary(Path zip, Path cli) throws IOException {
		try (ZipInputStream stream = new ZipInputStream(Files.newInputStream(zip))) {
			ZipEntry entry;
			while ((entry = stream.getNextEntry()) != null) {
				if (entry.isDirectory())
					continue;
				Files.copy(stream, cli, StandardCopyOption.REPLACE_EXISTING);
				return;
			}
		}
		throw new IOException("wakatime-cli archive contained no files");
	}

	private void makeExecutable(Path cli) {
		if (Platform.isWindows())
			return;
		try {
			Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwxr-xr-x"));
		} catch (Exception ignored) {
		}
	}

	private String localVersion(Path cli) {
		return Proc.runCapture(List.of(cli.toString(), "--version"), 15);
	}

	private boolean shouldCheckForUpdate() {
		try {
			if (!Files.isRegularFile(WakaTime.LAST_UPDATE_CHECK_FILE))
				return true;
			long last = Long.parseLong(Files.readString(WakaTime.LAST_UPDATE_CHECK_FILE).trim());
			return Instant.now().getEpochSecond() - last >= WakaTime.UPDATE_CHECK_INTERVAL.toSeconds();
		} catch (Exception e) {
			return true;
		}
	}

	private void touchLastCheck() {
		try {
			Files.writeString(WakaTime.LAST_UPDATE_CHECK_FILE, Long.toString(Instant.now().getEpochSecond()));
		} catch (IOException ignored) {
		}
	}

	private static String normalize(String version) {
		if (version == null)
			return null;
		String value = version.trim();
		int space = value.indexOf(' ');
		if (space > 0)
			value = value.substring(0, space);
		if (value.startsWith("v") || value.startsWith("V"))
			value = value.substring(1);
		return value.isEmpty() ? null : value;
	}
}
