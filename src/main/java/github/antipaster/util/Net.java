package github.antipaster.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Net {
	private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(30))
			.build();

	private Net() {
	}

	public static Optional<String> latestTag(String apiUrl, String userAgent) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
					.header("User-Agent", userAgent)
					.header("Accept", "application/vnd.github+json")
					.timeout(Duration.ofSeconds(30))
					.GET()
					.build();
			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2)
				return Optional.empty();
			Matcher matcher = TAG_NAME.matcher(response.body());
			return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	public static void download(String url, Path target, String userAgent) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", userAgent)
				.timeout(Duration.ofMinutes(3))
				.GET()
				.build();
		HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(target));
		if (response.statusCode() / 100 != 2)
			throw new IOException("Unexpected status " + response.statusCode() + " when downloading " + url);
	}
}
