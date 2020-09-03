package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.core.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AnalyseProjectAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(PlatformDataKeys.PROJECT);
    DCLogger.getInstance().logInfo("Re-Analyse Project requested for: " + project);
    // fixme: ?? background task here to avoid potential freeze due to MUTEX lock
    AnalysisData.getInstance().resetCachesAndTasks(project);
    if (LoginUtils.getInstance().isLogged(project, true)) {
      RunUtils.getInstance().asyncAnalyseProjectAndUpdatePanel(project);
    }
  }
}
