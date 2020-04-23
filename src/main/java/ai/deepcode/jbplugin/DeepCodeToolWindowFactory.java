package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.ui.utils.DeepCodeUIUtils;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class DeepCodeToolWindowFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    DumbService.getInstance(project)
        .runWhenSmart(
            () -> ServiceManager.getService(project, myTodoView.class).initToolWindow(toolWindow));
  }

  @Override
  public void init(ToolWindow window) {
    window.setIcon(DeepCodeUIUtils.EMPTY_EWI_ICON);
  }
}
