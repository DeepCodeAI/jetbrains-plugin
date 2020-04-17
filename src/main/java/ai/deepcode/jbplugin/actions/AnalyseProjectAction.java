package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.DeepCodeNotifications;
import ai.deepcode.jbplugin.utils.DeepCodeUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AnalyseProjectAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(PlatformDataKeys.PROJECT);
    if (DeepCodeUtils.isNotLogged(project)) {
      DeepCodeNotifications.reShowLastNotification();
      return;
    }
    DeepCodeUtils.asyncAnalyseProjectAndUpdatePanel(project);
  }
}
