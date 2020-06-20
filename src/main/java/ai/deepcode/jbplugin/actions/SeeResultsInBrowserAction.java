package ai.deepcode.jbplugin.actions;

import ai.deepcode.jbplugin.core.AnalysisData;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SeeResultsInBrowserAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project==null) return;
    final boolean urlExist = !AnalysisData.getInstance().getAnalysisUrl(project).isEmpty();
    e.getPresentation().setEnabled(urlExist);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project==null) return;
    final String analysisUrl = AnalysisData.getInstance().getAnalysisUrl(project);
    if (!analysisUrl.isEmpty()) BrowserUtil.open(analysisUrl);
  }
}
