package github.antipaster.util;

import java.util.Locale;

public final class Platform {
	private Platform() {
	}

	public static boolean isWindows() {
		return osName().contains("win");
	}

	public static boolean isMac() {
		String os = osName();
		return os.contains("mac") || os.contains("darwin");
	}

	public static String osSlug() {
		if (isWindows())
			return "windows";
		if (isMac())
			return "darwin";
		return "linux";
	}

	public static String archSlug() {
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (arch.contains("aarch64") || arch.contains("arm64"))
			return "arm64";
		if (arch.contains("amd64") || arch.contains("x86_64"))
			return "amd64";
		if (arch.contains("arm"))
			return "arm";
		if (arch.contains("86"))
			return "386";
		return "amd64";
	}

	public static String cliFileName() {
		return "wakatime-cli-" + osSlug() + "-" + archSlug() + (isWindows() ? ".exe" : "");
	}

	public static String cliAssetName() {
		return "wakatime-cli-" + osSlug() + "-" + archSlug() + ".zip";
	}

	private static String osName() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	}
}
