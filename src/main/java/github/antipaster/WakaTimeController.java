package github.antipaster;

import github.antipaster.cli.CliResolver;
import github.antipaster.cli.HeartbeatService;
import github.antipaster.cli.WakaConfig;
import github.antipaster.config.WakaTime;
import jakarta.enterprise.inject.Instance;
import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextInputDialog;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import software.coley.bentofx.dockable.Dockable;
import software.coley.bentofx.dockable.DockableCloseListener;
import software.coley.bentofx.dockable.DockableSelectListener;
import software.coley.bentofx.event.EventBus;
import software.coley.bentofx.path.DockablePath;
import software.coley.recaf.RecafBuildConfig;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.properties.builtin.InputFilePathProperty;
import software.coley.recaf.services.navigation.ClassNavigable;
import software.coley.recaf.services.workspace.WorkspaceCloseListener;
import software.coley.recaf.services.workspace.WorkspaceManager;
import software.coley.recaf.services.workspace.WorkspaceOpenListener;
import software.coley.recaf.ui.control.richtext.Editor;
import software.coley.recaf.ui.docking.DockingManager;
import software.coley.recaf.util.FxThreadUtil;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.BundleListener;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceFileResource;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wires Recaf's editor events to WakaTime heartbeats.
 * <ul>
 *     <li>Switching to a class tab is a "file changed" event.</li>
 *     <li>Moving the caret or typing in the focused editor is an "activity" event.</li>
 *     <li>A class being updated in the workspace (assemble/recompile) is a "write" event.</li>
 * </ul>
 * Heartbeats are throttled by {@link HeartbeatService}; wakatime-cli does the rest.
 */
public final class WakaTimeController implements WorkspaceOpenListener, WorkspaceCloseListener, BundleListener<JvmClassInfo> {
	private static final Logger logger = Logging.get(WakaTimeController.class);

	private final WorkspaceManager workspaceManager;
	private final Instance<DockingManager> dockingManager;
	private final String pluginVersion;

	private final DockableSelectListener dockableSelectListener = this::onDockableSelected;
	private final DockableCloseListener dockableCloseListener = this::onDockableClosed;

	private final ExecutorService initExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "wakatime-init");
		thread.setDaemon(true);
		return thread;
	});

	private volatile HeartbeatService heartbeats;
	private volatile EventBus eventBus;
	private volatile JvmClassBundle boundBundle;
	private volatile String projectName;
	private volatile String projectFolder;

	// Editor binding state; mutated only on the JavaFX thread.
	private Node boundNode;
	private CodeArea boundCodeArea;
	private InvalidationListener caretListener;

	// Snapshot of the focused class, readable from any thread.
	private volatile String currentClassName;
	private volatile String currentText;
	private volatile int currentLine;
	private volatile int currentColumn;

	public WakaTimeController(WorkspaceManager workspaceManager, Instance<DockingManager> dockingManager, String pluginVersion) {
		this.workspaceManager = workspaceManager;
		this.dockingManager = dockingManager;
		this.pluginVersion = pluginVersion;
	}

	public void start() {
		workspaceManager.addWorkspaceOpenListener(this);
		workspaceManager.addWorkspaceCloseListener(this);
		if (workspaceManager.hasCurrentWorkspace())
			onWorkspaceOpened(workspaceManager.getCurrent());
		FxThreadUtil.run(() -> {
			eventBus = dockingManager.get().getBento().events();
			eventBus.addDockableSelectListener(dockableSelectListener);
			eventBus.addDockableCloseListener(dockableCloseListener);
		});
		initExecutor.execute(this::initialize);
	}

	public void stop() {
		workspaceManager.removeWorkspaceOpenListener(this);
		workspaceManager.removeWorkspaceCloseListener(this);
		detachBundle();
		FxThreadUtil.run(() -> {
			if (eventBus != null) {
				eventBus.removeDockableSelectListener(dockableSelectListener);
				eventBus.removeDockableCloseListener(dockableCloseListener);
			}
			detachEditor();
		});
		HeartbeatService service = heartbeats;
		if (service != null)
			service.shutdown();
		initExecutor.shutdownNow();
	}

	private void initialize() {
		Optional<java.nio.file.Path> cli = new CliResolver().resolve();
		if (cli.isEmpty()) {
			logger.error("wakatime-cli is unavailable; time will not be tracked");
			return;
		}
		heartbeats = new HeartbeatService(cli.get(), userAgent());
		if (!WakaConfig.hasApiKey())
			promptForApiKey();
		logger.info("WakaTime tracking is ready");
	}

	private void promptForApiKey() {
		FxThreadUtil.run(() -> {
			TextInputDialog dialog = new TextInputDialog();
			dialog.setTitle("WakaTime");
			dialog.setHeaderText("Enter your WakaTime API key");
			dialog.setContentText("API key (wakatime.com/settings/api-key):");
			dialog.showAndWait()
					.map(String::trim)
					.filter(key -> !key.isEmpty())
					.ifPresent(WakaConfig::writeApiKey);
		});
	}

	// region Workspace + save tracking

	@Override
	public void onWorkspaceOpened(Workspace workspace) {
		projectName = deriveProject(workspace);
		projectFolder = deriveProjectFolder(workspace);
		detachBundle();
		try {
			JvmClassBundle bundle = workspace.getPrimaryResource().getJvmClassBundle();
			bundle.addBundleListener(this);
			boundBundle = bundle;
		} catch (Exception e) {
			logger.debug("Could not attach to the class bundle", e);
		}
	}

	@Override
	public void onWorkspaceClosed(Workspace workspace) {
		detachBundle();
		projectName = null;
		projectFolder = null;
		currentClassName = null;
		FxThreadUtil.run(this::detachEditor);
	}

	@Override
	public void onNewItem(String name, JvmClassInfo info) {
		// Not tracked.
	}

	@Override
	public void onUpdateItem(String name, JvmClassInfo oldInfo, JvmClassInfo newInfo) {
		HeartbeatService service = heartbeats;
		if (service == null)
			return;
		boolean focused = name.equals(currentClassName);
		service.send(project(), projectFolder, name,
				focused ? currentLine : 0,
				focused ? currentColumn : 0,
				true,
				focused ? () -> currentText : null);
	}

	@Override
	public void onRemoveItem(String name, JvmClassInfo info) {
		// Not tracked.
	}

	private void detachBundle() {
		JvmClassBundle bundle = boundBundle;
		if (bundle != null) {
			try {
				bundle.removeBundleListener(this);
			} catch (Exception ignored) {
			}
			boundBundle = null;
		}
	}

	// endregion

	// region Focus + activity tracking (JavaFX thread)

	private void onDockableSelected(DockablePath path, Dockable dockable) {
		detachEditor();
		Node node = dockable.getNode();
		if (node instanceof ClassNavigable navigable) {
			bindClass(node, navigable.getClassPath().getValue());
		} else {
			currentClassName = null;
		}
	}

	private void onDockableClosed(DockablePath path, Dockable dockable) {
		if (dockable.getNode() == boundNode) {
			detachEditor();
			currentClassName = null;
		}
	}

	private void bindClass(Node node, ClassInfo info) {
		boundNode = node;
		currentClassName = info.getName();
		currentText = null;
		currentLine = 0;
		currentColumn = 0;
		Editor editor = findEditor(node);
		if (editor != null) {
			attachEditor(editor);
		} else {
			// The editor may be created lazily just after selection; retry once.
			FxThreadUtil.delayedRun(250, () -> {
				if (boundNode == node && boundCodeArea == null) {
					Editor late = findEditor(node);
					if (late != null) {
						attachEditor(late);
						sendRead(late);
					}
				}
			});
		}
		sendRead(editor);
	}

	private void attachEditor(Editor editor) {
		CodeArea area = editor.getCodeArea();
		caretListener = observable -> sendRead(editor);
		area.caretPositionProperty().addListener(caretListener);
		boundCodeArea = area;
	}

	private void detachEditor() {
		if (boundCodeArea != null && caretListener != null)
			boundCodeArea.caretPositionProperty().removeListener(caretListener);
		boundCodeArea = null;
		caretListener = null;
		boundNode = null;
	}

	private void sendRead(Editor editor) {
		HeartbeatService service = heartbeats;
		String name = currentClassName;
		if (service == null || name == null)
			return;
		if (editor == null) {
			service.send(project(), projectFolder, name, 0, 0, false, null);
			return;
		}
		CodeArea area = editor.getCodeArea();
		int line = area.getCurrentParagraph() + 1;
		int column = area.getCaretColumn() + 1;
		currentLine = line;
		currentColumn = column;
		// getText() reads the whole editor buffer, so it is deferred into this supplier:
		// HeartbeatService only invokes it (on this JavaFX thread) when the heartbeat is
		// not throttled, instead of on every caret movement.
		service.send(project(), projectFolder, name, line, column, false, () -> {
			String text = editor.getText();
			currentText = text;
			return text;
		});
	}

	private static Editor findEditor(Node node) {
		if (node instanceof Editor editor)
			return editor;
		if (node instanceof Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				Editor found = findEditor(child);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	// endregion

	private String project() {
		String name = projectName;
		return name != null ? name : WakaTime.DEFAULT_PROJECT;
	}

	private String userAgent() {
		return "recaf/" + safe(RecafBuildConfig.VERSION, "unknown")
				+ " recaf-wakatime/" + safe(pluginVersion, "0.0.0");
	}

	private static String deriveProject(Workspace workspace) {
		WorkspaceResource resource = workspace.getPrimaryResource();
		if (!(resource instanceof WorkspaceFileResource fileResource))
			return WakaTime.DEFAULT_PROJECT;
		String name = fileResource.getFileInfo().getName();
		int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (separator >= 0)
			name = name.substring(separator + 1);
		name = stripArchiveExtension(name);
		return name.isBlank() ? WakaTime.DEFAULT_PROJECT : name;
	}

	/**
	 * The directory on disk that the workspace was opened from, passed to wakatime-cli as
	 * {@code --project-folder} so it can pick up a git repo / branch when the input happens
	 * to live inside one. Null for in-memory workspaces, which have no on-disk location.
	 */
	private static String deriveProjectFolder(Workspace workspace) {
		WorkspaceResource resource = workspace.getPrimaryResource();
		if (!(resource instanceof WorkspaceFileResource fileResource))
			return null;
		Path input = InputFilePathProperty.get(fileResource.getFileInfo());
		if (input == null)
			return null;
		Path parent = input.toAbsolutePath().getParent();
		return parent != null ? parent.toString() : null;
	}

	private static String stripArchiveExtension(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		for (String ext : new String[]{".jar", ".war", ".ear", ".zip", ".apk", ".dex", ".class"}) {
			if (lower.endsWith(ext))
				return name.substring(0, name.length() - ext.length());
		}
		return name;
	}

	private static String safe(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
