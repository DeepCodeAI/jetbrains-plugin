package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.utils.AnalysisData;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class InvalidateCachesAction extends AnAction {
  /**
   * Implement this method to provide your action handler.
   *
   * @param e Carries information on the invocation place
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(PlatformDataKeys.PROJECT);
    AnalysisData.clearCache(project);
    ServiceManager.getService(project, myTodoView.class).refresh();
/*
    ActionManager.getInstance()
        .getAction("ai.deepcode.jbplugin.ToolsMenu.AnalyseProjectAction")
        .actionPerformed(e);
*/
  }
}
