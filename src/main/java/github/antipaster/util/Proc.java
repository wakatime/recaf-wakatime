package github.antipaster.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Proc {
	private Proc() {
	}

	public static String runCapture(List<String> command, long timeoutSeconds) {
		try {
			Process process = start(command);
			String output = readFully(process.getInputStream());
			if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return null;
			}
			return process.exitValue() == 0 ? output.trim() : null;
		} catch (Exception e) {
			return null;
		}
	}

	public static int run(List<String> command, long timeoutSeconds) throws Exception {
		Process process = start(command);
		// Drain the combined output so the process never blocks on a full pipe buffer.
		readFully(process.getInputStream());
		if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			return -1;
		}
		return process.exitValue();
	}

	private static Process start(List<String> command) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		return builder.start();
	}

	private static String readFully(InputStream stream) throws Exception {
		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null)
				builder.append(line).append('\n');
		}
		return builder.toString();
	}
}
