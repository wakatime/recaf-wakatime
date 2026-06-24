package github.antipaster.model;

public record Heartbeat(
		String entity,
		String project,
		String language,
		String category,
		int lineNumber,
		int cursorPosition,
		boolean write,
		long epochSeconds) {
}
