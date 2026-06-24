package github.antipaster.model;

/**
 * A lightweight description of activity. The actual entity file is only written (and the
 * {@link Heartbeat} built) on the background thread once the request survives throttling,
 * keeping disk I/O off the UI thread.
 */
public record HeartbeatRequest(
		String project,
		String projectFolder,
		String internalName,
		String text,
		int lineNumber,
		int cursorPosition,
		boolean write) {
}
