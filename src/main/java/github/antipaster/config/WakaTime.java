package github.antipaster.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public final class WakaTime {
	public static final Path HOME = Paths.get(System.getProperty("user.home", "."));
	public static final Path WAKA_DIR = HOME.resolve(".wakatime");
	public static final Path CONFIG_FILE = HOME.resolve(".wakatime.cfg");
	public static final Path CACHE_DIR = WAKA_DIR.resolve("recaf-cache");
	public static final Path LAST_UPDATE_CHECK_FILE = WAKA_DIR.resolve(".recaf-last-update-check");

	public static final String GITHUB_LATEST_API = "https://api.github.com/repos/wakatime/wakatime-cli/releases/latest";
	public static final String GITHUB_DOWNLOAD = "https://github.com/wakatime/wakatime-cli/releases/latest/download/";

	public static final String USER_AGENT = "recaf-wakatime";
	public static final String LANGUAGE = "Java";
	public static final String CATEGORY = "coding";
	public static final String DEFAULT_PROJECT = "recaf-workspace";

	public static final Duration HEARTBEAT_THROTTLE = Duration.ofMinutes(2);
	public static final Duration UPDATE_CHECK_INTERVAL = Duration.ofHours(24);

	private WakaTime() {
	}
}
