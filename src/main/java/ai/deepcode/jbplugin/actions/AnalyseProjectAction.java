package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.ui.myTodoView;
import ai.deepcode.jbplugin.utils.AnalysisData;
import ai.deepcode.jbplugin.utils.DeepCodeParams;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AnalyseProjectAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(PlatformDataKeys.PROJECT);
    if (!DeepCodeParams.isLogged()) {
      DeepCodeNotifications.showLoginLink(project);
      return;
    }
    AnalysisData.getAnalysis(DeepCodeUtils.getAllSupportedFilesInProject(project));
    ServiceManager.getService(project, myTodoView.class).refresh();
  }
}
