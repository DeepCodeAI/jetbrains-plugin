package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.ui.utils.DeepCodeUIUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class DeepCodeStatusBarWidgetProvider implements StatusBarWidgetProvider {

  @Nullable
  @Override
  public StatusBarWidget getWidget(@NotNull Project project) {
    return new DeepCodeStatusBarWidget(project);
  }

  @NotNull
  @Override
  public String getAnchor() {
    return StatusBar.Anchors.before(StatusBar.StandardWidgets.POSITION_PANEL);
  }

  public class DeepCodeStatusBarWidget
      implements StatusBarWidget, StatusBarWidget.IconPresentation {
    private final Project project;
    private StatusBar myStatusBar;
    private Icon myCurrentIcon = DeepCodeUIUtils.EMPTY_EWI_ICON;
    private String myToolTipText = "DeepCode";

    public DeepCodeStatusBarWidget(@NotNull Project project) {
      this.project = project;
      update();
    }

    public void update() {
      myCurrentIcon = DeepCodeUIUtils.getSummaryIcon(project);
      myToolTipText = DeepCodeUIUtils.addErrWarnInfoCounts(project, "DeepCode", true, null);
      if (myStatusBar != null) myStatusBar.updateWidget(ID());
    }

    @NotNull
    @Override
    public String ID() {
      return "DeepCodeAnalysisStatus";
    }

    @Nullable
    @Override
    public WidgetPresentation getPresentation(@NotNull StatusBarWidget.PlatformType type) {
      return this;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      myStatusBar = statusBar;
    }

    @Override
    public void dispose() {}

    @Nullable
    @Override
    public Icon getIcon() {
      return myCurrentIcon;
    }

    @Nullable
    @Override
    public String getTooltipText() {
      return myToolTipText;
    }

    @Nullable
    @Override
    public Consumer<MouseEvent> getClickConsumer() {
      return mouseEvent -> {
        ToolWindow myToolWindow = ToolWindowManager.getInstance(project).getToolWindow("DeepCode");
        myToolWindow.show(null);
      };
    }
  }
}
