package github.antipaster.cli;

import github.antipaster.model.Heartbeat;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HeartbeatServiceTest {
	@Test
	void buildCommandOmitsNullAndBlankOptionalArguments() {
		HeartbeatService service = new HeartbeatService(Path.of("wakatime-cli"), "recaf-wakatime/test");
		try {
			Heartbeat heartbeat = new Heartbeat(
					"/tmp/Example.java",
					null,
					"",
					" ",
					"coding",
					12,
					4,
					false,
					123456789L);

			List<String> command = service.buildCommand(heartbeat);

			assertFalse(command.contains("--language"));
			assertFalse(command.contains("--alternate-project"));
			assertFalse(command.contains("--project-folder"));
			assertEquals(List.of(
					"wakatime-cli",
					"--entity", "/tmp/Example.java",
					"--plugin", "recaf-wakatime/test",
					"--category", "coding",
					"--time", "123456789",
					"--lineno", "12",
					"--cursorpos", "4"), command);
		} finally {
			service.shutdown();
		}
	}

	@Test
	void buildCommandIncludesOptionalArgumentsWithText() {
		HeartbeatService service = new HeartbeatService(Path.of("wakatime-cli"), "recaf-wakatime/test");
		try {
			Heartbeat heartbeat = new Heartbeat(
					"/tmp/Example.java",
					"sample-project",
					"/tmp/sample",
					"Java",
					"coding",
					0,
					0,
					true,
					123456789L);

			assertEquals(List.of(
					"wakatime-cli",
					"--entity", "/tmp/Example.java",
					"--plugin", "recaf-wakatime/test",
					"--category", "coding",
					"--language", "Java",
					"--alternate-project", "sample-project",
					"--project-folder", "/tmp/sample",
					"--time", "123456789",
					"--write"), service.buildCommand(heartbeat));
		} finally {
			service.shutdown();
		}
	}
}
