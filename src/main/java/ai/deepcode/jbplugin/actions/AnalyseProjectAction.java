package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.core.AnalysisData;
import ai.deepcode.jbplugin.core.DCLogger;
import ai.deepcode.jbplugin.core.DeepCodeUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AnalyseProjectAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(PlatformDataKeys.PROJECT);
    DCLogger.info("Re-Analyse Project requested for: " + project);
    AnalysisData.clearCache(project);
    if (DeepCodeUtils.isLogged(project, true)) {
      DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);
    }
  }
}
