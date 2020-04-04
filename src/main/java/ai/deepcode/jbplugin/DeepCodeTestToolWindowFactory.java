package ai.deepcode.jbplugin;

import ai.deepcode.jbplugin.ui.myTodoView;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class DeepCodeTestToolWindowFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ServiceManager.getService(project, myTodoView.class).initToolWindow(toolWindow);
  }
}
