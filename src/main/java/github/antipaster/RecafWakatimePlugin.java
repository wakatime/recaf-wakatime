package github.antipaster;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.plugin.Plugin;
import software.coley.recaf.plugin.PluginInformation;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.ui.docking.DockingManager;

@Dependent
@PluginInformation(id = "##ID##", version = "##VERSION##", name = "##NAME##", description = "##DESC##")
public class RecafWakatimePlugin implements Plugin {
	private static final Logger logger = Logging.get(RecafWakatimePlugin.class);

	private final WakaTimeController controller;

	@Inject
	public RecafWakatimePlugin(WorkspaceManager workspaceManager, Instance<DockingManager> dockingManager) {
		this.controller = new WakaTimeController(workspaceManager, dockingManager, pluginVersion());
	}

	@Override
	public void onEnable() {
		controller.start();
		logger.info("Recaf WakaTime plugin enabled");
	}

	@Override
	public void onDisable() {
		controller.stop();
		logger.info("Recaf WakaTime plugin disabled");
	}

	private String pluginVersion() {
		PluginInformation information = getClass().getAnnotation(PluginInformation.class);
		return information != null ? information.version() : "0.0.0";
	}
}
