package github.antipaster.cli;

import github.antipaster.config.WakaTime;
import github.antipaster.model.Heartbeat;
import github.antipaster.model.HeartbeatRequest;
import github.antipaster.recaf.Entities;
import github.antipaster.util.Proc;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public final class HeartbeatService {
	private static final Logger logger = Logging.get(HeartbeatService.class);
	private static final long THROTTLE_MS = WakaTime.HEARTBEAT_THROTTLE.toMillis();

	private final Path cli;
	private final String pluginUserAgent;
	private final ExecutorService executor;

	private final Object lock = new Object();
	private String lastKey;
	private long lastSentMs;

	public HeartbeatService(Path cli, String pluginUserAgent) {
		this.cli = cli;
		this.pluginUserAgent = pluginUserAgent;
		this.executor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "wakatime-heartbeat");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Sends a heartbeat unless it is throttled. A heartbeat is always sent when it is a
	 * write, when the focused file changed, or when more than {@link WakaTime#HEARTBEAT_THROTTLE}
	 * has passed since the last one.
	 * <p>
	 * The throttle check is cheap and runs on the caller's thread. {@code textSupplier}
	 * (which typically reads the editor's full contents) is only invoked once the heartbeat
	 * clears throttling, so it stays off the hot path of every caret move. It runs on the
	 * caller's thread, so callers on the JavaFX thread may read JavaFX state from it.
	 */
	public void send(String project, String projectFolder, String internalName, int lineNumber, int cursorPosition,
					 boolean write, Supplier<String> textSupplier) {
		String key = project + ' ' + internalName;
		synchronized (lock) {
			long now = System.currentTimeMillis();
			boolean changedFile = !key.equals(lastKey);
			if (!write && !changedFile && now - lastSentMs < THROTTLE_MS)
				return;
			lastKey = key;
			lastSentMs = now;
		}
		String text = textSupplier != null ? safeGet(textSupplier) : null;
		HeartbeatRequest request = new HeartbeatRequest(project, projectFolder, internalName, text, lineNumber, cursorPosition, write);
		try {
			executor.execute(() -> execute(request));
		} catch (RejectedExecutionException ignored) {
		}
	}

	private static String safeGet(Supplier<String> supplier) {
		try {
			return supplier.get();
		} catch (Exception e) {
			return null;
		}
	}

	public void shutdown() {
		executor.shutdownNow();
	}

	private void execute(HeartbeatRequest request) {
		try {
			Heartbeat heartbeat = Entities.forClass(request.project(), request.projectFolder(), request.internalName(),
					request.text(), request.lineNumber(), request.cursorPosition(), request.write());
			int exit = Proc.run(buildCommand(heartbeat), 120);
			if (exit != 0)
				logger.debug("wakatime-cli exited with code {}", exit);
		} catch (Exception e) {
			logger.debug("Failed to send heartbeat", e);
		}
	}

	List<String> buildCommand(Heartbeat heartbeat) {
		List<String> command = new ArrayList<>();
		command.add(cli.toString());
		command.add("--entity");
		command.add(heartbeat.entity());
		command.add("--plugin");
		command.add(pluginUserAgent);
		addOption(command, "--category", heartbeat.category());
		addOption(command, "--language", heartbeat.language());
		addOption(command, "--alternate-project", heartbeat.project());
		addOption(command, "--project-folder", heartbeat.projectFolder());
		command.add("--time");
		command.add(Long.toString(heartbeat.epochSeconds()));
		if (heartbeat.lineNumber() > 0) {
			command.add("--lineno");
			command.add(Integer.toString(heartbeat.lineNumber()));
		}
		if (heartbeat.cursorPosition() > 0) {
			command.add("--cursorpos");
			command.add(Integer.toString(heartbeat.cursorPosition()));
		}
		if (heartbeat.write()) {
			command.add("--write");
        }
		// The API key is intentionally not passed here; wakatime-cli reads it from
		// ~/.wakatime.cfg so it never appears in the process arguments.
		return command;
	}

	private static void addOption(List<String> command, String option, String value) {
		if (value == null || value.isBlank())
			return;
		command.add(option);
		command.add(value);
	}
}
